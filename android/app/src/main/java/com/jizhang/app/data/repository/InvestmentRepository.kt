package com.jizhang.app.data.repository

import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.InvestmentDao
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalInvestFlow
import com.jizhang.app.data.local.entity.LocalInvestPosition
import com.jizhang.app.data.local.entity.LocalInvestValuation
import com.jizhang.app.data.local.entity.PositionWithValue
import com.jizhang.app.data.local.newClientId
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.remote.ApiService
import com.jizhang.app.data.remote.dto.InvestDataDto
import com.jizhang.app.data.remote.dto.InvestFlowDto
import com.jizhang.app.data.remote.dto.InvestPositionDto
import com.jizhang.app.data.remote.dto.InvestSyncRequest
import com.jizhang.app.data.remote.dto.InvestValuationDto
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/** 投资账户的类型（`local_account.type = 6`） */
private const val ACCOUNT_TYPE_INVEST = 6

/** 转账的账单类型（1=支出 2=收入 3=转账）；只有转账才可能挂到投资品种上 */
private const val TRANSFER_TYPE = 3

/**
 * 「顺手推一次服务端」的等待上限。
 * <p>
 * 数据已经落在本地了，推送只是同步的一部分：断网或后端没发版时不能让保存按钮一直转圈，
 * 超时就留给下次同步（[pushPending] 会补推）。
 */
private const val PUSH_TIMEOUT_MS = 2_000L

/**
 * 投资资产的唯一口径：投资页、统计页、账户页共用一份，任何页面都不再自己加减。
 * <p>
 * 五个数的关系必须恒成立，错一处就会让两个页面对同一个账户给出两个数：
 * <ol>
 *   <li>**本金** = 投资账户的转入 − 转出（由流水聚合，不是手工填的）；</li>
 *   <li>**成本** = 持仓的期初成本 + 买入 − 卖出；</li>
 *   <li>**市值** = 最新估值 + 快照之后的买卖净额；从未估值的按成本计
 *       （按 0 计会让新建品种后净资产凭空塌一截）；</li>
 *   <li>**未分配** = 本金 − 成本（转进来但还没归到品种上的钱，成本填多了则为负）；</li>
 *   <li>**投资资产** = 本金 + 浮动盈亏 = 市值 + 未分配。</li>
 * </ol>
 * 第 5 条是关键：直接拿市值当资产会吞掉未分配现金，直接拿本金又会让浮盈浮亏
 * 永远进不了净资产。两者都不对。
 */
data class InvestmentSummary(
    /** 持仓总市值（分） */
    val marketValueFen: Long = 0L,
    /** 持仓总成本（分） */
    val costFen: Long = 0L,
    /** 投资账户本金余额合计（分） */
    val principalFen: Long = 0L,
    /** 未分配资金（分） */
    val unallocatedFen: Long = 0L,
    /** 最近一次估值时间；从未估值时为 null */
    val lastValuedAt: Long? = null
) {
    /** 浮动盈亏（分）：只算市值与净投入的差，不进收支表 */
    val profitFen: Long get() = marketValueFen - costFen

    /** 计入净资产的投资资产（分） */
    val assetFen: Long get() = principalFen + profitFen

    /** 收益率；没有成本时为 null（不显示 0%，那是"没投"不是"没赚"） */
    val profitRate: Double? get() = if (costFen > 0L) profitFen.toDouble() / costFen else null

    /** 没有任何投资账户与持仓 */
    val isEmpty: Boolean get() = principalFen == 0L && costFen == 0L && marketValueFen == 0L

    companion object {
        val EMPTY = InvestmentSummary()
    }
}

/**
 * 三个页面共用的汇总函数 —— 口径只在这里写一次。
 * <p>
 * 之所以抽出来：投资页、统计页、账户页原先各算一套（一个用 Flow、两个用 SQL 聚合），
 * 加仓后一处更新、另一处没更新，用户就会看到三个不同的数。
 */
