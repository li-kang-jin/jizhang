package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.BudgetDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.RecurringExpenseDao
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.local.entity.LocalBudget
import com.jizhang.app.data.local.entity.LocalRecurringExpense
import com.jizhang.app.data.local.newClientId
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.BudgetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.remote.dto.BudgetSaveDto
import com.jizhang.app.data.remote.dto.RecurringExpenseDto
import com.jizhang.app.data.remote.dto.RecurringExpenseSaveDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withTimeoutOrNull
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** 小于这个值的固定支出 id 视为早期版本的自增 id（跨设备会撞），推送前换成全局唯一 ID */
private const val LEGACY_RECURRING_ID_LIMIT = 1_000_000_000_000L

/**
 * 「顺手推一次服务端」的等待上限。
 * <p>
 * 保存 / 删除都已经落在本地了，推送只是锦上添花：后端没发版或断网时不能让保存按钮一直转圈，
 * 超时就当作推送失败留给下次同步。
 */
private const val PUSH_TIMEOUT_MS = 2_000L

/**
 * 预算仓库。
 *
 * ### 读写路径（离线优先）
 * - **读**：本地 Room 实时聚合。额度来自 `local_budget`，已用金额由交易流水算，
 *   离线照样能看进度，也不会出现「额度更新了但进度还是旧值」。
 * - **写**：**先写本地并标记待同步**，界面立刻生效；随后尽力推一次服务端（REST），
 *   推不上去（断网 / 服务端没发版）就留在 `pendingSync`，等下一次同步由 [pushPending] 补推。
 *   绝不能「推送失败就不落本地」—— 那样后端没部署时用户连预算都设不了。
 * - **删**：已经同步过的记录在本地打**墓碑**（`pendingDelete`），界面立刻消失，
 *   推送成功后再物理删；只存在于本地的记录直接删。否则断网删除的预算会被下次拉取「复活」。
 * - **拉**：跨设备 / 家庭成员的额度变更由同步拉取带回（见 [mergeFromServer]），
 *   本地未推送的改动（含墓碑）优先，不被服务端旧值覆盖。
 *
 * ### 每日预测
 * `elapsedDays / burnRate / forecastExpense / status` 都在这里算，不打服务端 ——
 * 预测必须跟着本地流水实时变，走接口反而会滞后。服务端的同名字段供 Web 用。
 */
