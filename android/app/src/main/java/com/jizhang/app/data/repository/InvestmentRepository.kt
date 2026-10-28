package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.InvestmentDao
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalInvestFlow
import com.jizhang.app.data.local.entity.LocalInvestPosition
import com.jizhang.app.data.local.entity.LocalInvestValuation
import com.jizhang.app.data.local.entity.PositionWithValue
import com.jizhang.app.data.local.newClientId
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject
import javax.inject.Singleton

/** 投资账户的类型（`local_account.type = 6`） */
private const val ACCOUNT_TYPE_INVEST = 6

/** 转账的账单类型（1=支出 2=收入 3=转账）；只有转账才可能挂到投资品种上 */
private const val TRANSFER_TYPE = 3

/**
 * 投资总览：一次算清「投了多少、现在值多少、还有多少没分配」。
 * <p>
 * 三个口径必须说清楚，否则数字之间会对不上：
 * <ol>
 *   <li>**总市值**：未估过值的品种按**成本**计，不能按 0 计 ——
 *       否则刚建完品种的第一眼，总市值会凭空塌下去；</li>
 *   <li>**总成本**：净投入（买入 − 卖出），V1 由用户手填；</li>
 *   <li>**未分配资金** = 投资账户余额 − 持仓成本合计。这是全模块最关键的一个数：
 *       它允许你月底转进来 5000 但暂时不分类，不至于因为「必须先建品种」而放弃记账。</li>
 * </ol>
 * 账户余额来自转账实时聚合，所以未分配资金**不需要任何额外维护**。
 */
data class InvestmentOverview(
    val positions: List<PositionWithValue> = emptyList(),
    /** 总市值（分），包含未估值品种的成本 */
    val totalValueFen: Long = 0L,
    /** 总成本（分） */
    val totalCostFen: Long = 0L,
    /** 投资账户余额合计（分）：转账进去的钱，可能还没分配到品种 */
    val accountBalanceFen: Long = 0L,
    /** 未分配资金（分），可能为负（持仓成本填得比实际转入还多） */
    val unallocatedFen: Long = 0L,
    /** 最近一次估值时间；从未估值时为 null */
    val lastValuedAt: Long? = null,
    /** 可挂持仓的投资账户（未归档） */
    val accounts: List<AccountWithBalance> = emptyList()
) {
    /** 浮动盈亏（分）：只算市值与净投入的差，不进收支表 */
    val profitFen: Long get() = totalValueFen - totalCostFen

    /** 收益率；没有成本时为 null（不显示 0%，那是"没投"不是"没赚"） */
    val profitRate: Double? get() = if (totalCostFen > 0L) profitFen.toDouble() / totalCostFen else null

    val hasInvestAccount: Boolean get() = accounts.isNotEmpty()

    /** 有没有「有持仓但从未估值」的品种：界面要提示先估值才看得见收益 */
    val hasUnvalued: Boolean get() = positions.any { it.valuedAt == null }
}

/**
 * 投资持仓仓库。
 * <p>
 * V1 只在本机使用，不参与云同步：改这张表不影响服务端，也不必发版。
 * 表里已经按同步表的样式留好 `pendingSync` / `clientUpdatedAt` 等列，
 * 将来接 [SyncManager] 时只需加 DTO + 端点，不用再写一次数据库迁移。
 */
@Singleton
class InvestmentRepository @Inject constructor(
    private val investmentDao: InvestmentDao,
    private val accountDao: AccountDao
) {

    fun observe(bookId: Long): Flow<InvestmentOverview> = combine(
        investmentDao.observeWithValue(bookId),
        accountDao.observeWithBalance(bookId),
        investmentDao.observeLastValuedAt(bookId)
    ) { positions, accounts, lastValuedAt ->
        val investAccounts = accounts.filter {
            it.type == ACCOUNT_TYPE_INVEST && !it.archived
        }
        val balance = investAccounts.sumOf { it.balance }
        val cost = positions.sumOf { it.costFen }
        InvestmentOverview(
            positions = positions,
            totalValueFen = positions.sumOf { it.marketValueFen },
            totalCostFen = cost,
            accountBalanceFen = balance,
            unallocatedFen = balance - cost,
            lastValuedAt = lastValuedAt,
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
     * @param initialValueFen 新建时顺手填的当前市值；null 表示先估成本、之后单独更新
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
        // 新建时填了市值就直接落一条快照：少一次「建完再点更新」的操作
        if (id == null && initialValueFen != null) {
            insertValuation(positionId, bookId, initialValueFen, now)
        }
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
    }

    /** 更新市值：只追加一条估值快照，不改持仓本身（历史曲线要留着） */
    suspend fun updateValue(bookId: Long, positionId: Long, valueFen: Long) {
        insertValuation(positionId, bookId, valueFen, System.currentTimeMillis())
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

    /** 删除品种：连同估值快照与成本流水一起清掉（纯本地，不保留墓碑） */
    suspend fun deletePosition(positionId: Long) {
        investmentDao.deleteFlowsOf(positionId)
        investmentDao.deleteValuationsOf(positionId)
        investmentDao.deleteById(positionId)
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
        investmentDao.deleteFlowByTransaction(transactionId)
        if (positionId == null || type != TRANSFER_TYPE || amountFen <= 0L) return false
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
        return true
    }

    /** 账单被删除时解除关联：成本必须跟着回退，否则删了账成本还挂着 */
    suspend fun detachTransfer(transactionId: Long) {
        investmentDao.deleteFlowByTransaction(transactionId)
    }

    /** 清空账本时一并清掉成本流水（账单没了，挂在上面的成本也没有意义） */
    suspend fun clearFlows(bookId: Long) {
        investmentDao.deleteFlowsOfBook(bookId)
    }
}

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
