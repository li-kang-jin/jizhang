package com.jizhang.app.data.repository

import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.roundToLong
import kotlin.math.sqrt

/** 预算建议的可信度：样本月数 + 波动共同决定 */
enum class BudgetConfidence { HIGH, MEDIUM, LOW }

/**
 * 一个分类的历史支出（分）。
 *
 * @param monthlyFen 按月排列，**[0] 是目标周期的前一个月**，依次往前；没花钱的月份是 0 而不是 null，
 *                   「这个月没花」本身就是要参与计算的真实信息
 * @param samePeriodLastYearFen 去年同期的支出，用来做季节性校准；不足一年数据时为 null
 */
data class CategoryHistory(
    val categoryId: Long,
    val name: String,
    val monthlyFen: List<Long>,
    val samePeriodLastYearFen: Long? = null
)

/** 单条分类建议 */
data class CategoryAdvice(
    val categoryId: Long,
    val name: String,
    /** 建议额度（分），已取整到 10 元；因上限压缩过时是压缩后的值 */
    val amountFen: Long,
    /** 计算依据里那个「月均」（分），界面上展示给用户看 */
    val medianFen: Long,
    /** 趋势调整百分比，+8 表示比月均上调 8% */
    val trendPercent: Int,
    /** 真正花过钱的月数（一次性大额月份不计入） */
    val sampleMonths: Int,
    val confidence: BudgetConfidence,
    /** 一条能自证的依据，直接显示在界面上 */
    val reason: String
)

/** 一次完整的预算建议 */
data class BudgetAdvice(
    val items: List<CategoryAdvice>,
    /** 建议总预算（分）= 各分类之和 */
    val totalFen: Long,
    /** 基准收入（分）：近几个月的稳健值，不是「本月收入」 */
    val incomeFen: Long?,
    /** 支出上限（分）= 基准收入 × (1 − 目标储蓄率) */
    val ceilingFen: Long?,
    /** 是否因为超过上限被等比压缩过 */
    val compressed: Boolean,
    /** 按建议执行后的预计储蓄率（%）；没有收入数据时为 null */
    val savingsRatePercent: Int?,
    /** 参与计算的历史月数（窗口长度，不是有支出的月数） */
    val historyMonths: Int,
    val confidence: BudgetConfidence,
    /** 卡片底部的整段说明：数据不足、被压缩、无收入等 */
    val note: String?
)

/**
 * 本地预算建议引擎（纯 Kotlin，无 IO、无网络，可单测）。
 *
 * ### 为什么不能只用「过去 N 个月的平均值」
 * 1. **均值会被一次性支出带偏**：一笔 6000 的年费能让「保险」的月均变成 1000。
 * 2. **只看历史会把月光当成标准**：历史花多少就批多少，等于用过去的失误给未来授权。
 * 3. **波动大的分类不该给死数**：旅游这种一年两次的支出，正确做法是摊销，不是按月均值。
 *
 * ### 算法（三步）
 * ```
 * 分类基准  B = 非零月加权中位数 × (非零月数 / 窗口月数) + 一次性支出 / 12
 * 趋势修正  T = clamp((B + TheilSen(序列)) / B, 0.8, 1.2)
 * 季节修正  S = clamp(去年同月 / 窗口均值, 0.85, 1.15)     // 仅当有去年同月数据
 * 分类建议  F = clamp(B × T × S, 下限 = 该分类本期固定支出)
 * 支出上限  A = 收入基准 × (1 − 目标储蓄率)
 * 合成      ΣF > A 时只压缩「弹性」部分（固定支出不动），并保证 ΣF ≤ A
 * ```
 * 之所以用加权中位数（近月权重 0.75^n）而不是普通中位数：越近的月份越能代表当下的生活方式，
 * 但权重衰减得快又会让单月异常值权重过高，0.75 是两者之间的折中。
 */
object BudgetAdvisor {

    /** 参与计算的历史月数 */
    const val HISTORY_MONTHS = 6

    /** 默认目标储蓄率：20%（50/30/20 法则里的储蓄项） */
    const val DEFAULT_SAVINGS_RATE = 0.2

    /** 近月权重衰减系数 */
    private const val DECAY = 0.75

    /** 趋势修正的上下限：不许因为两三个月的斜率就把预算改掉三成 */
    private const val TREND_MIN = 0.8
    private const val TREND_MAX = 1.2

    /** 季节修正的上下限 */
    private const val SEASON_MIN = 0.85
    private const val SEASON_MAX = 1.15

    /** 单月金额超过「非零月中位数」这个倍数，就视为一次性支出 */
    private const val SPIKE_MULTIPLE = 3.0

