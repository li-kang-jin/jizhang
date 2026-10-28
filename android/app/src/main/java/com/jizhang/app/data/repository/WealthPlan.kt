package com.jizhang.app.data.repository

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.random.Random

/**
 * 财富规划的输入：全部金额单位是「分」，与库里的口径一致。
 * <p>
 * [monthlyExpenseFen] / [monthlyIncomeFen] 由仓库层从账本按月聚合（旧 → 新），
 * 这个类只负责算术，不碰数据库，方便单测里直接构造。
 */
data class WealthInputs(
    /** 当前净资产（资产 - 负债） */
    val netWorthFen: Long,
    /** 近若干个月的支出（分），旧 → 新 */
    val monthlyExpenseFen: List<Long>,
    /** 与 [monthlyExpenseFen] 对齐的月收入（分）；没有收入记录就传空表 */
    val monthlyIncomeFen: List<Long> = emptyList(),
    /** 安全提取率（年支出 / FI 资产），4% ≈ 25 倍年支出 */
    val withdrawalRate: Double = WealthPlanner.DEFAULT_WITHDRAWAL_RATE,
    /** 实际收益率（已扣通胀） */
    val realReturn: Double = WealthPlanner.DEFAULT_REAL_RETURN,
    /** 实际收益率的年化波动 */
    val returnVolatility: Double = WealthPlanner.DEFAULT_VOLATILITY,
    /** 支出每年额外膨胀（生活方式升级），实际值之上 */
    val expenseGrowth: Double = 0.0,
    /** 收入每年实际增长 */
    val incomeGrowth: Double = 0.0,
    /** 目标年数（例如"从现在起到退休还有 20 年"）：给了才算「达标概率」 */
    val yearsToTarget: Int? = null,
    val simulations: Int = WealthPlanner.DEFAULT_SIMULATIONS,
    val maxYears: Int = WealthPlanner.DEFAULT_MAX_YEARS,
    /** 固定随机种子：同一组参数必须给出同一组结果，否则用户会以为数字在乱跳 */
    val seed: Long = WealthPlanner.DEFAULT_SEED
)

/**
 * 财富规划的结果。
 * <p>
 * 关键点：**不承诺一个年份**。确定性解只是"如果每年收益恰好等于均值"的那条线，
 * 真正给用户看的是中位数与 P10/P90 区间，以及某个时间点之前的达标概率。
 */
