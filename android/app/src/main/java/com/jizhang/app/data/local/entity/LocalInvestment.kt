package com.jizhang.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * 本地投资持仓：某个投资品种（一只基金 / 一只股票 / 一笔理财）。
 * <p>
 * 定位很窄：**只补「钱落在哪个品种上」和「现在值多少钱」两件事**，不重复记流水。
 * 买入/卖出仍然是普通的转账（储蓄卡 → 投资账户，`type = 3`），
 * 因此买股票不会让支出统计暴涨，预算和宠物建议也不受影响。
 * <p>
 * 为什么以「金额」而不是「份额」为一等公民：定投基金真正要回答的是
 * "投了多少、现在值多少、赚了多少"。份额必须先维护净值才能换算，一次要填两个数，
 * 这是绝大多数人放弃持仓功能的原因；[sharesScaled] 只是给想算单价的人留的口子。
 * <p>
 * 当前市值**不落库**：由 [LocalInvestValuation] 里最新一条快照，加上「快照之后发生的
 * 买卖净额」实时推算（加仓不会让市值停在旧值上），与「账户余额不落库、由流水实时聚合」
 * 保持同一个原则，不会出现两处数字打架。
 */
@Entity(
    tableName = "local_invest_position",
    indices = [Index("bookId"), Index("accountId")]
)
data class LocalInvestPosition(
    @PrimaryKey val id: Long,
    val bookId: Long,
    /** 挂在哪个投资账户下（`local_account.type = 6`） */
    val accountId: Long,
    /** 品种名称，如「沪深300ETF」 */
    val name: String,
    /** 基金 / 股票代码，可空 */
    val code: String? = null,
    /** 1=股票 2=基金 3=债券 4=理财 5=其他 */
    val kind: Int = KIND_FUND,
    /** 净投入（分）= 买入 − 卖出；V1 由用户手填，V2 起由转账自动累加 */
    val costFen: Long = 0L,
    /** 份额 × 10000（避免浮点漂移），可空 */
    val sharesScaled: Long? = null,
    /** 目标占比（%），0 = 未设（配置偏离提醒留给后续版本） */
    val targetPercent: Int = 0,
    val sort: Int = 0,
    val note: String? = null,
    /**
     * 已实现损益（分）：**只在清仓那一刻定格一次**，之后不再变。
     * <p>
     * 为什么必须有这个字段：成本口径是「净投入」，卖出额会直接冲减成本，
     * 于是「卖出价 − 成本」这个差额在数据里没有落点 —— 清仓后删掉品种，
     * 这笔盈亏就从账本里彻底消失，用户只能靠记一笔假支出把它补回来，
     * 结果投资亏损混进消费统计。定格在这里之后：
     * 「账户里的现金 = 本金 + 已实现 − 在仓成本」，资产口径才对得上。
     * <p>
     * 只有清仓（[archived] = true）的持仓才可能有非 0 值；在仓品种恒为 0。
     */
    val realizedFen: Long = 0L,
    /** 清仓时间（epoch millis）；null = 还在仓 */
    val closedAt: Long? = null,
    /** 归档：不再出现在列表里，但历史估值保留 */
    val archived: Boolean = false,
    /*
     * 下面四个字段 V1 不参与同步（投资模块当前纯本地），但按同步表的样式先留好，
     * 将来接进 SyncManager 时不用再改表结构、不用再写一次迁移。
     */
    val pendingSync: Boolean = true,
    val pendingDelete: Boolean = false,
    val clientUpdatedAt: Long = System.currentTimeMillis(),
    val serverUpdatedAt: Long? = null
) {
    companion object {
        const val KIND_STOCK = 1
        const val KIND_FUND = 2
        const val KIND_BOND = 3
        const val KIND_WEALTH = 4
        const val KIND_OTHER = 5

        /** 品种类型的展示名，列表与选择器共用一份，避免两处文案漂移 */
        fun kindName(kind: Int): String = when (kind) {
            KIND_STOCK -> "股票"
            KIND_FUND -> "基金"
            KIND_BOND -> "债券"
            KIND_WEALTH -> "理财"
            else -> "其他"
        }
    }
}

/**
 * 投资估值快照：某个持仓在某个时点的市值。
 * <p>
 * 每次「更新市值」写一行，**只增不改**。存快照而不是只存当前值，是为了两件事：
 * <ol>
 *   <li>净值曲线能回填历史 —— 只有一个当前值时，收益曲线只能从今天开始算；</li>
 *   <li>能看出「多久没更新了」，提醒用户估值过期（界面显示"上次估值 12 天前"）。</li>
 * </ol>
 * 不做行情接口是刻意的：记账的节奏是月度，天天盯盘反而更容易追涨杀跌。
 */
@Entity(
    tableName = "local_invest_valuation",
    indices = [Index("positionId"), Index("bookId")]
)
data class LocalInvestValuation(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val positionId: Long,
    /** 该时点的市值（分） */
    val valueFen: Long,
    /** 估值时点（epoch millis） */
    val valuedAt: Long,
    val pendingSync: Boolean = true,
    val pendingDelete: Boolean = false,
    val clientUpdatedAt: Long = System.currentTimeMillis()
)