    /** 一次性支出的绝对门槛：几十块的波动不值得摊销 */
    private const val SPIKE_MIN_FEN = 20_000L

    /** 一年 12 个月，一次性支出按 1/12 摊销 */
    private const val ANNUAL_DIVISOR = 12.0

    /** 取整单位：10 元 */
    private const val ROUND_FEN = 1_000L

    private const val HIGH_CV = 0.35
    private const val MEDIUM_CV = 0.60
    private const val INCOME_CV = 0.40
    private const val MONTHS_FOR_HIGH = 6
    private const val MONTHS_FOR_MEDIUM = 3

    /**
     * 生成建议。
     *
     * @param categories 各分类的历史；由仓库层负责把子分类归并到父分类、剔除余额校准
     * @param monthlyIncomeFen 近几个月的收入（分），[0] 是最新一个月
     * @param recurringByCategoryFen 目标周期内的固定支出（按分类）：既是预算下限，也是压缩时不动的那部分
     * @param savingsRate 目标储蓄率 0..1
     */
    fun advise(
        categories: List<CategoryHistory>,
        monthlyIncomeFen: List<Long>,
        recurringByCategoryFen: Map<Long, Long> = emptyMap(),
        savingsRate: Double = DEFAULT_SAVINGS_RATE
    ): BudgetAdvice {
        val windowMonths = max(
            categories.maxOfOrNull { it.monthlyFen.size } ?: 0,
            monthlyIncomeFen.size
        )

        val items = categories
            .mapNotNull { category ->
                adviseCategory(category, recurringByCategoryFen[category.categoryId] ?: 0L)
            }
            .filter { it.amountFen > 0 }
            .sortedByDescending { it.amountFen }

        if (items.isEmpty()) {
            return BudgetAdvice(
                items = emptyList(),
                totalFen = 0L,
                incomeFen = null,
                ceilingFen = null,
                compressed = false,
                savingsRatePercent = null,
                historyMonths = windowMonths,
                confidence = BudgetConfidence.LOW,
                note = "还没有足够的支出记录，先记满一个月再来算"
            )
        }

        val income = baselineIncome(monthlyIncomeFen)
        val ceiling = income?.let { (it * (1 - savingsRate)).roundToLong().coerceAtLeast(0L) }

        var finalItems = items
        var compressed = false
        if (ceiling != null && items.sumOf { it.amountFen } > ceiling) {
            compressed = true
            finalItems = compress(items, ceiling, recurringByCategoryFen)
        }
        val total = finalItems.sumOf { it.amountFen }

        val totalSeries = (0 until windowMonths).map { index ->
            categories.sumOf { it.monthlyFen.getOrElse(index) { 0L } }
        }
        val activeMonths = totalSeries.count { it > 0L }
        val confidence = overallConfidence(totalSeries, activeMonths)

        val notes = buildList {
            if (activeMonths < MONTHS_FOR_MEDIUM) {
                add("只有 $activeMonths 个月有支出记录，建议先按实际微调")
            }
            if (income == null) {
                add("没有收入记录，只能按历史支出给参考值")
            }
            if (compressed) {
                add("为了守住 ${(savingsRate * 100).roundToInt()}% 的储蓄率，弹性支出已按比例下调")
            }
        }

        return BudgetAdvice(
            items = finalItems,
            totalFen = total,
            incomeFen = income,
            ceilingFen = ceiling,
            compressed = compressed,
            savingsRatePercent = income?.let {
                if (it <= 0L) null else ((it - total) * 100.0 / it).roundToInt()
            },
            historyMonths = windowMonths,
            confidence = confidence,
            note = notes.takeIf { it.isNotEmpty() }?.joinToString("；")
        )
    }

    /* ==================== 分类计算 ==================== */

