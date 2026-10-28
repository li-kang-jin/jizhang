package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.AccountDao
import com.jizhang.app.data.local.dao.InvestmentDao
import com.jizhang.app.data.local.entity.AccountWithBalance
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

    /** 删除品种：连同它的估值快照一起清掉（V1 没有同步，不需要墓碑） */
    suspend fun deletePosition(positionId: Long) {
        investmentDao.deleteValuationsOf(positionId)
        investmentDao.deleteById(positionId)
    }

    /** 某账户下还挂着几个品种：删除投资账户前要先拦下来 */
    suspend fun countByAccount(accountId: Long): Int = investmentDao.countByAccount(accountId)
}
