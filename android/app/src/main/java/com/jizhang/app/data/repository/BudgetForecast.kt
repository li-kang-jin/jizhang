package com.jizhang.app.data.repository

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt
import kotlin.random.Random

/** 单日支出样本（没有记账的空白日补 0，否则「有多少天在花钱」会被高估） */
data class DailySpend(val date: LocalDate, val expense: Long)

/** 预测曲线上的一笔固定支出，画阶梯用 */
data class ForecastFixedStep(
    val date: LocalDate,
    val amount: Long,
    /** 同一天多笔时用「、」拼起来，仅用于提示 */
    val names: String
)

/**
 * 预算趋势预测结果。
 * <p>
 * 曲线数组下标都是「周期内第几天」（0 = 起点，[daysTotal] = 周期末）：
 * - [actual] 只有 0..[elapsedDays] 有值；
 * - [median] / [low] / [high] 的 0..[elapsedDays] 等于实际值，之后才是预测；
 * - [baseline] 是「扣掉固定支出后匀速花，再叠加固定支出阶梯」的参考线。
 */
data class BudgetForecast(
    val period: String,
    val daysTotal: Int,
    val elapsedDays: Int,
    val daysLeft: Int,
    val totalBudget: Long,
    val spentSoFar: Long,
    /** 本期还没到期的固定支出合计 */
    val fixedTotal: Long,
    val actual: List<Long?>,
    val baseline: List<Long>,
    val median: List<Long?>,
    val low: List<Long?>,
    val high: List<Long?>,
    val fixedSteps: List<ForecastFixedStep>,
    /** 目标置信水平（1 - α），默认 0.85 */
    val confidence: Double,
    /** 期末超支概率 0..1 */
    val overrunProbability: Double,
    /** 期末支出的中位数 / 悲观分位（P90） */
    val finalMedian: Long,
    val finalP90: Long,
    /** 按目标置信水平反解的每日建议额度（分/天） */
    val suggestedDaily: Long,
    /** 当前日均（指数加权） */
    val averageDaily: Long,
    /** 有支出的天数、参与估计的总天数 */
    val positiveDays: Int,
    val sampleDays: Int,
    /** 样本不足，退回「带漂移的布朗运动」闭式解 */
    val degraded: Boolean,
    /** 参数估计：某天有支出的概率 λ（零膨胀部分） */
    val lambda: Double = 0.0,
    /** 参数估计：日支出标准差（分/天），VaR 与布朗近似都用它 */
    val dailySd: Long = 0L,
    /** VaR 用的正态分位 z(1−α)，85% 把握时约 1.04 */
    val zValue: Double = 0.0,
    /** 蒙特卡洛路径数；退化到闭式解时为 0 */
    val simulations: Int = 0
) {
    /** 超支概率分档文案 */
    val riskLabel: String
        get() = when {
            overrunProbability >= 0.6 -> "高"
            overrunProbability >= 0.3 -> "偏高"
            overrunProbability >= 0.1 -> "中"
            else -> "低"
        }

    /** 剩余可自由支配额度（已扣掉未到期的固定支出） */
    val freeBudget: Long get() = totalBudget - spentSoFar - fixedTotal

    /** 目标置信度，用于文案：85% 把握 */
    val confidencePercent: Int get() = (confidence * 100).roundToInt()
}

/**
 * 预算趋势 / 超支概率预测。
 *
 * ## 模型
 *
 * 日支出既不是每天都有，金额又右偏，所以用**零膨胀伽马**（复合泊松–伽马的日级离散版本）：
 * 某天发生支出的概率 λ，金额 X ~ Gamma(k, θ)，于是
 * `E[S] = λ·kθ`，`Var[S] = λ·kθ² + λ(1-λ)(kθ)²`。
 * 参数用矩估计（k = μ²/σ²，θ = σ²/μ），样本做 5%/95% 缩尾，避免手动记的房租把日常均值拉飞。
 *
 * 超支概率 = 蒙特卡洛里「期末总支出 > 总预算」的路径占比；
 * 固定支出按到期日作为**确定性现金流**叠加，而不是简单从额度里扣掉。
 *
 * 每日建议额度用 VaR 约束反解（把总预算当风险限额）：
 * ```
 * a = A/D − z(1−α)·σ/√D
 * ```
 * A 是剩余可自由支配额度、D 是剩余天数、σ 是日支出标准差。安全边际 ∝ σ/√D，
 * 正是随机游走位移标度律（Einstein：⟨x²⟩ = 2Dt）的直接结果 —— 越接近期末，腾挪空间越小。
 *
 * 样本不足（有支出天数 < [MIN_POSITIVE_DAYS]）时退回**带漂移的算术布朗运动**闭式解：
 * `P(超支) = 1 − Φ((A − μD)/(σ√D))`，曲线用 μ 线性外推 + 正态分位带。
 */