data class WealthPlan(
    /** FI 资产：年支出 / 提取率 */
    val fiNumberFen: Long,
    /**
     * Coast FI：从今天起不再追加投入、只靠复利，到目标年数时刚好滚到 FI 资产所需的今天金额。
     * 越过它就是「工作可选」而不是「必须继续攒」。没设目标年数时为 null。
     */
    val coastFiNumberFen: Long?,
    val netWorthFen: Long,
    /** 进度，可能 > 1（已经超过目标） */
    val progress: Double,
    /** 还差多少（已达标为 0） */
    val gapFen: Long,
    /** 年支出（由账本推出来，不是用户填的） */
    val annualExpenseFen: Long,
    val monthlyExpenseFen: Long,
    /** 年收入；没有收入记录时为 0 */
    val annualIncomeFen: Long,
    /** 年结余 = 收入 - 支出（可能为负） */
    val annualSavingFen: Long,
    /** 储蓄率；没有收入记录时为 null */
    val savingsRate: Double?,
    /** 自由度：净资产能覆盖多少个月支出 */
    val freedomMonths: Double?,
    /** 确定性解（收益恰好等于均值、支出不增长）；无法达标时为 null */
    val deterministicYears: Double?,
    /** 达标年份分位数；超过模拟窗口时为 null（而不是编一个数出来） */
    val medianYears: Int?,
    val p10Years: Int?,
    val p90Years: Int?,
    /** 目标年数（回显用，与算概率时的口径一致） */
    val targetYears: Int?,
    /** 在 [targetYears] 之前达标的概率；没给目标年数时为 null */
    val successProbability: Double?,
    /** 模拟窗口内始终没达标的比例 */
    val neverReaches: Double,
    /** 扇形图的年份轴（0 = 现在），以及对应的 P10/P50/P90 净资产 */
    val fanYears: List<Int>,
    val fanP10: List<Long>,
    val fanP50: List<Long>,
    val fanP90: List<Long>,
    /**
     * 同一条模拟里算出来的**算术均值**路径。
     * <p>
     * 中位数与均值的裂口就是 Jensen 不等式：\(e^{\mathbb{E}[X]} \le \mathbb{E}[e^X]\)。
     * 用「平均收益」规划出来的钱，一半概率拿不到——这两条线必须画在一起，
     * 否则用户会以为中位数那条线"算少了"。
     */
    val fanMean: List<Long>,
    /** 增长解剖：本金与复利两条腿 */
    val anatomy: GrowthAnatomy,
    /** 达标年份直方图：下标 = 达标年（0..[maxYears]），值 = 落在该年的路径条数 */
    val reachedHistogram: List<Int>,
    /** 参与拟合的月份数：不足 3 个月时 [hasEnoughData] 为 false */
    val sampleMonths: Int,
    val hasEnoughData: Boolean,
    /** 参数回显：UI 的"假设与口径"要照抄，不能和计算用的值不一致 */
    val withdrawalRate: Double,
    val realReturn: Double,
    val returnVolatility: Double,
    val expenseGrowth: Double,
    val incomeGrowth: Double,
    val simulations: Int,
    val maxYears: Int,
    val seed: Long
)

/**
 * 增长解剖：把净资产拆成「你放进去的」与「钱自己长出来的」两股。
 * <p>
 * 递推 \(W_t = W_{t-1}(1+r) + S\) 的闭式解可以严格分解：
 * \[
 * W_t = \underbrace{W_0}_{本金} + \underbrace{S\,t}_{累计储蓄} + \underbrace{W_0\big[(1+r)^t-1\big] + S\frac{(1+r)^t-1-rt}{r}}_{累计复利}
 * \]
 * 前两项相加就是 [contributed]（直线），第三项是 [compounded]（指数）——画成堆叠面积，
 * 交叉点之前是「人在攒钱」，之后是「钱在攒钱」。
 */
data class GrowthAnatomy(
    /** 逐年累计投入 = 起始净资产 + 年结余 × t（直线，可能因负结余而递减） */
    val contributed: List<Long>,
    /** 逐年累计复利 = 净资产 − 累计投入（亏损年份为负） */
    val compounded: List<Long>,
    /**
     * 飞轮启动年：复利年增量首次超过当年结余的那一年。
     * <p>
     * 解 \(r W_t = S\) 得 \(t^\* = \ln\frac{2S}{rW_0+S}\big/\ln(1+r)\)；
     * 当 \(W_0 = 0\) 时退化为 \(t^\* = \ln 2/\ln(1+r)\) —— 与放射性半衰期 \(\ln2/\lambda\) 是同一个式子，
     * 也就是说**与你存多少钱无关**，只由收益率决定。
     * 结余 ≤ 0 或收益率 ≤ 0 时不存在（返回 null，不编数字）。
     */
    val flywheelYear: Int?,
    /** 复利年增量恰好等于年结余时的资产临界值 = 年结余 / 收益率 */
    val criticalAssetFen: Long,
    /** 期末累计复利占净资产的比重；总额 ≤ 0 时为 null */
    val compoundShare: Double?
)

/**
 * 杠杆榜上的一条：某个动作能让中位数达标时间提前几年。
 * <p>
 * [yearsSaved] 为正表示提前；用「年」而不是「弹性系数」，因为用户做的是人生决策，不是交论文。
 */
data class LeverageOption(
    val label: String,
    val detail: String,
    val yearsSaved: Double
)

