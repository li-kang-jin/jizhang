package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountAmountRow
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.dao.BookMemberDao
import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TagAmountRow
import com.jizhang.app.data.local.dao.TransactionDao
import com.jizhang.app.data.remote.dto.AccountAssetDto
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.remote.dto.MemberReportDto
import com.jizhang.app.data.remote.dto.MemberStatDto
import com.jizhang.app.data.remote.dto.TrendItemDto
import java.time.LocalDate
import java.time.YearMonth
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 报表仓库（统计页的数据源）。
 * <p>
 * 全部基于本地 Room 聚合计算，不请求服务端 —— 断网 / 未登录也能看统计，
 * 登录后本地本身就是服务端数据的镜像，结果一致。
 * <p>
 * 口径约定（各方法共用）：
 * - **转账（type = 3）不算收支**，只在总览里单独给出笔数与金额；
 * - 分类 / 账户 / 成员 / 标签各维度一律排除转账，避免「支出 1000」里混进一笔内部转账；
 * - 所有查询都过滤软删除墓碑（`pendingDelete = 0`），但包含尚未同步的本地记账（那才是真的账）。
 */
@Singleton
class ReportRepository @Inject constructor(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val accountDao: AccountDao,
    private val bookMemberDao: BookMemberDao
) {

    private companion object {
        const val EXPENSE_TYPE = 1
        const val INCOME_TYPE = 2

        /** 排行默认取前几名：再多用户也不会看，反而把卡片撑长 */
        const val RANK_LIMIT = 5
    }

    /* ==================== 总览 ==================== */

    /** 默认看当前月，方便「只要本月收支」的地方（如账单页宠物心情）直接调用 */
    suspend fun overview(bookId: Long, period: ReportPeriod = ReportPeriod.current()): ReportOverview {
        val current = transactionDao.rangeSummary(bookId, period.startMillis, period.endMillis)
        val previous = transactionDao.rangeSummary(
            bookId,
            period.previousPeriod.startMillis,
            period.previousPeriod.endMillis
        )
        val lastYear = transactionDao.rangeSummary(
            bookId,
            period.lastYearPeriod.startMillis,
            period.lastYearPeriod.endMillis
        )
        val days = period.daysCounted.coerceAtLeast(1).toLong()

        return ReportOverview(
            periodLabel = period.label,
            income = current.income,
            expense = current.expense,
            balance = current.income - current.expense,
            incomeCount = current.incomeCount,
            expenseCount = current.expenseCount,
            transferCount = current.transferCount,
            transferAmount = current.transferAmount,
            activeDays = current.activeDays,
            avgExpensePerDay = current.expense / days,
            avgIncomePerDay = current.income / days,
            expenseChainRatio = ratio(current.expense, previous.expense),
            incomeChainRatio = ratio(current.income, previous.income),
            expenseYoYRatio = ratio(current.expense, lastYear.expense),
            incomeYoYRatio = ratio(current.income, lastYear.income)
        )
    }

    /**
     * 某一天的当天明细（日历点开那一格时展示）。
     * <p>
     * 直接把「一天」当成一个 [ReportPeriod] 去查总览，避免另写一条 SQL 后出现两套口径。
     */
    suspend fun dayDetail(bookId: Long, date: LocalDate): DayDetail {
        val day = ReportPeriod(ReportGranularity.DAY, date)
        val row = transactionDao.rangeSummary(bookId, day.startMillis, day.endMillis)
        return DayDetail(
            date = date,
            income = row.income,
            expense = row.expense,
            incomeCount = row.incomeCount,
            expenseCount = row.expenseCount,
            transferCount = row.transferCount,
            transferAmount = row.transferAmount
        )
    }

    /* ==================== 趋势 ==================== */

    /**
     * 收支趋势，横轴随粒度变化：
     * - 周 → 本周 7 天（横轴写星期，比「14 日」好认）
     * - 日 → 24 小时（几点花的钱）
     * - 月 → 当月每天
     * - 年 → 12 个月
     *
     * 缺失的时间点补 0，保证折线不断、柱子等高。
     */
    suspend fun trend(bookId: Long, period: ReportPeriod): List<TrendItemDto> = when (period.granularity) {
        ReportGranularity.WEEK -> dailyTrend(bookId, period, weekdayLabels = true)
        ReportGranularity.DAY -> hourlyTrend(bookId, period)
        ReportGranularity.MONTH -> dailyTrend(bookId, period)
        ReportGranularity.YEAR -> monthlyTrend(bookId, period)
    }

    private suspend fun hourlyTrend(bookId: Long, period: ReportPeriod): List<TrendItemDto> {
        val rows = transactionDao.sumByHour(bookId, period.startMillis, period.endMillis)
            .associateBy { it.period }
        return (0..23).map { hour ->
            val key = "%02d".format(hour)
            val row = rows[key]
            TrendItemDto(
                period = "$hour 时",
                income = row?.income ?: 0L,
                expense = row?.expense ?: 0L,
                balance = (row?.income ?: 0L) - (row?.expense ?: 0L)
            )
        }
    }

    /**
     * 按天分桶（周 / 月共用）。
     *
     * @param weekdayLabels 横轴写成「周一」而不是「14 日」：周视图里 7 根柱子按星期读更顺，
     *                      跨月的那一周也不会出现「30 日 / 1 日」这种跳月的割裂感
     */
    private suspend fun dailyTrend(
        bookId: Long,
        period: ReportPeriod,
        weekdayLabels: Boolean = false
    ): List<TrendItemDto> {
        // 当月只画到今天，历史月画整月：未来日期拉一排 0 会把图压扁
        val today = LocalDate.now()
        val last = when {
            period.startDate.isAfter(today) -> period.startDate
            period.endDate.minusDays(1).isAfter(today) -> today
            else -> period.endDate.minusDays(1)
        }
        if (period.startDate.isAfter(last)) {
            return emptyList()
        }

        val rows = transactionDao.sumByDay(bookId, period.startMillis, period.endMillis)
            .associateBy { it.period }
        val days = (last.toEpochDay() - period.startDate.toEpochDay()).toInt() + 1
        return (0 until days).map { offset ->
            val date = period.startDate.plusDays(offset.toLong())
            val row = rows[date.toString()]
            val income = row?.income ?: 0L
            val expense = row?.expense ?: 0L
            TrendItemDto(
                period = if (weekdayLabels) ReportPeriod.weekdayText(date) else "${date.dayOfMonth} 日",
                income = income,
                expense = expense,
                balance = income - expense
            )
        }
    }

    private suspend fun monthlyTrend(bookId: Long, period: ReportPeriod): List<TrendItemDto> {
        val rows = transactionDao.sumByMonth(bookId, period.startMillis, period.endMillis)
            .associateBy { it.period }
        val yearMonth = YearMonth.from(period.startDate)
        return (1..12).map { month ->
            val key = "%04d-%02d".format(yearMonth.year, month)
            val row = rows[key]
            val income = row?.income ?: 0L
            val expense = row?.expense ?: 0L
            TrendItemDto(
                period = "$month 月",
                income = income,
                expense = expense,
                balance = income - expense
            )
        }
    }

    /* ==================== 分类构成 ==================== */

    /**
     * 分类占比。
     *
     * @param groupByParent true 时把子分类的金额归并到父分类（同时在 children 里保留明细），
     *                      false 时按分类原样平铺
     */
    suspend fun category(
        bookId: Long,
        type: Int,
        period: ReportPeriod,
        groupByParent: Boolean = true
    ): CategoryBreakdown {
        val rows = transactionDao.sumByCategory(bookId, type, period.startMillis, period.endMillis)
        val categoryMap = categoryDao.listByBook(bookId).associateBy { it.id }
        val total = rows.sumOf { it.total }
        if (total == 0L) {
            return CategoryBreakdown(0L, emptyList())
        }

        val flat = rows.map { row ->
            val category = row.categoryId?.let { categoryMap[it] }
            CategorySlice(
                categoryId = row.categoryId,
                name = category?.name ?: "未分类",
                icon = category?.icon,
                amount = row.total,
                ratio = (row.total.toDouble() / total).toFloat()
            )
        }

        if (!groupByParent) {
            return CategoryBreakdown(total, flat)
        }

        // 父分类归并：子分类的金额加到父分类上，父分类自己直接记的账也要算进去
        val childrenByParent = flat
            .filter { slice ->
                val parentId = slice.categoryId?.let { categoryMap[it]?.parentId } ?: 0L
                parentId != 0L
            }
            .groupBy { slice -> categoryMap[slice.categoryId]?.parentId ?: 0L }

        val merged = flat
            .filter { slice ->
                val parentId = slice.categoryId?.let { categoryMap[it]?.parentId } ?: 0L
                parentId == 0L
            }
            .map { parent ->
                val children = childrenByParent[parent.categoryId].orEmpty()
                    .sortedByDescending { it.amount }
                parent.copy(
                    amount = parent.amount + children.sumOf { it.amount },
                    ratio = ((parent.amount + children.sumOf { it.amount }).toDouble() / total).toFloat(),
                    children = children
                )
            }
            // 理论上不会发生：子分类的父分类不在当前类型里（改过类型的历史数据），兜底也列出来
            .let { parents ->
                val orphanTotal = flat.filter { slice ->
                    val parentId = slice.categoryId?.let { categoryMap[it]?.parentId } ?: 0L
                    parentId != 0L && parents.none { it.categoryId == parentId }
                }
                parents + orphanTotal
            }
            .sortedByDescending { it.amount }

        return CategoryBreakdown(total, merged)
    }

    /** 该账本的支出分类，供预算设置选择范围 */
    suspend fun expenseCategories(bookId: Long): List<CategoryStatDto> =
        categoryDao.listByBookAndType(bookId, EXPENSE_TYPE).map {
            CategoryStatDto(
                categoryId = it.id,
                categoryName = it.name,
                icon = it.icon,
                color = it.color
            )
        }

    /* ==================== 排行 ==================== */

    /**
     * 账户收支排行：看清钱从哪张卡进出。
     *
     * @param type 1 = 支出排行，2 = 收入排行
     */
    suspend fun accountRank(bookId: Long, period: ReportPeriod, type: Int): List<RankItem> {
        val rows = transactionDao.sumByAccountGroup(bookId, period.startMillis, period.endMillis)
        if (rows.isEmpty()) {
            return emptyList()
        }
        val accountMap = accountDao.listWithBalance(bookId).associateBy { it.id }
        val total = rows.sumOf { amountOf(it, type) }
        if (total <= 0L) {
            return emptyList()
        }

        return rows
            .mapNotNull { row ->
                val amount = amountOf(row, type)
                if (amount <= 0L) {
                    return@mapNotNull null
                }
                val account = accountMap[row.accountId]
                RankItem(
                    key = row.accountId,
                    name = account?.name ?: "已删除的账户",
                    icon = account?.icon,
                    amount = amount,
                    ratio = (amount.toDouble() / total).toFloat(),
                    count = row.txCount
                )
            }
            .sortedByDescending { it.amount }
            .take(RANK_LIMIT)
    }

    /**
     * 标签排行。
     * <p>
     * 本地标签是一列逗号分隔的字符串（SQLite 没法直接拆分聚合），所以这里只取「标签 + 金额」，
     * 在内存里拆开统计 —— 单期账单量级在千行以内，开销可以忽略；一笔账挂多个标签时每个标签都计入。
     */
    suspend fun tagRank(bookId: Long, period: ReportPeriod, type: Int): List<RankItem> {
        val rows = transactionDao.listTagAmounts(bookId, period.startMillis, period.endMillis)
            .filter { it.type == type }
        if (rows.isEmpty()) {
            return emptyList()
        }

        val amountByTag = mutableMapOf<String, Long>()
        val countByTag = mutableMapOf<String, Long>()
        rows.forEach { row: TagAmountRow ->
            splitTags(row.tags).forEach { tag ->
                amountByTag[tag] = (amountByTag[tag] ?: 0L) + row.amount
                countByTag[tag] = (countByTag[tag] ?: 0L) + 1L
            }
        }
        val total = amountByTag.values.sum()
        if (total <= 0L) {
            return emptyList()
        }

        return amountByTag.entries
            .map { (tag, amount) ->
                RankItem(
                    key = tag.hashCode().toLong(),
                    name = tag,
                    icon = null,
                    amount = amount,
                    ratio = (amount.toDouble() / total).toFloat(),
                    count = countByTag[tag] ?: 0L
                )
            }
            .sortedByDescending { it.amount }
            .take(RANK_LIMIT)
    }

    private fun amountOf(row: AccountAmountRow, type: Int): Long =
        if (type == INCOME_TYPE) row.income else row.expense

    private fun splitTags(tags: String): List<String> =
        tags.split(',').map { it.trim() }.filter { it.isNotEmpty() }

    /* ==================== 记账习惯 ==================== */

    /**
     * 记账习惯：记了多少天、平均一天记几笔、最大单日支出、最常记账的星期、首末笔时间。
     * <p>
     * 这些数据完全来自本地流水，属于「顺手就能算出来」的量化指标，
     * 比再堆一张图更能反映记账这件事本身。
     */
    suspend fun habit(bookId: Long, period: ReportPeriod): HabitStats {
        val summary = transactionDao.rangeSummary(bookId, period.startMillis, period.endMillis)
        val days = period.daysCounted.coerceAtLeast(1)
        val bounds = transactionDao.timeBounds(bookId, period.startMillis, period.endMillis)

        val dayRows = transactionDao.sumByDay(bookId, period.startMillis, period.endMillis)
            .mapNotNull { row ->
                runCatching { LocalDate.parse(row.period) }.getOrNull()
                    ?.let { DayAmount(it, row.income, row.expense) }
            }
        val spentDays = dayRows.filter { it.expense > 0L }

        val weekdayRows = transactionDao.sumByWeekday(bookId, period.startMillis, period.endMillis)
            .associateBy { it.weekday }
        // SQLite 的 %w：0=周日；展示按「周一 … 周日」排，所以把周日的值挪到末尾
        val weekdayExpense = (1..7).map { index ->
            val weekday = index % 7
            weekdayRows[weekday]?.expense ?: 0L
        }

        val totalFlow = summary.income + summary.expense
        val txCount = summary.incomeCount + summary.expenseCount

        return HabitStats(
            activeDays = summary.activeDays.toInt(),
            txCount = txCount,
            avgTxPerDay = txCount.toDouble() / days,
            avgAmountPerDay = totalFlow / days,
            avgAmountPerTx = if (txCount == 0L) 0L else totalFlow / txCount,
            maxDay = spentDays.maxByOrNull { it.expense },
            minDay = spentDays.minByOrNull { it.expense },
            busiestWeekday = weekdayRows.values.maxByOrNull { it.txCount }?.weekday,
            weekdayExpense = weekdayExpense,
            firstRecordTime = bounds.firstTime,
            lastRecordTime = bounds.lastTime
        )
    }

    /** 收支日历：区间内每天的收入 / 支出（没有记账的天不会返回，由界面补空格） */
    suspend fun calendar(bookId: Long, period: ReportPeriod): List<DayAmount> =
        transactionDao.sumByDay(bookId, period.startMillis, period.endMillis)
            .mapNotNull { row ->
                runCatching { LocalDate.parse(row.period) }.getOrNull()
                    ?.let { DayAmount(it, row.income, row.expense) }
            }

    /* ==================== 成员 ==================== */

    /** 按成员统计收支（家庭账本「谁花了多少」） */
    suspend fun memberStats(bookId: Long, period: ReportPeriod): MemberReportDto {
        val rows = transactionDao.sumByMember(bookId, period.startMillis, period.endMillis)
        val memberMap = bookMemberDao.listByBook(bookId).associateBy { it.userId }

        val items = rows.map { row ->
            val member = row.createdBy?.let { memberMap[it] }
            MemberStatDto(
                userId = row.createdBy,
                username = member?.username,
                nickname = member?.nickname,
                alias = member?.alias,
                displayName = member?.label ?: if (row.createdBy == null) "未记录记账人" else "已退出的成员",
                income = row.income,
                expense = row.expense,
                balance = row.income - row.expense,
                count = row.txCount
            )
        }
        return MemberReportDto(
            totalIncome = items.sumOf { it.income },
            totalExpense = items.sumOf { it.expense },
            items = items
        )
    }

    /* ==================== 资产 ==================== */

    /**
     * 资产总览：分开算资产与负债，并给出净资产。
     * <p>
     * 信用卡余额为负 = 欠款，单独归为负债；只有 `includeInTotal = 1` 的账户参与合计
     * （只用来走账的中间账户不该算进身家）。
     */
    suspend fun assets(bookId: Long): AssetSummary {
        val accounts = accountDao.listWithBalance(bookId)
        val items = accounts.map {
            AccountAssetDto(
                accountId = it.id,
                name = it.name,
                type = it.type,
                icon = it.icon,
                balance = it.balance,
                includeInTotal = it.includeInTotal
            )
        }
        val counted = items.filter { it.includeInTotal == 1 }
        val totalAssets = counted.filter { it.type != AssetSummary.CREDIT_CARD_TYPE }
            .sumOf { it.balance }
        val totalLiabilities = counted.filter { it.type == AssetSummary.CREDIT_CARD_TYPE }
            .sumOf { -it.balance }
            .coerceAtLeast(0L)

        return AssetSummary(
            totalAssets = totalAssets,
            totalLiabilities = totalLiabilities,
            netAssets = totalAssets - totalLiabilities,
            accounts = items
        )
    }

    /**
     * 某个时间点的净资产，口径与 [assets] 完全一致：
     * 只有 `includeInTotal = 1` 的账户参与，信用卡按欠款计入负债。
     */
    suspend fun netAssetsAt(bookId: Long, before: Long): Long =
        netOf(accountDao.listWithBalanceBefore(bookId, before))

    /**
     * 期末净资产的逐格序列：与 [trend] 等长、同桶对齐，用于趋势图下方的净值线。
     * <p>
     * 净值是**存量**指标：第 i 格 = 期初净值 + 第 0..i 格的 (收入 - 支出) 之和。
     * 转账在整本账里天然抵消，所以只累加收支增量；某一格没有流水时增量为 0，
     * 线保持水平而不会掉到 0（存量画成 0 是错的）。
     */
    suspend fun assetTrend(bookId: Long, period: ReportPeriod): List<Long> {
        val buckets = trend(bookId, period)
        if (buckets.isEmpty()) {
            return emptyList()
        }
        var net = netAssetsAt(bookId, period.startMillis)
        return buckets.map { bucket ->
            net += bucket.income - bucket.expense
            net
        }
    }

    /** 净资产：资产合计 - 负债合计（信用卡余额为负时翻正计入负债） */
    private fun netOf(accounts: List<AccountWithBalance>): Long {
        val counted = accounts.filter { it.includeInTotal == 1 }
        val totalAssets = counted
            .filter { it.type != AssetSummary.CREDIT_CARD_TYPE }
            .sumOf { it.balance }
        val totalLiabilities = counted
            .filter { it.type == AssetSummary.CREDIT_CARD_TYPE }
            .sumOf { -it.balance }
            .coerceAtLeast(0L)
        return totalAssets - totalLiabilities
    }

    /* ==================== 工具 ==================== */

    /** 环比 / 同比：上期为 0 时无法计算，返回 null（界面显示「—」而不是无穷大） */
    private fun ratio(current: Long, previous: Long): Double? {
        if (previous == 0L) {
            return null
        }
        return (current - previous).toDouble() / previous
    }
}