object BudgetForecaster {

    /** 蒙特卡洛路径数。2000 条时概率的标准误约 0.008，够用且移动端几毫秒跑完 */
    private const val SIMULATIONS = 2000

    /** 指数加权的半衰期：10 天前的一笔只算一半权重 */
    private const val HALF_LIFE_DAYS = 10.0

    /** 缩尾比例：掐掉两端各 5%，避免极端大额污染日常均值 */
    private const val WINSOR = 0.05

    /** 有支出的天数少于此值时改用布朗闭式解 */
    private const val MIN_POSITIVE_DAYS = 14

    /** 80% 分位带用到的正态分位（P10 / P90） */
    private const val Z80 = 1.2816

    /** 默认目标置信水平：85% 把握不超支 */
    const val DEFAULT_CONFIDENCE = 0.85

    fun forecast(
        period: String,
        totalBudget: Long,
        spentSoFar: Long,
        fixed: FixedReservation,
        dailySpends: List<DailySpend>,
        today: LocalDate = LocalDate.now(),
        confidence: Double = DEFAULT_CONFIDENCE,
        seed: Long = 20_261_017L
    ): BudgetForecast {
        val daysTotal = BudgetPeriods.daysTotal(period)
        val daysLeft = BudgetPeriods.daysLeft(period, today)
        val elapsedDays = elapsedDays(period, today, daysTotal)
        val periodStart = BudgetPeriods.startDate(period)
        val periodEnd = BudgetPeriods.endDate(period)

        val fixedTotal = fixed.total
        val fixedSteps = fixed.items
            .groupBy { it.nextDate }
            .map { (date, list) ->
                ForecastFixedStep(
                    date = date,
                    amount = list.sumOf { it.amount },
                    names = list.joinToString("、") { it.expense.name }
                )
            }
            .filter { it.date.isBefore(periodEnd) }
            .sortedBy { it.date }

        // 实际累计：只有周期已开始才有意义
        val actual = MutableList<Long?>(daysTotal + 1) { null }
        actual[0] = 0L
        if (elapsedDays > 0) {
            val byDate = dailySpends
                .filter { !it.date.isBefore(periodStart) && !it.date.isAfter(today) }
                .associate { it.date to it.expense }
            var sum = 0L
            for (i in 1..elapsedDays) {
                sum += byDate[periodStart.plusDays((i - 1).toLong())] ?: 0L
                actual[i] = sum
            }
            // 卡片上的「已花」来自 overview，口径一致；用它对最后一点对齐，避免误差累积
            if (actual[elapsedDays] != spentSoFar && elapsedDays > 0) {
                actual[elapsedDays] = spentSoFar
            }
        }

        // 基准线：扣掉固定支出后匀速花，再把固定支出在到期日阶跃上去
        val fixedByDate = fixedSteps.associate { it.date to it.amount }
        val flexibleBudget = (totalBudget - fixedTotal).coerceAtLeast(0L)
        val baseline = MutableList(daysTotal + 1) { 0L }
        var fixedCum = 0L
        for (i in 1..daysTotal) {
            fixedCum += fixedByDate[periodStart.plusDays((i - 1).toLong())] ?: 0L
            baseline[i] = flexibleBudget * i / daysTotal + fixedCum
        }
        baseline[0] = 0L

        val model = estimate(dailySpends, today)
        val futureDays = if (elapsedDays <= 0) daysLeft else max(daysLeft - 1, 0)
        val futureStart = if (elapsedDays <= 0) periodStart else today.plusDays(1)

        // 未来每天的固定支出（按相对未来起点的偏移存）
        val futureFixed = LongArray(futureDays + 2)
        for (step in fixedSteps) {
            val offset = ChronoUnit.DAYS.between(futureStart, step.date).toInt() + 1
            if (offset in 1..futureDays) futureFixed[offset] += step.amount
        }

        // 剩余可自由支配：总预算 − 已花 − 未到期固定支出
        val freeBudget = totalBudget - spentSoFar - fixedTotal

        val median = MutableList<Long?>(daysTotal + 1) { null }
        val low = MutableList<Long?>(daysTotal + 1) { null }
        val high = MutableList<Long?>(daysTotal + 1) { null }
        for (i in 0..elapsedDays) {
            median[i] = actual[i]
            low[i] = actual[i]
            high[i] = actual[i]
        }

        val degraded = model.positiveDays < MIN_POSITIVE_DAYS
        var overrun = 0.0
        var finalMedian = spentSoFar
        var finalP90 = spentSoFar

        if (futureDays <= 0) {
            overrun = if (spentSoFar > totalBudget) 1.0 else 0.0
            finalMedian = spentSoFar
            finalP90 = spentSoFar
        } else if (degraded) {
            // 布朗运动闭式解：未来累计 ~ N(μD, σ²D)
            val meanRest = model.meanDay * futureDays
            val sdRest = model.sdDay * sqrt(futureDays.toDouble())
            overrun = if (sdRest <= 1e-6) {
                if (freeBudget < 0) 1.0 else 0.0
            } else {
                (1.0 - normalCdf((freeBudget - meanRest) / sdRest)).coerceIn(0.0, 1.0)
            }
            var fixedSoFar = 0L
            for (k in 1..futureDays) {
                fixedSoFar += futureFixed[k]
                val idx = elapsedDays + k
                val center = spentSoFar + fixedSoFar + meanRest * k / futureDays
                val band = Z80 * model.sdDay * sqrt(k.toDouble())
                median[idx] = center.roundToLong()
                low[idx] = (center - band).roundToLong().coerceAtLeast(0L)
                high[idx] = (center + band).roundToLong()
            }
            finalMedian = (spentSoFar + fixedTotal + meanRest).roundToLong()
            finalP90 = (spentSoFar + fixedTotal + meanRest + Z80 * sdRest).roundToLong()
        } else {
            val rnd = Random(seed)
            val values = LongArray(SIMULATIONS) { spentSoFar }
            val stride = max(1, futureDays / 40)
            val deterministicDaily = model.meanDay.roundToLong()
            for (k in 1..futureDays) {
                val fixedToday = futureFixed[k]
                for (j in values.indices) {
                    val spend = when {
                        model.deterministic -> deterministicDaily
                        rnd.nextDouble() < model.lambda ->
                            nextGamma(rnd, model.shape, model.scale).roundToLong().coerceAtLeast(0L)

                        else -> 0L
                    }
                    values[j] += fixedToday + spend
                }
                if (k % stride == 0 || k == futureDays) {
                    val sorted = values.copyOf()
                    sorted.sort()
                    median[elapsedDays + k] = quantile(sorted, 0.5)
                    low[elapsedDays + k] = quantile(sorted, 0.1)
                    high[elapsedDays + k] = quantile(sorted, 0.9)
                }
            }
            val sorted = values.copyOf()
            sorted.sort()
            finalMedian = quantile(sorted, 0.5)
            finalP90 = quantile(sorted, 0.9)
            overrun = values.count { it > totalBudget }.toDouble() / SIMULATIONS
        }

        /*
         * 每日建议额度：a = A/D − z(1−α)·σ/√D。
         * D 用「剩余天数（含今天）」，A 已扣掉固定支出 —— 未来固定支出是确定性的，
         * 按 a 花满 D 天后正好把 A 用完，超支只可能来自波动。
         */
        val d = max(daysLeft, 1).toDouble()
        val z = normalQuantile(confidence)
        val suggested = (freeBudget / d - z * model.sdDay / sqrt(d))
            .roundToLong()
            .coerceAtLeast(0L)

        return BudgetForecast(
            period = period,
            daysTotal = daysTotal,
            elapsedDays = elapsedDays,
            daysLeft = daysLeft,
            totalBudget = totalBudget,
            spentSoFar = spentSoFar,
            fixedTotal = fixedTotal,
            actual = actual,
            baseline = baseline,
            median = median,
            low = low,
            high = high,
            fixedSteps = fixedSteps,
            confidence = confidence,
            overrunProbability = overrun,
            finalMedian = finalMedian,
            finalP90 = finalP90,
            suggestedDaily = suggested,
            averageDaily = model.meanDay.roundToLong(),
            positiveDays = model.positiveDays,
            sampleDays = model.sampleDays,
            degraded = degraded,
            lambda = model.lambda,
            dailySd = model.sdDay.roundToLong(),
            zValue = z,
            simulations = if (degraded || futureDays <= 0) 0 else SIMULATIONS
        )
    }