    private fun adviseCategory(history: CategoryHistory, recurringFen: Long): CategoryAdvice? {
        val series = history.monthlyFen
        if (series.isEmpty() && recurringFen <= 0L) {
            return null
        }

        val medianNonZero = weightedMedian(series)
        val spikeThreshold = max(medianNonZero * SPIKE_MULTIPLE, SPIKE_MIN_FEN.toDouble())
        val spikes = if (medianNonZero > 0L) series.filter { it > spikeThreshold } else emptyList()

        // 波动中的「常规月份」：把一次性支出剔除后再看频率，否则年费会让频率虚高
        val regularMonths = series.count { it > 0L } - spikes.size
        val windowMonths = series.size
        val frequency = if (windowMonths <= 0) 1.0 else regularMonths.toDouble() / windowMonths
        val amortized = spikes.sumOf { it } / ANNUAL_DIVISOR

        var base = medianNonZero * frequency + amortized

        // 一次性支出月用中位数替换后再拟合斜率，避免一个尖峰把趋势拉飞
        val cleaned = series
            .map { if (medianNonZero > 0L && it > spikeThreshold) medianNonZero.toDouble() else it.toDouble() }
            .reversed() // Theil–Sen 需要从旧到新
        val slope = theilSenSlope(cleaned)
        val trendRatio = if (medianNonZero > 0L) {
            ((medianNonZero + slope) / medianNonZero).coerceIn(TREND_MIN, TREND_MAX)
        } else {
            1.0
        }
        val trendPercent = ((trendRatio - 1) * 100).roundToInt()

        val seasonalRatio = seasonalRatio(history, cleaned, base)
        base *= trendRatio * seasonalRatio

        // 固定支出是硬性下限：历史里没记过房租，不代表房租可以不进预算
        val floored = max(base, recurringFen.toDouble())
        val amount = roundToTen(floored)
        if (amount <= 0L) {
            return null
        }

        val sampleMonths = series.count { it > 0L }
        val cv = cvOf(series)
        val confidence = when {
            sampleMonths >= MONTHS_FOR_HIGH && cv <= HIGH_CV -> BudgetConfidence.HIGH
            sampleMonths >= MONTHS_FOR_MEDIUM && cv <= MEDIUM_CV -> BudgetConfidence.MEDIUM
            else -> BudgetConfidence.LOW
        }

        val reason = buildString {
            append("近 $windowMonths 个月有 $sampleMonths 个月花钱")
            append("，月均 ${yuan((medianNonZero * frequency).roundToLong())}")
            if (trendPercent != 0) {
                append(" · 趋势 ${if (trendPercent > 0) "+" else ""}$trendPercent%")
            }
            if (amortized > 0) {
                append(" · 含一次性支出摊销 ${yuan(amortized.roundToLong())}")
            }
            if (seasonalRatio != 1.0) {
                append(" · 已按去年同期校准")
            }
            if (recurringFen > 0L && base < recurringFen) {
                append(" · 不低于固定支出 ${yuan(recurringFen)}")
            }
        }

        return CategoryAdvice(
            categoryId = history.categoryId,
            name = history.name,
            amountFen = amount,
            medianFen = (medianNonZero * frequency).roundToLong(),
            trendPercent = trendPercent,
            sampleMonths = sampleMonths,
            confidence = confidence,
            reason = reason
        )
    }

    /**
     * 季节修正：去年同月的开销 / 窗口内的月均。
     * 只在有去年同期数据、且窗口月均大于 0 时启用；比率被夹在 0.85..1.15 之间。
     */
    private fun seasonalRatio(history: CategoryHistory, cleaned: List<Double>, base: Double): Double {
        val lastYear = history.samePeriodLastYearFen ?: return 1.0
        if (lastYear <= 0L || base <= 0.0) {
            return 1.0
        }
        val average = cleaned.average()
        if (average <= 0.0) {
            return 1.0
        }
        return (lastYear / average).coerceIn(SEASON_MIN, SEASON_MAX)
    }

    /* ==================== 上限与压缩 ==================== */

    /**
     * 基准收入：近 3 个有收入月份的加权中位数；收入波动大时改用 P25（把旺季当常态会高估承受力）。
     * 完全没记收入时返回 null —— 这时候不该编一个收入出来。
     */
    private fun baselineIncome(monthlyIncomeFen: List<Long>): Long? {
        val positive = monthlyIncomeFen.filter { it > 0L }
        if (positive.isEmpty()) {
            return null
        }
        val volatile = positive.size >= 4 && cvOf(positive) > INCOME_CV
        return if (volatile) percentile(positive, 0.25) else median(positive.take(3))
    }

    /**
     * 超过上限时压缩。
     *
     * 只压「弹性」部分：固定支出（房租、房贷）砍掉没有意义，砍了也不代表不用交；
     * 压不动就压到只剩固定支出，并在说明里讲清楚。
     */
    private fun compress(
        items: List<CategoryAdvice>,
        ceiling: Long,
        recurringByCategoryFen: Map<Long, Long>
    ): List<CategoryAdvice> {
        val fixed = items.associate { it.categoryId to minOf(it.amountFen, recurringByCategoryFen[it.categoryId] ?: 0L) }
        val fixedSum = fixed.values.sum()
        val flexibleSum = (items.sumOf { it.amountFen } - fixedSum).coerceAtLeast(0L)
        val room = (ceiling - fixedSum).coerceAtLeast(0L)
        val ratio = if (flexibleSum <= 0L) 0.0 else (room.toDouble() / flexibleSum).coerceAtMost(1.0)

        val compressed = items.map { item ->
            val fixedPart = fixed[item.categoryId] ?: 0L
            val flexiblePart = (item.amountFen - fixedPart) * ratio
            item.copy(amountFen = roundToTen(fixedPart + flexiblePart))
        }.filter { it.amountFen > 0L }

        return trimToCeiling(compressed, ceiling)
    }