/**
 * 投资成本流水：把「一笔转账」记账成「某个品种的买入 / 卖出」。
 * <p>
 * 为什么单独一张表而不是给 `local_transaction` 加 `positionId` 列：
 * 加列要一路改到服务端的 `TransactionSaveDto / VO / Entity`，动的是同步协议；
 * 而这张表纯本地，列一加就能用。代价是改/删账单时要按 [transactionId] 反查回退，
 * 这一条在 `InvestmentRepository.syncTransfer` 里统一处理。
 * <p>
 * [transactionId] 唯一：一笔转账最多只能算到一个品种头上，重复记账会让成本凭空翻倍。
 */
@Entity(
    tableName = "local_invest_flow",
    indices = [
        Index("positionId"),
        Index("bookId"),
        Index(value = ["transactionId"], unique = true)
    ]
)
data class LocalInvestFlow(
    @PrimaryKey val id: Long,
    val bookId: Long,
    val positionId: Long,
    /** 关联的转账流水 id（`local_transaction.id`）；改/删那笔转账时按它反查 */
    val transactionId: Long,
    /** 1 = 买入（钱转入投资账户） 2 = 卖出（钱从投资账户转出） */
    val side: Int,
    /** 计入成本的金额（分） */
    val amountFen: Long,
    /** 发生时间：算历史时点的成本要用 */
    val occurredAt: Long,
    val pendingSync: Boolean = true,
    val pendingDelete: Boolean = false,
    val clientUpdatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        const val SIDE_BUY = 1
        const val SIDE_SELL = 2
    }
}

/**
 * 持仓 + 最新市值的只读投影（Room 查询结果，非数据库表）。
 * <p>
 * [valueFen] / [valuedAt] 为 null 表示这个品种**从未估值过**——此时一律按成本计，
 * 而不是按 0 计，否则新建一个品种就会把总市值打下去一截。
 */
data class PositionWithValue(
    val id: Long,
    val bookId: Long,
    val accountId: Long,
    val name: String,
    val code: String?,
    val kind: Int,
    /** 总成本 = 期初成本 + 转账自动累计（买入 − 卖出） */
    val costFen: Long,
    val sharesScaled: Long?,
    val targetPercent: Int,
    val sort: Int,
    val note: String?,
    val archived: Boolean,
    /** 当前市值（分）= 最新快照值 + 快照之后的买卖净额；null 表示从未估值，按成本计 */
    val valueFen: Long?,
    /** 最新快照的时点（不是推算时点）：界面「上次估值 N 天前」用 */
    val valuedAt: Long?,
    /** 编辑弹窗回填用的「期初成本」：转账自动记的那部分不该被手改覆盖 */
    val manualCostFen: Long = 0L,
    /** 转账自动累计的成本（可为负：卖出多于买入时） */
    val flowCostFen: Long = 0L,
    /** 清仓时定格的已实现损益（分）：在仓品种恒为 0 */
    val realizedFen: Long = 0L,
    /** 清仓时间；null = 还在仓 */
    val closedAt: Long? = null
) {
    /** 计入总市值：没估过值就按成本计，不当作 0 */
    val marketValueFen: Long get() = valueFen ?: costFen

    /** 浮动盈亏（分），可能为负 */
    val profitFen: Long get() = marketValueFen - costFen

    /** 收益率；成本为 0 时没有意义，返回 null */
    val profitRate: Double? get() = if (costFen > 0L) profitFen.toDouble() / costFen else null
}

/**
 * 按账户汇总的持仓市值与成本（Room 只读投影）。
 * <p>
 * 统计页不能直接把投资账户的「余额（本金）」换成市值：那样会吞掉账户里还没归到
 * 品种上的现金（未分配资金），也会让「只填成本、没走转账」的品种凭空增加净资产。
 * 正确口径是 `本金余额 + (市值 − 成本)`，所以两个数必须一起返回。
 * <p>
 * 没有持仓的账户不会出现在结果里，调用方回落到原余额。
 */
data class AccountMarketValue(
    val accountId: Long,
    /** 该账户下持仓的最新市值合计（从未估值的品种按成本计） */
    val valueFen: Long,
    /** 该账户下持仓的总成本合计（期初 + 转账累计），用于算未实现盈亏 */
    val costFen: Long
)

/**
 * 按账户汇总的已实现损益（Room 只读投影）。
 * <p>
 * 含已清仓的品种：账户的投资资产 = 本金 + 已实现 + 浮动，少了这一项，
 * 清仓后账户会凭空少一笔钱。全清仓的账户不在 [AccountMarketValue] 结果里，
 * 所以这条汇总必须独立查询。
 */
data class AccountRealized(
    val accountId: Long,
    /** 该账户下所有品种（含已清仓）定格下来的已实现损益合计（分） */
    val realizedFen: Long
)