internal fun summarizeInvest(
    positions: List<PositionWithValue>,
    investAccounts: List<AccountWithBalance>,
    lastValuedAt: Long?
): InvestmentSummary {
    val marketValue = positions.sumOf { it.marketValueFen }
    val cost = positions.sumOf { it.costFen }
    val principal = investAccounts.sumOf { it.balance }
    return InvestmentSummary(
        marketValueFen = marketValue,
        costFen = cost,
        principalFen = principal,
        unallocatedFen = principal - cost,
        lastValuedAt = lastValuedAt
    )
}

/**
 * 投资总览：持仓明细 + 汇总口径 + 可挂账户。
 * <p>
 * 所有数字都从 [summary] 派生，页面只做展示，不再各自加减。
 */
data class InvestmentOverview(
    val positions: List<PositionWithValue> = emptyList(),
    /** 唯一口径：本金 / 成本 / 市值 / 未分配 / 浮动都在这里 */
    val summary: InvestmentSummary = InvestmentSummary.EMPTY,
    /** 可挂持仓的投资账户（未归档） */
    val accounts: List<AccountWithBalance> = emptyList()
) {
    /** 总市值（分），包含未估值品种的成本 */
    val totalValueFen: Long get() = summary.marketValueFen

    /** 总成本（分） */
    val totalCostFen: Long get() = summary.costFen

    /** 投资账户余额合计（分）：转账进去的钱，可能还没分配到品种 */
    val accountBalanceFen: Long get() = summary.principalFen

    /** 未分配资金（分），可能为负（持仓成本填得比实际转入还多） */
    val unallocatedFen: Long get() = summary.unallocatedFen

    /** 最近一次估值时间；从未估值时为 null */
    val lastValuedAt: Long? get() = summary.lastValuedAt

    /** 浮动盈亏（分） */
    val profitFen: Long get() = summary.profitFen

    /** 收益率；没有成本时为 null */
    val profitRate: Double? get() = summary.profitRate

    val hasInvestAccount: Boolean get() = accounts.isNotEmpty()

    /** 有没有「有持仓但从未估值」的品种：界面要提示先估值才看得见收益 */
    val hasUnvalued: Boolean get() = positions.any { it.valuedAt == null }
}

/**
 * 投资持仓仓库。
 * <p>
 * ### 读写路径（离线优先，与预算/固定支出同一套约定）
 * - **读**：本地 Room 聚合，离线照常可用。
 * - **写**：先落本地并标记 `pendingSync`，界面立刻生效；随后尽力推一次服务端（REST），
 *   推不上去就留在待同步队列，由 [pushPending] 补推。
 * - **删**：已经同步过的品种打**墓碑**（`pendingDelete`），界面立刻消失，推成功后再物理删 ——
 *   否则断网时删掉的品种会在下次拉取时「复活」。从没推上去过的直接物理删。
 * - **拉**：跨设备变更由 [mergeFromServer] 带回，本地待推的改动优先，不被服务端旧值覆盖。
 *
 * ### 为什么流水不推墓碑
 * 服务端对每个持仓的流水是**全量覆盖**（先删该持仓的所有流水再插入客户端这份）。
 * 因此本地删掉一笔流水不需要单独通知服务端，只要把持仓标记为待推，下次推送自然消失 ——
 * 省掉一张墓碑表，也避免了「墓碑和新流水抢 `transactionId` 唯一索引」的难题。
 */