    /** 取整可能让合计略超上限：从最大的一条里扣回来，保证「建议总预算 ≤ 上限」这个承诺成立 */
    private fun trimToCeiling(items: List<CategoryAdvice>, ceiling: Long): List<CategoryAdvice> {
        val mutable = items.toMutableList()
        var excess = mutable.sumOf { it.amountFen } - ceiling
        while (excess > 0L) {
            val index = mutable.indices.maxByOrNull { mutable[it].amountFen } ?: break
            val available = max(mutable[index].amountFen - ROUND_FEN, 0L)
            if (available <= 0L) {
                break
            }
            val step = minOf(((excess + ROUND_FEN - 1) / ROUND_FEN) * ROUND_FEN, available)
            mutable[index] = mutable[index].copy(amountFen = mutable[index].amountFen - step)
            excess -= step
        }
        return mutable.filter { it.amountFen > 0L }
    }

    /* ==================== 统计工具 ==================== */

    /** 近月加权的非零中位数：[0] 的权重最高，逐月衰减 0.75 */
    private fun weightedMedian(series: List<Long>): Long {
        val pairs = series.mapIndexedNotNull { index, value ->
            if (value <= 0L) null else value to Math.pow(DECAY, index.toDouble())
        }.sortedBy { it.first }
        if (pairs.isEmpty()) {
            return 0L
        }
        val totalWeight = pairs.sumOf { it.second }
        var accumulated = 0.0
        for ((value, weight) in pairs) {
            accumulated += weight
            if (accumulated >= totalWeight / 2) {
                return value
            }
        }
        return pairs.last().first
    }

    /** Theil–Sen 斜率：所有两点斜率的中位数，对离群点稳健 */
    private fun theilSenSlope(series: List<Double>): Double {
        if (series.size < 2) {
            return 0.0
        }
        val slopes = ArrayList<Double>()
        for (i in series.indices) {
            for (j in i + 1 until series.size) {
                slopes += (series[j] - series[i]) / (j - i)
            }
        }
        return medianDouble(slopes)
    }

    private fun median(values: List<Long>): Long {
        if (values.isEmpty()) {
            return 0L
        }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
    }

    private fun medianDouble(values: List<Double>): Double {
        if (values.isEmpty()) {
            return 0.0
        }
        val sorted = values.sorted()
        val middle = sorted.size / 2
        return if (sorted.size % 2 == 0) {
            (sorted[middle - 1] + sorted[middle]) / 2
        } else {
            sorted[middle]
        }
    }

    private fun percentile(values: List<Long>, fraction: Double): Long {
        if (values.isEmpty()) {
            return 0L
        }
        val sorted = values.sorted()
        val index = ((sorted.size - 1) * fraction).toInt().coerceIn(0, sorted.size - 1)
        return sorted[index]
    }

    /** 变异系数 σ/μ：衡量「这个分类花得稳不稳」 */
    private fun cvOf(values: List<Long>): Double {
        if (values.size < 2) {
            return 0.0
        }
        val mean = values.average()
        if (mean <= 0.0) {
            return 0.0
        }
        val variance = values.sumOf { (it - mean) * (it - mean) } / values.size
        return sqrt(variance) / mean
    }

    private fun overallConfidence(totalSeries: List<Long>, activeMonths: Int): BudgetConfidence {
        val cv = cvOf(totalSeries)
        return when {
            activeMonths >= MONTHS_FOR_HIGH && cv <= HIGH_CV -> BudgetConfidence.HIGH
            activeMonths >= MONTHS_FOR_MEDIUM && cv <= MEDIUM_CV -> BudgetConfidence.MEDIUM
            else -> BudgetConfidence.LOW
        }
    }

    /** 取整到 10 元，且正数至少 10 元（否则一条建议会显示成 0） */
    private fun roundToTen(fen: Double): Long {
        if (fen <= 0.0) {
            return 0L
        }
        return max((fen / ROUND_FEN).roundToLong() * ROUND_FEN, ROUND_FEN)
    }

    /** 分 → ¥1,180 / ¥1,180.50（整数金额不显示小数） */
    private fun yuan(fen: Long): String {
        val sign = if (fen < 0) "-" else ""
        val absolute = abs(fen)
        val text = if (absolute % 100 == 0L) {
            String.format(Locale.CHINA, "%,d", absolute / 100)
        } else {
            String.format(Locale.CHINA, "%,.2f", absolute / 100.0)
        }
        return "$sign¥$text"
    }
}