    /* ---------------- 参数估计 ---------------- */

    private data class SpendModel(
        /** 某天发生支出的概率 */
        val lambda: Double,
        val shape: Double,
        val scale: Double,
        /** 含空白日的日均 / 日标准差 */
        val meanDay: Double,
        val sdDay: Double,
        val positiveDays: Int,
        val sampleDays: Int,
        /** 样本里没有任何波动（每天都一样，或天天为 0）：模拟不该凭空造出随机性 */
        val deterministic: Boolean
    )

    /**
     * 矩估计。
     * <p>
     * 「有支出天数的比例」与日均权重按「距今多少天」指数衰减（半衰期 10 天）：
     * 最近的花钱习惯更能代表接下来的行为。金额做 5%/95% 缩尾后拟合 Gamma
     * （`shape = μ²/σ²`、`scale = σ²/μ`，这里用缩尾后的简单矩估计，避免个别大额主导形状）。
     */
    private fun estimate(daily: List<DailySpend>, today: LocalDate): SpendModel {
        if (daily.isEmpty()) {
            return SpendModel(0.0, 1.0, 0.0, 0.0, 0.0, 0, 0, deterministic = true)
        }

        val weights = daily.map { item ->
            val age = ChronoUnit.DAYS.between(item.date, today).coerceAtLeast(0L).toDouble()
            0.5.pow(age / HALF_LIFE_DAYS)
        }
        val sumW = weights.sum()
        if (sumW <= 0.0) {
            return SpendModel(0.0, 1.0, 0.0, 0.0, 0.0, 0, daily.size, deterministic = true)
        }

        val meanDay = daily.indices.sumOf { weights[it] * daily[it].expense } / sumW
        val varDay = daily.indices.sumOf { weights[it] * (daily[it].expense - meanDay).pow(2) } / sumW

        val weightPositive = daily.indices.filter { daily[it].expense > 0 }
            .sumOf { weights[it] }
        val lambda = (weightPositive / sumW).coerceIn(0.0, 1.0)
        val positives = daily.filter { it.expense > 0 }.map { it.expense.toDouble() }
        val positiveDays = positives.size
        if (positiveDays == 0) {
            return SpendModel(0.0, 1.0, 0.0, meanDay, sqrt(varDay), 0, daily.size, deterministic = true)
        }

        // 缩尾：掐掉两端 5%，避免手动记的房租这类大额把日常均值拉飞
        val sorted = positives.sorted()
        val loIdx = (sorted.size * WINSOR).toInt().coerceIn(0, sorted.lastIndex)
        val hiIdx = ((sorted.size - 1) * (1 - WINSOR)).toInt().coerceIn(0, sorted.lastIndex)
        val lo = sorted[loIdx]
        val hi = sorted[max(hiIdx, loIdx)]
        val trimmed = positives.map { it.coerceIn(lo, hi) }

        val meanPos = trimmed.average()
        val varPos = trimmed.sumOf { (it - meanPos).pow(2) } / trimmed.size

        // shape 限制在 [0.5, 50]：太小会模拟出离谱的巨额单笔；样本方差为 0 时取上限，
        // 让支出近似确定，而不是凭空造出 14% 的波动
        val shape = if (varPos > 1e-6 && meanPos > 0) {
            (meanPos * meanPos / varPos).coerceIn(0.5, 50.0)
        } else {
            50.0
        }
        val scale = if (meanPos > 0) meanPos / shape else 0.0

        // 「每天都有支出且金额完全一样」这类样本：日间没有波动，模拟要退化成确定性外推，
        // 否则 Gamma 的 shape 兜底会凭空造出十几%的日波动，把概率算高
        val deterministic = varPos <= 1e-6 && (lambda >= 0.999 || lambda <= 0.001)

        /*
         * 日均 / 日标准差用**拟合后的模型**反推，而不是原始样本：
         * 零膨胀伽马的矩是 E[S] = λ·μ₊、Var[S] = λ·σ₊² + λ(1−λ)·μ₊²。
         * 这样 VaR 用的 σ 和蒙特卡洛抽的是同一份分布；
         * 否则原始样本里某天记的一笔大额会把 σ 顶得极高，建议额度被算成 0，
         * 而模拟那边又用了缩尾后的金额，两边自相矛盾。
         */
        val fittedMean = lambda * meanPos
        val fittedVar = lambda * varPos + lambda * (1 - lambda) * meanPos * meanPos

        return SpendModel(
            lambda = lambda,
            shape = shape,
            scale = scale,
            meanDay = fittedMean,
            sdDay = sqrt(fittedVar.coerceAtLeast(0.0)),
            positiveDays = positiveDays,
            sampleDays = daily.size,
            deterministic = deterministic
        )
    }

