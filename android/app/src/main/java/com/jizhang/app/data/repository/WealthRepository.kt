package com.jizhang.app.data.repository

import com.jizhang.app.data.local.dao.CategoryDao
import com.jizhang.app.data.local.dao.TransactionDao
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 统计页卡片用的一小撮结果：只要进度与自由度，不需要跑完整模拟。
 * <p>
 * 卡片在报表首屏，加载开销必须可忽略；完整规划在财富页里按需算。
 */
data class WealthProgress(
    val progress: Double,
    /** 净资产能覆盖多少个月支出；样本不足时为 null */
    val freedomMonths: Double?,
    val fiNumberFen: Long,
    val gapFen: Long,
    val hasEnoughData: Boolean
)

/** 财富页一次要展示的全部结果：主规划 + 杠杆榜 + 压力测试 */
data class WealthPlanning(
    val plan: WealthPlan,
    val leverage: List<LeverageOption>,
    val stress: List<StressResult>
)

/**
 * 财富规划的数据入口：把「账本里的历史」拼成 [WealthInputs]。
 * <p>
 * 两个关键口径：
 * 1. 支出样本**只取有记账的月份**（近 24 个月）。把没记账的月份当 0 会把中位数拉到 0，
 *    年支出被低估，FI 数字随之虚低——比不给结论更糟。
 * 2. 剔除「余额调整」：余额校准不是消费，一笔校准单就能把年支出抬高几千。
 */
@Singleton
class WealthRepository @Inject constructor(
    private val reportRepository: ReportRepository,
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {

    /** 统计页卡片：只算进度，[simulations] 压到很低，避免拖慢报表首屏 */
    suspend fun progress(
        bookId: Long,
        withdrawalRate: Double = WealthPlanner.DEFAULT_WITHDRAWAL_RATE
    ): WealthProgress {
        val plan = WealthPlanner.plan(
            inputsOf(
                sample = sample(bookId),
                withdrawalRate = withdrawalRate,
                simulations = 200,
                maxYears = 5
            )
        )
        return WealthProgress(
            progress = plan.progress,
            freedomMonths = plan.freedomMonths,
            fiNumberFen = plan.fiNumberFen,
            gapFen = plan.gapFen,
            hasEnoughData = plan.hasEnoughData
        )
    }

    /**
     * 财富页：完整规划（蒙特卡洛 + 扇形图 + 杠杆榜）。
     * <p>
     * 杠杆榜与主结论必须基于同一份输入（同一种子、同一批路径），否则各条建议之间不可比。
     */
    suspend fun plan(
        bookId: Long,
        withdrawalRate: Double,
        realReturn: Double,
        targetYears: Int
    ): WealthPlanning {
        val inputs = inputsOf(
            sample = sample(bookId),
            withdrawalRate = withdrawalRate,
            realReturn = realReturn,
            targetYears = targetYears,
            simulations = PLAN_SIMULATIONS
        )
        val plan = WealthPlanner.plan(inputs)
        return WealthPlanning(
            plan = plan,
            leverage = if (plan.hasEnoughData) WealthPlanner.leverage(inputs) else emptyList(),
            stress = if (plan.hasEnoughData) WealthPlanner.stressTest(inputs) else emptyList()
        )
    }

    private fun inputsOf(
        sample: MonthlySample,
        withdrawalRate: Double,
        realReturn: Double = WealthPlanner.DEFAULT_REAL_RETURN,
        targetYears: Int? = null,
        simulations: Int = PLAN_SIMULATIONS,
        maxYears: Int = WealthPlanner.DEFAULT_MAX_YEARS
    ) = WealthInputs(
        netWorthFen = sample.netWorthFen,
        monthlyExpenseFen = sample.expenses,
        monthlyIncomeFen = sample.incomes,
        withdrawalRate = withdrawalRate,
        realReturn = realReturn,
        yearsToTarget = targetYears,
        simulations = simulations,
        maxYears = maxYears
    )

    /**
     * 近 [WINDOW_MONTHS] 个月的月度收支。
     * <p>
     * 起点取「首笔记账所在月」与「当前月 - 23」的较大者：新用户不至于拿一堆 0 去算中位数，
     * 老用户也不会被三年前的一次旅行带偏。
     */
    private suspend fun sample(bookId: Long): MonthlySample {
        val netWorth = reportRepository.assets(bookId).netAssets
        val firstMillis = transactionDao.minTradeTime(bookId)
            ?: return MonthlySample(netWorth, emptyList(), emptyList())

        val zone = ZoneId.systemDefault()
        val currentMonth = YearMonth.now(zone)
        val firstMonth = YearMonth.from(Instant.ofEpochMilli(firstMillis).atZone(zone))
        val startMonth = maxOf(firstMonth, currentMonth.minusMonths(WINDOW_MONTHS - 1L))
        if (startMonth.isAfter(currentMonth)) {
            return MonthlySample(netWorth, emptyList(), emptyList())
        }

        val start = startMonth.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = currentMonth.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()

        // 与预算预测同样的排除口径：余额校准不是消费
        val adjustId = categoryDao.listByBook(bookId)
            .firstOrNull { it.name == AccountRepository.ADJUST_CATEGORY_NAME }?.id ?: 0L

        val rows = transactionDao.sumByMonthExcluding(bookId, start, end, adjustId)
            .associateBy { it.period }

        val months = generateSequence(startMonth) { it.plusMonths(1) }
            .takeWhile { !it.isAfter(currentMonth) }
            .mapNotNull { rows[it.toString()] }
            .toList()

        return MonthlySample(
            netWorthFen = netWorth,
            expenses = months.map { it.expense },
            incomes = months.map { it.income }
        )
    }

    private data class MonthlySample(
        val netWorthFen: Long,
        val expenses: List<Long>,
        val incomes: List<Long>
    )

    private companion object {
        /** 24 个月足够覆盖一个完整的年度周期，又不至于把太久以前的消费习惯算进来 */
        const val WINDOW_MONTHS = 24L

        /** 财富页的模拟次数：5000 条约 20-40ms，够稳也够快 */
        const val PLAN_SIMULATIONS = 5_000
    }
}