/**
 * 压力情景：往模拟里注入一个"真实的坏运气"，看结论退多少。
 * <p>
 * 三个场景分别对应失业风险、序列收益风险、生活方式膨胀——都是决定 FI 成败的常见变量，
 * 但在单一均值假设里完全看不见。
 */
data class StressProfile(
    /** 第一年收入中断的月数（0 = 不中断） */
    val lostIncomeMonths: Int = 0,
    /** 开头连续几年固定 −30%（序列风险：同样的平均收益，先亏后赚最伤） */
    val crashFirstYears: Int = 0,
    /** 在支出增长之外，每年额外膨胀多少（生活方式升级） */
    val extraExpenseGrowth: Double = 0.0
)

/** 压力测试的一条结果：达标时间推迟多少年、达标概率掉多少 */
data class StressResult(
    val label: String,
    val detail: String,
    /** 中位数达标时间推迟的年数；任一情景在窗口内未达标时为 null */
    val yearsDelayed: Int?,
    /** 达标概率下降的百分点（0.08 = 掉 8 个百分点）；没设目标年数时为 null */
    val successDrop: Double?
)

/**
 * 财务自由 / 财富积累的规划器。
 * <p>
 * 三层结论，从"好懂"到"可信"：
 * 1. **FI 数字与进度**：年支出 / 提取率，和当前净资产比一下；
 * 2. **确定性年数**：收益固定为均值时的闭式解，用来做即时反馈（滑块拖动不卡）；
 * 3. **蒙特卡洛分布**：对收益与收入做逐年抽样，给出中位数与 P10/P90，以及达标概率。
 * <p>
 * 模型诚实说明：收益按对数正态、年度独立同分布；支出按账本中位数外推（可叠加生活方式膨胀）；
 * 不考虑税收、房产、养老金与一次性大额事件。它不是预言，是把"如果维持现在的节奏"算清楚。
 */
object WealthPlanner {

    const val DEFAULT_WITHDRAWAL_RATE = 0.04
    const val DEFAULT_REAL_RETURN = 0.045
    const val DEFAULT_VOLATILITY = 0.15
    const val DEFAULT_SIMULATIONS = 5_000
    const val DEFAULT_MAX_YEARS = 50
    const val DEFAULT_SEED = 20_261_019L

    /** 扇形图最多画多少年：再往后区间会宽到没有信息量 */
    private const val FAN_HORIZON = 40

    /** 杠杆榜每个情景跑的路径数：四个情景 + 基准，比主图省一半，结论只要排序稳定 */
    private const val LEVERAGE_SIMULATIONS = 1_500

    /** 一次性多投的演示金额：10 万元（分） */
    private const val LUMP_SUM_FEN = 10_000_000L

    /** 压力情景里的熊市年份收益：-30% 实际收益，够狠但真实发生过 */
    private const val CRASH_RETURN = -0.30

    /** 压力测试的路径数：与杠杆榜同量级，只看「退了多少」 */
    private const val STRESS_SIMULATIONS = 1_500

    /** 至少几个月的数据才敢出结论 */
    private const val MIN_SAMPLE_MONTHS = 3