    /* ---------------- 分布工具 ---------------- */

    /** 排序后数组的分位数（最近邻，避免插值引入假的精度） */
    private fun quantile(sorted: LongArray, q: Double): Long {
        if (sorted.isEmpty()) return 0L
        val idx = ((sorted.size - 1) * q).roundToLong().toInt().coerceIn(0, sorted.lastIndex)
        return sorted[idx]
    }

    /** Marsaglia–Tsang 法抽 Gamma（shape 允许非整数） */
    private fun nextGamma(rnd: Random, shape: Double, scale: Double): Double {
        if (shape <= 0.0 || scale <= 0.0) return 0.0
        if (shape < 1.0) {
            val u = rnd.nextDouble().coerceAtLeast(1e-12)
            return nextGamma(rnd, shape + 1.0, scale) * u.pow(1.0 / shape)
        }
        val d = shape - 1.0 / 3.0
        val c = 1.0 / sqrt(9.0 * d)
        while (true) {
            val x = rnd.nextGaussian()
            val v = (1.0 + c * x).let { it * it * it }
            if (v <= 0.0) continue
            val u = rnd.nextDouble()
            if (u < 1.0 - 0.0331 * x * x * x * x) return d * v * scale
            if (ln(u.coerceAtLeast(1e-12)) < 0.5 * x * x + d * (1.0 - v + ln(v))) {
                return d * v * scale
            }
        }
    }