@Singleton
class InvestmentRepository @Inject constructor(
    private val investmentDao: InvestmentDao,
    private val accountDao: AccountDao,
    private val tokenStore: TokenStore,
    private val api: ApiService
) {

    fun observe(bookId: Long): Flow<InvestmentOverview> = combine(
        investmentDao.observeWithValue(bookId),
        accountDao.observeWithBalance(bookId),
        investmentDao.observeLastValuedAt(bookId)
    ) { positions, accounts, lastValuedAt ->
        val investAccounts = accounts.filter {
            it.type == ACCOUNT_TYPE_INVEST && !it.archived
        }
        InvestmentOverview(
            positions = positions,
            summary = summarizeInvest(positions, investAccounts, lastValuedAt),
            accounts = investAccounts
        )
    }

    /** 投资账户列表（新建持仓时选归属账户用；不需要实时流） */
    suspend fun investAccounts(bookId: Long): List<AccountWithBalance> =
        accountDao.listWithBalance(bookId).filter { it.type == ACCOUNT_TYPE_INVEST && !it.archived }

    /**
     * 新建或修改一个品种。
     *
     * @param id 传入已有 id 表示编辑；null 表示新建
     * @param initialValueFen 顺手填的当前市值（新建、编辑都可）；null 表示不动市值
     */
    suspend fun savePosition(
        id: Long?,
        bookId: Long,
        accountId: Long,
        name: String,
        code: String?,
        kind: Int,
        costFen: Long,
        initialValueFen: Long? = null,
        note: String? = null
    ): Long {
        val now = System.currentTimeMillis()
        val existing = id?.let { investmentDao.findById(it) }
        val positionId = id ?: newClientId()
        investmentDao.upsert(
            LocalInvestPosition(
                id = positionId,
                bookId = bookId,
                accountId = accountId,
                name = name.trim(),
                code = code?.trim()?.takeIf { it.isNotEmpty() },
                kind = kind,
                costFen = costFen.coerceAtLeast(0L),
                // 保留编辑前的排序与份额，避免「改个名字把顺序打乱」
                sharesScaled = existing?.sharesScaled,
                sort = existing?.sort ?: (investmentDao.maxSort(bookId) + 1),
                note = note?.trim()?.takeIf { it.isNotEmpty() },
                clientUpdatedAt = now
            )
        )
        // 填了市值就直接落一条快照：新建时省掉「建完再点更新」，编辑时能把改成本
        // 和校准市值一次做完（只改期初成本时市值不会自动跟，用户容易漏）
        if (initialValueFen != null) {
            insertValuation(positionId, bookId, initialValueFen, now)
        }
        tryPush(bookId)
        return positionId
    }

    /** 只改成本（净值/买入额变化时用），不动名称与归属 */
    suspend fun updateCost(positionId: Long, costFen: Long) {
        val existing = investmentDao.findById(positionId) ?: return
        investmentDao.upsert(
            existing.copy(
                costFen = costFen.coerceAtLeast(0L),
                clientUpdatedAt = System.currentTimeMillis()
            )
        )
        tryPush(existing.bookId)
    }

    /** 更新市值：只追加一条估值快照，不改持仓本身（历史曲线要留着） */
    suspend fun updateValue(bookId: Long, positionId: Long, valueFen: Long) {
        insertValuation(positionId, bookId, valueFen, System.currentTimeMillis())
        tryPush(bookId)
    }

    /**
     * 批量更新市值（投资页「更新全部市值」一次提交多个品种）。
     * <p>
     * 同一时刻写同一个 `valuedAt`，界面上就是「这一轮看过了」；历史曲线仍按时间取最新。
     * 调用方只需传真正变过的品种 —— 全量补快照会把曲线糊满水平线，也看不出哪天真的核对过。
     */
    suspend fun updateValues(bookId: Long, values: Map<Long, Long>) {
        if (values.isEmpty()) return
        val at = System.currentTimeMillis()
        values.forEach { (positionId, valueFen) ->
            // 弹窗打开期间品种可能已被删除：给不存在的持仓写估值只会留下孤儿数据
            if (investmentDao.findById(positionId) != null) {
                insertValuation(positionId, bookId, valueFen, at)
            }
        }
        tryPush(bookId)
    }

    private suspend fun insertValuation(positionId: Long, bookId: Long, valueFen: Long, at: Long) {
        investmentDao.insertValuation(
            LocalInvestValuation(
                id = newClientId(),
                bookId = bookId,
                positionId = positionId,
                valueFen = valueFen.coerceAtLeast(0L),
                valuedAt = at,
                clientUpdatedAt = at
            )
        )
    }

    /**
     * 删除品种。
     * <p>
     * 已经同步过的打**墓碑**（界面立刻消失，推送成功后再物理删），否则下次拉取会把它「复活」；
     * 从没推上去过的（或纯本地账本）直接物理删，不给服务端添垃圾。
     */
    suspend fun deletePosition(positionId: Long) {
        val existing = investmentDao.findById(positionId) ?: return
        if (existing.pendingSync || !useRemote(existing.bookId)) {
            investmentDao.deleteFlowsOf(positionId)
            investmentDao.deleteValuationsOf(positionId)
            investmentDao.deleteById(positionId)
        } else {
            investmentDao.markPendingDelete(positionId)
        }
        tryPush(existing.bookId)
    }

    /** 某账户下还挂着几个品种：删除投资账户前要先拦下来 */
    suspend fun countByAccount(accountId: Long): Int = investmentDao.countByAccount(accountId)

    /** 某投资账户下可选的品种（记账页「转账 → 归属品种」用） */
    suspend fun positionsOfAccount(accountId: Long): List<LocalInvestPosition> =
        investmentDao.listByAccount(accountId)

    /** 某笔转账当前挂在哪个品种上（编辑账单时回填） */
    suspend fun positionIdOfTransaction(transactionId: Long): Long? =
        investmentDao.positionIdOfTransaction(transactionId)

    /**
     * 把一笔转账记成某品种的买入 / 卖出。
     * <p>
     * 方向不用用户选，由账户判定：钱**转入**持仓所属账户 = 买入，**转出** = 卖出。
     * 让用户手选「这是买还是卖」是多余的一步，而且选反了成本就会反向累计。
     * <p>
     * 实现上**先删后建**：改账单时品种、金额、方向都可能变，逐个比对字段既繁琐又容易残留
     * 一条旧记录（表现成成本凭空翻倍）。`transactionId` 上的唯一索引兜住并发重复。
     *
     * @return 是否记成功；品种不存在或这笔转账与该品种的账户无关时返回 false
     */
    suspend fun syncTransfer(
        bookId: Long,
        transactionId: Long,
        positionId: Long?,
        type: Int,
        amountFen: Long,
        accountId: Long,
        toAccountId: Long?,
        occurredAt: Long
    ): Boolean {
        // 改账单时可能换了品种：原来挂的那个也要标回待推，否则服务端还留着旧流水
        val previous = investmentDao.findFlowByTransaction(transactionId)
        investmentDao.deleteFlowByTransaction(transactionId)
        previous?.let { investmentDao.markPendingSync(it.positionId) }

        if (positionId == null || type != TRANSFER_TYPE || amountFen <= 0L) {
            tryPush(bookId)
            return false
        }
        val position = investmentDao.findById(positionId) ?: return false
        val side = investSideOf(
            positionAccountId = position.accountId,
            accountId = accountId,
            toAccountId = toAccountId
        ) ?: return false
        investmentDao.upsertFlow(
            LocalInvestFlow(
                id = newClientId(),
                bookId = bookId,
                positionId = positionId,
                transactionId = transactionId,
                side = side,
                amountFen = amountFen,
                occurredAt = occurredAt,
                clientUpdatedAt = System.currentTimeMillis()
            )
        )
        // 服务端按持仓「全量覆盖」流水，所以只要把持仓标回待推，这次增删就会被一并带上去
        investmentDao.markPendingSync(positionId)
        tryPush(bookId)
        return true
    }

    /** 账单被删除时解除关联：成本必须跟着回退，否则删了账成本还挂着 */
    suspend fun detachTransfer(transactionId: Long) {
        val previous = investmentDao.findFlowByTransaction(transactionId) ?: return
        investmentDao.deleteFlowByTransaction(transactionId)
        investmentDao.markPendingSync(previous.positionId)
        tryPush(previous.bookId)
    }

    /** 清空账本时一并清掉成本流水（账单没了，挂在上面的成本也没有意义） */
    suspend fun clearFlows(bookId: Long) {
        investmentDao.deleteFlowsOfBook(bookId)
    }

    /* ==================== 与同步协作 ==================== */

    /**
     * 把本地待同步的投资数据补推到服务端。
     * <p>
     * 一个请求带齐三张表：服务端先删本批持仓的流水再按这份重建，所以客户端永远只推
     * 「当前状态」，不需要为流水单独维护删除标记。
     * <p>
     * 单条失败不影响交易同步（属于附属数据），异常与业务失败一律吞掉并返回 0，
     * 记录留在 `pendingSync` 里等下一轮。
     *
     * @return 成功推送（含删除、含估值）的条数
     */
    suspend fun pushPending(bookId: Long): Int {
        if (!useRemote(bookId)) return 0
        val pendingDelete = investmentDao.listPendingDelete(bookId)
        val pending = investmentDao.listPending(bookId)
        val pendingValues = investmentDao.listPendingValuations(bookId)
        if (pending.isEmpty() && pendingDelete.isEmpty() && pendingValues.isEmpty()) return 0

        val request = InvestSyncRequest(
            bookId = bookId,
            positions = pending.map { it.toDto() },
            valuations = pendingValues.map { it.toDto() },
            // 每个待推持仓带上它的**全部**流水：删掉的那几笔自然不会出现在请求里
            flows = pending.flatMap { position ->
                investmentDao.listFlows(position.id).map { it.toDto() }
            },
            deletedPositionIds = pendingDelete.map { it.id }
        )
        val result = runCatching { api.syncInvest(request) }.getOrNull() ?: return 0
        if (!result.isSuccess) return 0

        val deletedIds = pendingDelete.map { it.id }
        if (deletedIds.isNotEmpty()) {
            // 服务端已级联删掉它们的估值与流水，本地跟着清干净
            investmentDao.deleteValuationsByPositionIds(deletedIds)
            investmentDao.deleteFlowsByPositionIds(deletedIds)
            investmentDao.deleteByIds(deletedIds)
        }
        if (pending.isNotEmpty()) {
            investmentDao.markSynced(pending.map { it.id })
        }
        if (pendingValues.isNotEmpty()) {
            investmentDao.markValuationsSynced(pendingValues.map { it.id })
        }
        return pending.size + deletedIds.size + pendingValues.size
    }

    /**
     * 合并拉取回来的投资数据。
     * <p>
     * 本地待推的改动优先：等 [pushPending] 推上去之后再接受服务端版本，
     * 否则刚建好的品种会被服务端的旧值盖掉。
     *
     * @return 实际写入本地的条数
     */
    suspend fun mergeFromServer(bookId: Long, data: InvestDataDto): Int {
        if (data.isEmpty) return 0
        val pendingIds = investmentDao.listPending(bookId).mapTo(mutableSetOf()) { it.id }
        val pendingDeleteIds = investmentDao.listPendingDelete(bookId).mapTo(mutableSetOf()) { it.id }
        val pendingValueIds = investmentDao.listPendingValuations(bookId).mapTo(mutableSetOf()) { it.id }
        var changed = 0

        for (dto in data.positions) {
            if (dto.id in pendingIds || dto.id in pendingDeleteIds) continue
            investmentDao.upsert(dto.toLocal(bookId))
            changed++
        }
        for (dto in data.valuations) {
            if (dto.id in pendingValueIds) continue
            // 估值只增不改，同 id 重复写入是幂等的
            investmentDao.insertValuation(dto.toLocal(bookId))
            changed++
        }
        for (dto in data.flows) {
            // IGNORE：本地刚挂上的流水不能被服务端的旧记录顶掉（transactionId 上有唯一索引）
            investmentDao.insertFlowIgnore(dto.toLocal(bookId))
            changed++
        }
        return changed
    }

    /**
     * 拉取服务端的投资数据并合并。
     * <p>
     * 后端没发版（接口 404）或断网时返回 0，不影响本地使用 ——
     * 投资模块在服务端就绪前必须是「纯本地也能跑」的状态，否则一次发版事故会让整页打不开。
     */
    suspend fun pullRemote(bookId: Long): Int {
        if (!useRemote(bookId)) return 0
        val result = runCatching { api.investData(bookId) }.getOrNull() ?: return 0
        val data = result.data ?: return 0
        return mergeFromServer(bookId, data)
    }

    /** 尽力推一次：失败不影响本地，留给下次同步 */
    private suspend fun tryPush(bookId: Long) {
        if (!useRemote(bookId)) return
        withTimeoutOrNull(PUSH_TIMEOUT_MS) { runCatching { pushPending(bookId) } }
    }

    /** 本地账本 / 未登录时不同步：所有写入只留在本机 */
    private suspend fun useRemote(bookId: Long): Boolean =
        bookId != JizhangDatabase.LOCAL_BOOK_ID && !tokenStore.accessToken().isNullOrBlank()
}