    fun plan(inputs: WealthInputs, stress: StressProfile = StressProfile()): WealthPlan {
        val expenses = inputs.monthlyExpenseFen.filter { it >= 0L }
        val sampleMonths = expenses.size
        val monthlyExpense = median(expenses)
        val annualExpense = monthlyExpense * 12

        val incomes = inputs.monthlyIncomeFen.filter { it > 0L }
        val annualIncome = if (incomes.isEmpty()) {
            0L
        } else {
            (incomes.sumOf { it } / incomes.size) * 12
        }
        val annualSaving = annualIncome - annualExpense
        val savingsRate = if (annualIncome > 0L) {
            annualSaving.toDouble() / annualIncome
        } else {
            null
        }

        val withdrawalRate = inputs.withdrawalRate.coerceIn(0.005, 0.1)
        val realReturn = inputs.realReturn.coerceIn(-0.5, 0.2)
        val volatility = inputs.returnVolatility.coerceIn(0.0, 0.6)
        val expenseGrowth = inputs.expenseGrowth.coerceIn(-0.1, 0.2)
        val incomeGrowth = inputs.incomeGrowth.coerceIn(-0.1, 0.2)

        // 年支出是 0 时 FI 数字也是 0，任何"进度"都没有意义：直接给一个明确的空结果
        if (annualExpense <= 0L) {
            return emptyPlan(inputs, sampleMonths, annualIncome, withdrawalRate, realReturn, volatility)
        }

        val fiNumber = (annualExpense / withdrawalRate).roundToLong()
        val netWorth = max(0L, inputs.netWorthFen)
        val progress = if (fiNumber > 0L) netWorth.toDouble() / fiNumber else 0.0
        val gap = max(0L, fiNumber - netWorth)
        val freedomMonths = netWorth.toDouble() / monthlyExpense

        val deterministic = deterministicYears(
            netWorthFen = netWorth,
            annualSavingFen = annualSaving,
            fiNumberFen = fiNumber,
            realReturn = realReturn
        )

        val simulations = inputs.simulations.coerceIn(200, 20_000)
        val maxYears = inputs.maxYears.coerceIn(5, 60)
        val targetYears = inputs.yearsToTarget?.coerceIn(0, maxYears)

        val result = simulate(
            netWorthFen = netWorth,
            annualExpenseFen = annualExpense,
            annualIncomeFen = annualIncome,
            fiNumberFen = fiNumber,
            withdrawalRate = withdrawalRate,
            realReturn = realReturn,
            volatility = volatility,
            expenseGrowth = expenseGrowth,
            incomeGrowth = incomeGrowth,
            targetYears = targetYears,
            simulations = simulations,
            maxYears = maxYears,
            seed = inputs.seed,
            stress = stress
        )

        return WealthPlan(
            fiNumberFen = fiNumber,
            coastFiNumberFen = targetYears?.let { coastFiNumber(fiNumber, realReturn, it) },
            netWorthFen = netWorth,
            progress = progress,
            gapFen = gap,
            annualExpenseFen = annualExpense,
            monthlyExpenseFen = monthlyExpense,
            annualIncomeFen = annualIncome,
            annualSavingFen = annualSaving,
            savingsRate = savingsRate,
            freedomMonths = freedomMonths,
            deterministicYears = deterministic,
            medianYears = result.medianYears,
            p10Years = result.p10Years,
            p90Years = result.p90Years,
            targetYears = targetYears,
            successProbability = result.successProbability,
            neverReaches = result.neverReaches,
            fanYears = result.fanYears,
            fanP10 = result.fanP10,
            fanP50 = result.fanP50,
            fanP90 = result.fanP90,
            fanMean = result.fanMean,
            anatomy = anatomy(
                netWorthFen = netWorth,
                annualSavingFen = annualSaving,
                realReturn = realReturn,
                maxYears = maxYears
            ),
            reachedHistogram = result.reachedHistogram,
            sampleMonths = sampleMonths,
            hasEnoughData = sampleMonths >= MIN_SAMPLE_MONTHS,
            withdrawalRate = withdrawalRate,
            realReturn = realReturn,
            returnVolatility = volatility,
            expenseGrowth = expenseGrowth,
            incomeGrowth = incomeGrowth,
            simulations = simulations,
            maxYears = maxYears,
            seed = inputs.seed
        )
    }