    /** Box–Muller 标准正态 */
    private fun Random.nextGaussian(): Double {
        val u1 = nextDouble().coerceAtLeast(1e-12)
        val u2 = nextDouble()
        return sqrt(-2.0 * ln(u1)) * cos(2.0 * PI * u2)
    }

    /** 标准正态 CDF（Abramowitz & Stegun 26.2.17，误差 < 1e-7） */
    private fun normalCdf(z: Double): Double {
        val t = 1.0 / (1.0 + 0.2316419 * abs(z))
        val d = 0.3989422804 * exp(-z * z / 2.0)
        val p = d * t * (0.3193815 + t * (-0.3565638 + t * (1.781478 + t * (-1.821256 + t * 1.330274))))
        return if (z > 0) 1.0 - p else p
    }

    /** 正态分位：直接二分反解 CDF，避免再抄一张系数表 */
    private fun normalQuantile(p: Double): Double {
        if (p <= 0.5) return 0.0
        var lo = 0.0
        var hi = 8.0
        repeat(64) {
            val mid = (lo + hi) / 2.0
            if (normalCdf(mid) < p) lo = mid else hi = mid
        }
        return (lo + hi) / 2.0
    }

    /** 周期内已过天数（含今天）；与 BudgetRepository 里的同名逻辑保持一致 */
    private fun elapsedDays(period: String, today: LocalDate, daysTotal: Int): Int {
        val start = BudgetPeriods.startDate(period)
        val end = BudgetPeriods.endDate(period)
        return when {
            today.isBefore(start) -> 0
            !today.isBefore(end) -> daysTotal
            else -> (today.toEpochDay() - start.toEpochDay()).toInt() + 1
        }
    }
}