/* ==================== DTO 映射 ==================== */

private fun LocalInvestPosition.toDto() = InvestPositionDto(
    id = id,
    bookId = bookId,
    accountId = accountId,
    name = name,
    code = code,
    kind = kind,
    costFen = costFen,
    sharesScaled = sharesScaled,
    targetPercent = targetPercent,
    sort = sort,
    note = note,
    archived = archived
)

private fun LocalInvestValuation.toDto() = InvestValuationDto(
    id = id,
    bookId = bookId,
    positionId = positionId,
    valueFen = valueFen,
    valuedAt = valuedAt
)

private fun LocalInvestFlow.toDto() = InvestFlowDto(
    id = id,
    bookId = bookId,
    positionId = positionId,
    transactionId = transactionId,
    side = side,
    amountFen = amountFen,
    occurredAt = occurredAt
)

/** 服务端版本落本地时 `pendingSync` 必须是 false，否则下次又被推一遍 */
private fun InvestPositionDto.toLocal(bookId: Long) = LocalInvestPosition(
    id = id,
    bookId = bookId,
    accountId = accountId,
    name = name,
    code = code,
    kind = kind,
    costFen = costFen,
    sharesScaled = sharesScaled,
    targetPercent = targetPercent,
    sort = sort,
    note = note,
    archived = archived,
    pendingSync = false,
    pendingDelete = false,
    clientUpdatedAt = System.currentTimeMillis()
)