    /**
     * 确定性达标年数：每年「先按固定实际收益增值、年末再投入结余」。
     * <p>
     * \(N_{t+1}=N_t(1+r)+S\) 的闭式解：
     * \(T=\ln\frac{F+S/r}{N+S/r}\big/\ln(1+r)\)，\(r=0\) 时退化为 \((F-N)/S\)。
     * 结余为负且净资产已追不上时返回 null（不编造一个"永远"的数字）。
     */
    fun deterministicYears(
        netWorthFen: Long,
        annualSavingFen: Long,
        fiNumberFen: Long,
        realReturn: Double
    ): Double? {
        if (fiNumberFen <= 0L) return null
        if (netWorthFen >= fiNumberFen) return 0.0
        if (annualSavingFen <= 0L) return null
        return if (realReturn == 0.0) {
            (fiNumberFen - netWorthFen).toDouble() / annualSavingFen
        } else {
            val base = 1.0 + realReturn
            if (base <= 0.0) return null
            val offset = annualSavingFen / realReturn
            ln((fiNumberFen + offset) / (netWorthFen + offset)) / ln(base)
        }
    }

    /**
     * 增长解剖：把逐年净资产拆成「累计投入」与「累计复利」两条腿。
     * <p>
     * 与 [deterministicYears] 用同一个递推 \(W_t=W_{t-1}(1+r)+S\)，因此解可以与它对上：
     * \[
     * W_t = W_0(1+r)^t + S\frac{(1+r)^t-1}{r}
     * \]
     * 拆出「投入」\(W_0+St\) 之后，剩下的就是复利贡献。
     * <p>
     * 飞轮启动年解 \(rW_t=S\)，得 \(t^\*=\ln\frac{2S}{rW_0+S}\big/\ln(1+r)\)；
     * \(W_0=0\) 时退化为 \(\ln 2/\ln(1+r)\)，**与存多少无关**，只由收益率决定
     * （与放射衰变的半衰期 \(\ln2/\lambda\) 同构：两者都是一阶线性系统）。
     */
    fun anatomy(
        netWorthFen: Long,
        annualSavingFen: Long,
        realReturn: Double,
        maxYears: Int
    ): GrowthAnatomy {
        val years = maxYears.coerceIn(1, 60)
        val start = max(0L, netWorthFen)
        val saving = annualSavingFen
        val r = realReturn

        val contributed = ArrayList<Long>(years + 1)
        val compounded = ArrayList<Long>(years + 1)
        for (t in 0..years) {
            val put = start + saving * t
            val total = if (r == 0.0) {
                put.toDouble()
            } else {
                val growth = (1.0 + r).pow(t)
                start * growth + saving * (growth - 1.0) / r
            }
            contributed.add(max(0L, put))
            compounded.add((total - put).roundToLong())
        }

        // 结余非正时复利永远追不上储蓄：不存在「飞轮」，不给年份
        val flywheel = if (r > 0.0 && saving > 0L) {
            val ratio = 2.0 * saving / (r * start + saving)
            when {
                ratio <= 1.0 -> 0
                else -> {
                    val at = ln(ratio) / ln(1.0 + r)
                    if (at.isFinite() && at <= years) at.roundToInt() else null
                }
            }
        } else {
            null
        }

        val totalEnd = contributed.last() + compounded.last()
        return GrowthAnatomy(
            contributed = contributed,
            compounded = compounded,
            flywheelYear = flywheel,
            criticalAssetFen = if (r > 0.0 && saving > 0L) (saving / r).roundToLong() else 0L,
            compoundShare = if (totalEnd > 0L) {
                compounded.last().toDouble() / totalEnd
            } else {
                null
            }
        )
    }