@Singleton
class BudgetRepository @Inject constructor(
    private val budgetDao: BudgetDao,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val recurringExpenseDao: RecurringExpenseDao,
    private val tokenStore: TokenStore,
    private val api: ApiService
) {

    /** 账本总预算对应的分类 ID */
    private val totalCategoryId = 0L

    /** 预算只统计支出 */
    private val typeExpense = 1

    /** 预测超支超过这个比例才算「有风险」 */
    private val atRiskRatio = 1.1

    fun observeOverview(
        bookId: Long,
        period: String = BudgetPeriods.of(BudgetCycle.MONTH)
    ): Flow<BudgetOverviewDto> =
        budgetDao.observeByPeriod(bookId, period).map { buildOverview(bookId, period, it) }

    suspend fun overview(
        bookId: Long,
        period: String = BudgetPeriods.of(BudgetCycle.MONTH)
    ): BudgetOverviewDto =
        buildOverview(bookId, period, budgetDao.listByPeriod(bookId, period))

    /**
     * 保存预算。同一账本 + 分类 + 周期只保留一条，重复保存视为修改额度。
     * <p>
     * **离线优先**：先落本地（界面立刻看到），能联网就顺手推一次服务端；推送失败不算失败，
     * 记录留在待同步队列里等下次同步。所以后端没部署 / 断网时预算照样能设。
     *
     * @return 只有「本地都写不进去」才会失败；服务端推送失败由 [pushPending] 兜底
     */
    suspend fun save(
        bookId: Long,
        categoryId: Long,
        period: String,
        amount: Long,
        remark: String? = null
    ): Result<Unit> = runCatching {
        val exist = budgetDao.find(bookId, categoryId, period)
        val local = LocalBudget(
            // 已有记录沿用原 id（含墓碑：重新设置 = 取消删除），否则给一个本地临时 id
            id = exist?.id ?: (budgetDao.maxId() + 1),
            bookId = bookId,
            categoryId = categoryId,
            period = period,
            amount = amount,
            remark = remark,
            pendingSync = true,
            pendingDelete = false,
            clientUpdatedAt = System.currentTimeMillis()
        )
        budgetDao.upsert(local)

        if (useRemote(bookId)) {
            // 推不上去也无所谓：本地已经是用户想要的值了
            withTimeoutOrNull(PUSH_TIMEOUT_MS) { pushOne(local) }
        }
    }

    /**
     * 删除预算。
     * <p>
     * 已经同步到服务端的记录在本地打墓碑（界面立刻消失），推送成功后再物理删；
     * 只存在于本地的记录（还没推上去过）直接物理删 —— 留墓碑反而永远推不出去。
     */
    suspend fun delete(budgetId: Long, bookId: Long): Result<Unit> = runCatching {
        val row = budgetDao.findById(budgetId) ?: return@runCatching
        if (row.pendingSync || !useRemote(bookId)) {
            budgetDao.deleteById(budgetId)
        } else {
            budgetDao.markPendingDelete(budgetId)
            // 只有确认服务端删掉了才清墓碑；超时 / 失败时墓碑留着，等下次同步重试
            val deleted = runCatching {
                withTimeoutOrNull(PUSH_TIMEOUT_MS) {
                    val result = api.deleteBudget(row.id, bookId)
                    if (!result.isSuccess) {
                        error(result.message.ifBlank { "删除预算失败" })
                    }
                    true
                }
            }.getOrNull() == true
            if (deleted) {
                budgetDao.deleteById(budgetId)
            }
        }
    }

    suspend fun listByPeriod(bookId: Long, period: String): List<LocalBudget> =
        budgetDao.listByPeriod(bookId, period)

    /** 某分类在指定周期的额度（分），未设置（或已删除）返回 null */
    suspend fun amountOf(bookId: Long, categoryId: Long, period: String): Long? =
        budgetDao.find(bookId, categoryId, period)?.takeIf { !it.pendingDelete }?.amount

    /**
     * 新周期还没有任何预算时，自动沿用上一周期的额度。
     * <p>
     * 用户上个月设了预算，这个月一进来又是空的会很烦躁；复制一份可以马上接着用，
     * 复制出来的记录同样标记待同步。
     *
     * @return 复制的条数，0 表示没有可继承的内容
     */
    suspend fun inheritPrevious(bookId: Long, period: String): Int {
        if (budgetDao.listByPeriod(bookId, period).isNotEmpty()) {
            return 0
        }
        val source = budgetDao.listByPeriod(bookId, BudgetPeriods.previous(period))
        if (source.isEmpty()) {
            return 0
        }

        var id = budgetDao.maxId()
        val now = System.currentTimeMillis()
        val copies = source.map { budget ->
            LocalBudget(
                id = ++id,
                bookId = bookId,
                categoryId = budget.categoryId,
                period = period,
                amount = budget.amount,
                remark = budget.remark,
                pendingSync = true,
                clientUpdatedAt = now
            )
        }
        budgetDao.upsertAll(copies)
        return copies.size
    }

    /* ---------------- 与同步协作 ---------------- */

    /**
     * 把本地待同步的预算补推到服务端。
     * <p>
     * 同步推送协议里没有预算字段，所以离线期间改的、后端还没发版时设的、以及登录时从本地
     * 账本迁过来的预算只能走 REST 补推。单条失败不影响其它记录，也不让整个同步失败（附属数据）。
     * <p>
     * **先推删除再推新增**：同键的墓碑与待更新不会同时存在，但先删能避免「建了又删」的顺序错乱。
     *
     * @return 成功推送（含删除）的条数
     */
    suspend fun pushPending(bookId: Long): Int {
        if (!useRemote(bookId)) {
            return 0
        }
        var pushed = 0

        for (item in budgetDao.listPendingDelete(bookId)) {
            val deleted = runCatching { api.deleteBudget(item.id, bookId) }.getOrNull()?.isSuccess == true
            if (deleted) {
                budgetDao.deleteById(item.id)
                pushed++
            }
        }

        for (item in budgetDao.listPending(bookId)) {
            if (pushOne(item)) {
                pushed++
            }
        }
        return pushed
    }

    /**
     * 尽力把一条预算推到服务端。
     * <p>
     * 服务端返回的是它自己的 ID，所以成功后要按业务键清掉本地旧行再写回服务端 ID，
     * 否则同一份预算会在本地留下两条（一条待同步、一条已同步）。
     *
     * @return 是否推送成功；失败时本地记录保持 `pendingSync`，等下次同步
     */
    private suspend fun pushOne(item: LocalBudget): Boolean = runCatching {
        val serverId = api.saveBudget(
            BudgetSaveDto(
                bookId = item.bookId,
                categoryId = item.categoryId,
                cycle = BudgetPeriods.wireCycle(BudgetPeriods.cycleOf(item.period)),
                periodStart = BudgetPeriods.startIso(item.period),
                amount = item.amount,
                remark = item.remark
            )
        ).data ?: error("预算同步失败")

        budgetDao.deleteByKey(item.bookId, item.categoryId, item.period)
        budgetDao.upsert(
            item.copy(
                id = serverId,
                pendingSync = false,
                clientUpdatedAt = System.currentTimeMillis()
            )
        )
        true
    }.getOrDefault(false)

    /**
     * 合并同步拉取回来的预算额度。
     * <p>
     * 本地未推送的改动优先：待更新的、以及刚删掉还没推上去的（墓碑）都跳过，
     * 否则刚设好 / 刚删掉的额度会被服务端的旧值盖回来。
     *
     * @return 写入本地的条数
     */
    suspend fun mergeFromServer(
        bookId: Long,
        budgets: List<BudgetDto>,
        deletedBudgetIds: List<Long>
    ): Int {
        var changed = 0
        for (dto in budgets) {
            val period = BudgetPeriods.ofWire(dto.cycle, dto.periodStart)
            val local = budgetDao.find(bookId, dto.categoryId, period)
            if (local != null && (local.pendingSync || local.pendingDelete)) {
                continue
            }
            budgetDao.deleteByKey(bookId, dto.categoryId, period)
            budgetDao.upsert(
                LocalBudget(
                    id = dto.id,
                    bookId = bookId,
                    categoryId = dto.categoryId,
                    period = period,
                    amount = dto.amount,
                    remark = dto.remark,
                    pendingSync = false,
                    clientUpdatedAt = System.currentTimeMillis()
                )
            )
            changed++
        }
        if (deletedBudgetIds.isNotEmpty()) {
            // 服务端说删了，但本地还有没推上去的改动 / 墓碑：先留着，别把用户刚做的操作抹掉
            val keep = (budgetDao.listPending(bookId).map { it.id } +
                budgetDao.listPendingDelete(bookId).map { it.id }).toSet()
            val removable = deletedBudgetIds.filterNot { it in keep }
            if (removable.isNotEmpty()) {
                budgetDao.deleteByIds(removable)
            }
        }
        return changed
    }

    /* ---------------- 固定支出 ---------------- */

    fun observeRecurring(bookId: Long): Flow<List<LocalRecurringExpense>> =
        recurringExpenseDao.observeByBook(bookId)

    suspend fun listRecurring(bookId: Long): List<LocalRecurringExpense> =
        recurringExpenseDao.listByBook(bookId)

    /**
     * 保存固定支出。
     * <p>
     * id 由客户端生成（见 [newClientId]）：同名固定支出允许存在多条，没有可用的业务键，
     * 幂等只能落在客户端 ID 上 —— 服务端按 id upsert，本地也就不需要在保存后换 ID。
     * <p>
     * 与预算一样**离线优先**：先落本地（待同步标记），再尽力推一次服务端；推不上去也不报错，
     * 等下次同步补推。否则后端没发版时固定支出根本登记不上。
     */
    suspend fun saveRecurring(item: LocalRecurringExpense): Result<Unit> = runCatching {
        val saved = item.copy(
            id = if (item.id > 0L) item.id else newClientId(),
            pendingSync = true,
            pendingDelete = false
        )
        recurringExpenseDao.upsert(saved)
        if (useRemote(saved.bookId)) {
            withTimeoutOrNull(PUSH_TIMEOUT_MS) { pushOneRecurring(saved) }
        }
    }

    /**
     * 删除固定支出。
     * <p>
     * 已同步过的记录本地先打墓碑（界面立刻消失），推送成功再物理删；
     * 只在本地存在的记录直接删，避免留下永远推不出去的墓碑。
     */
    suspend fun deleteRecurring(id: Long, bookId: Long): Result<Unit> = runCatching {
        val row = recurringExpenseDao.findById(id) ?: return@runCatching
        if (row.pendingSync || !useRemote(bookId)) {
            recurringExpenseDao.deleteById(id)
        } else {
            recurringExpenseDao.markPendingDelete(id)
            // 同样：只有服务端确认删掉才清墓碑
            val deleted = runCatching {
                withTimeoutOrNull(PUSH_TIMEOUT_MS) {
                    val result = api.deleteRecurringExpense(id, bookId)
                    if (!result.isSuccess) {
                        error(result.message.ifBlank { "固定支出删除失败" })
                    }
                    true
                }
            }.getOrNull() == true
            if (deleted) {
                recurringExpenseDao.deleteById(id)
            }
        }
    }

    /**
     * 把本地待同步的固定支出补推到服务端。
     * <p>
     * 同步推送协议只处理交易，固定支出和预算一样必须单独走 REST：离线期间改的、
     * 以及登录时从本地账本迁过来的规则都在这里补上。
     *
     * @return 成功推送的条数
     */
    suspend fun pushPendingRecurring(bookId: Long): Int {
        if (!useRemote(bookId)) {
            return 0
        }
        normalizeRecurringIds()
        var pushed = 0

        // 先推删除（墓碑），再推新增 / 修改
        for (item in recurringExpenseDao.listPendingDelete(bookId)) {
            val deleted = runCatching { api.deleteRecurringExpense(item.id, bookId) }
                .getOrNull()?.isSuccess == true
            if (deleted) {
                recurringExpenseDao.deleteById(item.id)
                pushed++
            }
        }

        for (item in recurringExpenseDao.listPending(bookId)) {
            if (pushOneRecurring(item)) {
                pushed++
            }
        }
        return pushed
    }

    /** 尽力把一条固定支出推到服务端；失败保持待同步，等下次同步 */
    private suspend fun pushOneRecurring(item: LocalRecurringExpense): Boolean = runCatching {
        val result = api.saveRecurringExpense(RecurringExpenseSaveDto.from(item))
        if (!result.isSuccess) {
            error(result.message.ifBlank { "固定支出同步失败" })
        }
        recurringExpenseDao.markSynced(listOf(item.id))
        true
    }.getOrDefault(false)

    /**
     * 合并同步拉取回来的固定支出。
     * <p>
     * 本地待同步的改动优先：等 [pushPendingRecurring] 推上去之后再接受服务端版本，
     * 否则刚登记好的规则会被服务端的旧值盖掉。
     *
     * @return 写入本地的条数
     */
    suspend fun mergeRecurringFromServer(
        bookId: Long,
        items: List<RecurringExpenseDto>,
        deletedIds: List<Long>
    ): Int {
        var changed = 0
        for (dto in items) {
            val local = recurringExpenseDao.findById(dto.id)
            if (local != null && (local.pendingSync || local.pendingDelete)) {
                continue
            }
            recurringExpenseDao.upsert(dto.toLocal(bookId))
            changed++
        }
        if (deletedIds.isNotEmpty()) {
            // 本地还有没推上去的改动 / 墓碑时先留着，别把用户刚做的操作抹掉
            val keep = (recurringExpenseDao.listPending(bookId).map { it.id } +
                recurringExpenseDao.listPendingDelete(bookId).map { it.id }).toSet()
            val removable = deletedIds.filterNot { it in keep }
            if (removable.isNotEmpty()) {
                recurringExpenseDao.deleteByIds(removable)
            }
        }
        return changed
    }

    /**
     * 早期记录的 id 是本地自增的小数字，同一份规则在另一台设备上会拿到同样的 id，
     * 推送后互相覆盖。推送前统一换成全局唯一 ID。
     */
    private suspend fun normalizeRecurringIds() {
        recurringExpenseDao.listByIdBelow(LEGACY_RECURRING_ID_LIMIT).forEach { legacy ->
            recurringExpenseDao.renameId(legacy.id, newClientId())
        }
    }

    /**
     * 本期**还没到期**的固定支出合计，从每日可花里预留掉。
     * <p>
     * 只算「今天及以后」的发生日：今天之前该扣的，理应已经记成账单进 [buildOverview] 的
     * `totalUsed` 了，再预留一次就是重复扣钱。代价是用户忘了记某笔到期的固定支出时，
     * 那笔钱会被释放回每日额度 —— 这是模式 1（只预留、不自动记账）的已知取舍。
     */
    suspend fun reservedFixed(
        bookId: Long,
        period: String,
        today: LocalDate = LocalDate.now()
    ): FixedReservation {
        val periodEnd = BudgetPeriods.endDate(period)
        val from = maxOf(BudgetPeriods.startDate(period), today)
        if (!from.isBefore(periodEnd)) {
            return FixedReservation()
        }

        val items = mutableListOf<FixedReservationItem>()
        var total = 0L
        for (expense in recurringExpenseDao.listEnabled(bookId)) {
            val dates = RecurringExpenses.occurrences(expense, from, periodEnd)
            if (dates.isEmpty()) {
                continue
            }
            val amount = expense.amount * dates.size
            total += amount
            items += FixedReservationItem(
                expense = expense,
                nextDate = dates.first(),
                count = dates.size,
                amount = amount
            )
        }
        return FixedReservation(total = total, items = items.sortedBy { it.nextDate })
    }

    /* ---------------- 内部方法 ---------------- */

    private suspend fun buildOverview(
        bookId: Long,
        period: String,
        budgets: List<LocalBudget>,
        today: LocalDate = LocalDate.now()
    ): BudgetOverviewDto {
        val start = BudgetPeriods.startMillis(period)
        val end = BudgetPeriods.endMillis(period)
        val daysTotal = BudgetPeriods.daysTotal(period)
        val daysLeft = BudgetPeriods.daysLeft(period, today)
        val elapsedDays = elapsedDays(period, today, daysTotal)

        val sum = transactionDao.sumBetween(bookId, start, end)
        val periodExpense = sum.expense

        val categoryMap = categoryDao.listByBook(bookId).associateBy { it.id }
        val usedMap = transactionDao.sumByCategory(bookId, typeExpense, start, end)
            .filter { it.categoryId != null }
            .associate { it.categoryId!! to it.total }

        val totalBudget = budgets.firstOrNull { it.categoryId == totalCategoryId }
        val totalAmount = totalBudget?.amount ?: 0L
        val remaining = totalAmount - periodExpense

        val items = budgets
            .filter { it.categoryId != totalCategoryId }
            .map { budget ->
                val used = usedMap[budget.categoryId] ?: 0L
                toDto(
                    budget,
                    categoryMap[budget.categoryId]?.name,
                    categoryMap[budget.categoryId]?.icon,
                    used
                )
            }

        // 固定支出预留：把本期还没到期的房租 / 房贷等从「每天可花」里扣掉，
        // 否则月中交完房租，日均可用会被算成 0 甚至负数
        val reservation = reservedFixed(bookId, period, today)
        val flexibleRemaining = remaining - reservation.total
        val spendable = if (reservation.total > 0L) flexibleRemaining else remaining

        val burnRate = if (elapsedDays > 0) periodExpense.toDouble() / elapsedDays else null
        val forecast = burnRate?.let { rate ->
            periodExpense + Math.round(rate * maxOf(daysLeft - 1, 0))
        }

        return BudgetOverviewDto(
            cycle = BudgetPeriods.wireCycle(BudgetPeriods.cycleOf(period)),
            periodStart = BudgetPeriods.startIso(period),
            period = period,
            hasBudget = totalBudget != null,
            totalBudgetId = totalBudget?.id,
            totalBudget = totalAmount,
            totalUsed = periodExpense,
            remaining = if (totalAmount == 0L) 0L else remaining,
            usedRatio = if (totalAmount == 0L) null else periodExpense.toDouble() / totalAmount,
            exceeded = totalAmount > 0 && periodExpense > totalAmount,
            monthExpense = periodExpense,
            daysInMonth = daysTotal,
            daysTotal = daysTotal,
            daysLeft = daysLeft,
            elapsedDays = elapsedDays,
            dailyAvailable = if (spendable > 0 && daysLeft > 0) spendable / daysLeft else 0L,
            suggestDaily = if (totalAmount == 0L) 0L else totalAmount / daysTotal,
            todayExpense = todayExpense(bookId, today),
            burnRate = burnRate,
            forecastExpense = forecast,
            status = status(totalAmount, periodExpense, forecast, reservation.total),
            fixedReserved = reservation.total,
            flexibleRemaining = if (totalAmount == 0L) 0L else flexibleRemaining,
            items = items,
            overruns = items.filter { it.exceeded }
        )
    }

    private fun toDto(budget: LocalBudget, name: String?, icon: String?, used: Long): BudgetDto {
        val amount = budget.amount
        return BudgetDto(
            id = budget.id,
            categoryId = budget.categoryId,
            categoryName = name ?: "未分类",
            categoryIcon = icon,
            cycle = BudgetPeriods.wireCycle(BudgetPeriods.cycleOf(budget.period)),
            periodStart = BudgetPeriods.startIso(budget.period),
            period = budget.period,
            amount = amount,
            used = used,
            remaining = amount - used,
            usedRatio = if (amount == 0L) null else used.toDouble() / amount,
            exceeded = amount > 0 && used > amount,
            remark = budget.remark
        )
    }

    /**
     * 周期内已过天数（含今天）。
     * 不能写成 `daysTotal - daysLeft + 1`：周期还没开始时 daysLeft == daysTotal，
     * 那个式子会算出 1，把「还没开始」错报成「已经过了一天」。
     */
    private fun elapsedDays(period: String, today: LocalDate, daysTotal: Int): Int {
        val start = BudgetPeriods.startDate(period)
        val end = BudgetPeriods.endDate(period)
        return when {
            today.isBefore(start) -> 0
            !today.isBefore(end) -> daysTotal
            else -> (today.toEpochDay() - start.toEpochDay()).toInt() + 1
        }
    }

    private suspend fun todayExpense(bookId: Long, today: LocalDate): Long {
        val zone = ZoneId.systemDefault()
        val start = today.atStartOfDay(zone).toInstant().toEpochMilli()
        // 用次日 0 点做右开边界，而不是「今天 0 点 + 24h」：夏令时切换那天不是 24 小时
        val end = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return transactionDao.sumBetween(bookId, start, end).expense
    }

    private fun status(totalAmount: Long, used: Long, forecast: Long?, reserved: Long): String = when {
        totalAmount <= 0L -> BudgetOverviewDto.STATUS_NO_BUDGET
        used > totalAmount -> BudgetOverviewDto.STATUS_OVER
        // 预测里的「还没发生的固定支出」也要算进去，否则房租还没扣的那几天会误判成很安全
        forecast != null && forecast + reserved > totalAmount * atRiskRatio ->
            BudgetOverviewDto.STATUS_AT_RISK

        else -> BudgetOverviewDto.STATUS_ON_TRACK
    }

    private suspend fun useRemote(bookId: Long): Boolean =
        bookId != JizhangDatabase.LOCAL_BOOK_ID && !tokenStore.accessToken().isNullOrBlank()
}

/** 本期固定支出预留结果 */
data class FixedReservation(
    /** 本期还没到期的固定支出合计（分） */
    val total: Long = 0L,
    val items: List<FixedReservationItem> = emptyList()
)

data class FixedReservationItem(
    val expense: LocalRecurringExpense,
    /** 本期下一次发生日 */
    val nextDate: LocalDate,
    /** 本期还没到期的发生次数 */
    val count: Int,
    /** 预留金额（分） */
    val amount: Long
)