private fun InvestValuationDto.toLocal(bookId: Long) = LocalInvestValuation(
    id = id,
    bookId = bookId,
    positionId = positionId,
    valueFen = valueFen,
    valuedAt = valuedAt,
    pendingSync = false,
    pendingDelete = false,
    clientUpdatedAt = System.currentTimeMillis()
)

private fun InvestFlowDto.toLocal(bookId: Long) = LocalInvestFlow(
    id = id,
    bookId = bookId,
    positionId = positionId,
    transactionId = transactionId,
    side = side,
    amountFen = amountFen,
    occurredAt = occurredAt,
    pendingSync = false,
    pendingDelete = false,
    clientUpdatedAt = System.currentTimeMillis()
)

/**
 * 判定一笔转账相对某个品种是买入还是卖出。
 * <p>
 * 钱**转入**持仓所属账户 = 买入，**转出** = 卖出，两端都不沾边 = 不计入（null）。
 * <p>
 * 抽成纯函数是为了能直接单测：方向判反了不会报错，只会让成本朝反方向累计，
 * 症状是"卖了之后成本反而变高"，肉眼很难发现。
 *
 * @param positionAccountId 该品种挂靠的投资账户
 * @param accountId 转账的转出账户
 * @param toAccountId 转账的转入账户（支出/收入时为 null）
 */
internal fun investSideOf(
    positionAccountId: Long,
    accountId: Long,
    toAccountId: Long?
): Int? = when {
    toAccountId != null && positionAccountId == toAccountId -> LocalInvestFlow.SIDE_BUY
    positionAccountId == accountId -> LocalInvestFlow.SIDE_SELL
    else -> null
}