    /**
     * 杠杆榜：哪一根旋钮最值钱。
     * <p>
     * 四个情景各跑一遍同样的模拟（同一种子、同样的路径数），再看中位数达标时间提前了几年。
     * 经济学上这是有限差分弹性 \(\partial T/\partial x\)，但用「年」表达，用户才看得懂：
     * 早期几乎总是「少花」和「多存」赢过「多赚一点收益率」——这正是储蓄率杠杆。
     */
    fun leverage(inputs: WealthInputs): List<LeverageOption> {
        if (inputs.monthlyExpenseFen.isEmpty()) return emptyList()
        val sims = inputs.simulations.coerceIn(300, LEVERAGE_SIMULATIONS)
        val base = inputs.copy(simulations = sims)
        val baseYears = plan(base).medianYears ?: return emptyList()

        val scenarios = buildList {
            add(
                Triple(
                    "少花 10%",
                    "把每个月的支出压掉一成（生活方式的边际）",
                    base.copy(monthlyExpenseFen = base.monthlyExpenseFen.map { (it * 0.9).roundToLong() })
                )
            )
            if (base.monthlyIncomeFen.any { it > 0L }) {
                add(
                    Triple(
                        "多赚 10%",
                        "收入涨一成（跳槽 / 副业），支出不变",
                        base.copy(monthlyIncomeFen = base.monthlyIncomeFen.map { (it * 1.1).roundToLong() })
                    )
                )
            }
            add(
                Triple(
                    "收益率 +1%",
                    "长期实际收益从 ${(base.realReturn * 100).roundToInt()}% 提到 " +
                        "${((base.realReturn + 0.01) * 100).roundToInt()}%",
                    base.copy(realReturn = (base.realReturn + 0.01).coerceAtMost(0.1))
                )
            )
            add(
                Triple(
                    "现在多投 10 万",
                    "一次性把 10 万元放进组合，之后节奏不变",
                    base.copy(netWorthFen = base.netWorthFen + LUMP_SUM_FEN)
                )
            )
        }

        return scenarios.mapNotNull { (label, detail, scenario) ->
            val years = plan(scenario).medianYears ?: return@mapNotNull null
            LeverageOption(
                label = label,
                detail = detail,
                yearsSaved = (baseYears - years).toDouble()
            )
        }.sortedByDescending { it.yearsSaved }
    }

    /**
     * 压力测试：把三种"坏运气"分别注入同一条收益序列，看结论退多少。
     * <p>
     * 这不是为了吓人，而是纠正一个常见错觉：4% 规则与"平均 5% 实际收益"都建立在长期平均上，
     * 而人真正经历的是顺序——先亏两年再涨，和先涨两年再亏，终值一样但中途会被迫下车。
     */
    fun stressTest(inputs: WealthInputs): List<StressResult> {
        if (inputs.monthlyExpenseFen.isEmpty()) return emptyList()
        val sims = inputs.simulations.coerceIn(300, STRESS_SIMULATIONS)
        val base = plan(inputs.copy(simulations = sims))
        // 基准自己在窗口内都到不了，比「退多少」没有意义
        val baseYears = base.medianYears ?: return emptyList()
        val baseProbability = base.successProbability

        val scenarios = listOf(
            StressProfile(lostIncomeMonths = 6) to
                ("失业 6 个月" to "第一年收入砍半，之后回到原收入"),
            StressProfile(crashFirstYears = 2) to
                ("开局两年 −30%" to "长期均值不变，但先亏后赚（序列风险）"),
            StressProfile(extraExpenseGrowth = 0.01) to
                ("支出每年多涨 1%" to "生活方式随收入缓慢升级")
        )

        return scenarios.map { (profile, text) ->
            val scenario = plan(inputs.copy(simulations = sims), profile)
            StressResult(
                label = text.first,
                detail = text.second,
                // null = 这个情景在模拟窗口内没能达标
                yearsDelayed = scenario.medianYears?.let { (it - baseYears).coerceAtLeast(0) },
                successDrop = if (baseProbability != null && scenario.successProbability != null) {
                    (baseProbability - scenario.successProbability).coerceAtLeast(0.0)
                } else {
                    null
                }
            )
        }
    }

    /** Coast FI：不再追加投入，只靠复利在 [yearsUntilRetirement] 年后滚到 [fiNumberFen] 所需的今天金额 */
    fun coastFiNumber(fiNumberFen: Long, realReturn: Double, yearsUntilRetirement: Int): Long {
        if (fiNumberFen <= 0L || yearsUntilRetirement <= 0) return fiNumberFen
        val base = (1.0 + realReturn).coerceAtLeast(0.01)
        return (fiNumberFen / base.pow(yearsUntilRetirement.toDouble())).roundToLong()
    }

    /** 反事实：今天这笔 [fen] 按实际收益 [realReturn] 放 [years] 年后的购买力 */
    fun opportunityCost(fen: Long, realReturn: Double, years: Int): Long {
        if (fen <= 0L || years <= 0) return fen
        return (fen * (1.0 + realReturn).pow(years.toDouble())).roundToLong()
    }

    /* ==================== 蒙特卡洛 ==================== */

    private class SimulationResult(
        val medianYears: Int?,
        val p10Years: Int?,
        val p90Years: Int?,
        val successProbability: Double?,
        val neverReaches: Double,
        val fanYears: List<Int>,
        val fanP10: List<Long>,
        val fanP50: List<Long>,
        val fanP90: List<Long>,
        val fanMean: List<Long>,
        val reachedHistogram: List<Int>
    )

    private fun simulate(
        netWorthFen: Long,
        annualExpenseFen: Long,
        annualIncomeFen: Long,
        fiNumberFen: Long,
        withdrawalRate: Double,
        realReturn: Double,
        volatility: Double,
        expenseGrowth: Double,
        incomeGrowth: Double,
        targetYears: Int?,
        simulations: Int,
        maxYears: Int,
        seed: Long,
        stress: StressProfile = StressProfile()
    ): SimulationResult {
        val years = maxYears
        val rnd = Random(seed)
        // 对数正态的漂移项：保证 E[1+r] = 1 + 实际收益率
        val drift = ln(1.0 + realReturn.coerceAtLeast(-0.99))
        val fanHorizon = min(years, FAN_HORIZON)

        val reached = IntArray(simulations) { years + 1 }
        val fan = Array(fanHorizon + 1) { DoubleArray(simulations) }
        // 均值与中位数必须来自同一组路径：一边解析解一边抽样，两条线的差会被模型误差污染
        val fanSum = DoubleArray(fanHorizon + 1)

        for (i in 0 until simulations) {
            var netWorth = netWorthFen.toDouble()
            fan[0][i] = netWorth
            fanSum[0] += netWorth
            if (netWorth >= fiNumberFen) reached[i] = 0

            for (year in 1..years) {
                val expense = annualExpenseFen *
                    (1.0 + expenseGrowth + stress.extraExpenseGrowth).pow(year)
                var income = annualIncomeFen * (1.0 + incomeGrowth).pow(year)
                // 失业冲击只作用在第一年：中年失业后通常要几个月才回到同等收入
                if (year == 1 && stress.lostIncomeMonths > 0) {
                    income *= 1.0 - (stress.lostIncomeMonths / 12.0).coerceIn(0.0, 1.0)
                }
                // 无论是否走压力情景都抽一次随机数，保证与基准路径用的是同一条收益序列
                val shock = Stats.nextGaussian(rnd)
                val growth = if (year <= stress.crashFirstYears) {
                    CRASH_RETURN
                } else {
                    exp(drift + volatility * shock) - 1.0
                }
                // 结余在年末投入：收入是逐月到手的，全年结余不可能在年初就吃满一整年收益。
                // 这也让蒙特卡洛与 deterministicYears 的递推 W_t = W_{t-1}(1+r) + S 严格同口径，
                // 否则「确定性年数」与概率结论会给出两套对不上的数字。
                netWorth = netWorth * (1.0 + growth) + income - expense
                if (netWorth < 0.0) netWorth = 0.0

                if (year <= fanHorizon) {
                    fan[year][i] = netWorth
                    fanSum[year] += netWorth
                }

                // 目标本身随支出一起长：达标线 = 当年支出 / 提取率
                if (reached[i] > years && netWorth >= expense / withdrawalRate) {
                    reached[i] = year
                }
            }
        }

        val sorted = LongArray(simulations) { reached[it].toLong() }.also { it.sort() }
        val fanP10 = ArrayList<Long>(fanHorizon + 1)
        val fanP50 = ArrayList<Long>(fanHorizon + 1)
        val fanP90 = ArrayList<Long>(fanHorizon + 1)
        for (year in 0..fanHorizon) {
            val sortedYear = fan[year].clone().also { it.sort() }
            fanP10.add(Stats.quantile(sortedYear, 0.1).toLong())
            fanP50.add(Stats.quantile(sortedYear, 0.5).toLong())
            fanP90.add(Stats.quantile(sortedYear, 0.9).toLong())
        }

        return SimulationResult(
            medianYears = yearOrNull(Stats.quantile(sorted, 0.5), years),
            p10Years = yearOrNull(Stats.quantile(sorted, 0.1), years),
            p90Years = yearOrNull(Stats.quantile(sorted, 0.9), years),
            successProbability = targetYears?.let { limit ->
                sorted.count { it <= limit }.toDouble() / simulations
            },
            neverReaches = sorted.count { it > years }.toDouble() / simulations,
            fanYears = (0..fanHorizon).toList(),
            fanP10 = fanP10,
            fanP50 = fanP50,
            fanP90 = fanP90,
            fanMean = (0..fanHorizon).map { year ->
                (fanSum[year] / simulations).roundToLong()
            },
            // index = 达标年（0..years）；超出窗口的路径不落桶，由 neverReaches 单独表达
            reachedHistogram = IntArray(years + 1)
                .also { buckets -> reached.forEach { if (it <= years) buckets[it]++ } }
                .toList()
        )
    }

    /** 超过模拟窗口的"达标年"是删失值，不给数字（否则会把 P90 说成一个假的具体年份） */
    private fun yearOrNull(year: Long, maxYears: Int): Int? =
        if (year > maxYears) null else year.toInt()

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) return 0L
        val sorted = values.sorted()
        return sorted[(sorted.size - 1) / 2]
    }

    private fun emptyPlan(
        inputs: WealthInputs,
        sampleMonths: Int,
        annualIncomeFen: Long,
        withdrawalRate: Double,
        realReturn: Double,
        volatility: Double
    ): WealthPlan {
        val netWorth = max(0L, inputs.netWorthFen)
        return WealthPlan(
            fiNumberFen = 0L,
            coastFiNumberFen = null,
            netWorthFen = netWorth,
            progress = 0.0,
            gapFen = 0L,
            annualExpenseFen = 0L,
            monthlyExpenseFen = 0L,
            annualIncomeFen = annualIncomeFen,
            annualSavingFen = annualIncomeFen,
            savingsRate = if (annualIncomeFen > 0L) 1.0 else null,
            freedomMonths = null,
            deterministicYears = null,
            medianYears = null,
            p10Years = null,
            p90Years = null,
            targetYears = inputs.yearsToTarget,
            successProbability = null,
            neverReaches = 0.0,
            fanYears = listOf(0),
            fanP10 = listOf(netWorth),
            fanP50 = listOf(netWorth),
            fanP90 = listOf(netWorth),
            fanMean = listOf(netWorth),
            // 没有支出就没有 FI 目标，解剖与直方图都没有可说的东西：给空列表，UI 直接跳过
            anatomy = GrowthAnatomy(
                contributed = emptyList(),
                compounded = emptyList(),
                flywheelYear = null,
                criticalAssetFen = 0L,
                compoundShare = null
            ),
            reachedHistogram = emptyList(),
            sampleMonths = sampleMonths,
            hasEnoughData = false,
            withdrawalRate = withdrawalRate,
            realReturn = realReturn,
            returnVolatility = volatility,
            expenseGrowth = inputs.expenseGrowth,
            incomeGrowth = inputs.incomeGrowth,
            simulations = inputs.simulations,
            maxYears = inputs.maxYears,
            seed = inputs.seed
        )
    }

}
