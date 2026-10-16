package com.jizhang.app.data.repository

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 统计粒度：日 / 月 / 年。
 * <p>
 * 与预算的 [BudgetCycle] 是两套东西：预算关心「额度按什么周期设置」，统计关心「看哪段时间的数据」，
 * 两者都有年 / 月，但统计没有「周」，也不需要和服务端的线格式对齐。
 */
enum class ReportGranularity(val label: String) {
    DAY("日"),
    MONTH("月"),
    YEAR("年");

    /** 上一个更粗的粒度，用于「回到今天 / 本月 / 今年」文案 */
    val nowLabel: String
        get() = when (this) {
            DAY -> "今天"
            MONTH -> "本月"
            YEAR -> "今年"
        }
}

/**
 * 统计页的时间窗 = 粒度 + 锚点。
 *
 * 锚点只要落在某个自然日 / 自然月 / 自然年内，就代表看那个周期：
 * - 日：锚点当天
 * - 月：锚点所在整月
 * - 年：锚点所在整年
 *
 * 区间统一是**左闭右开**的 epoch 毫秒（`[startMillis, endMillis)`），与本地 Room 查询口径一致。
 */
data class ReportPeriod(
    val granularity: ReportGranularity,
    val anchor: LocalDate
) {

    /** 周期首日（含） */
    val startDate: LocalDate = startOf(granularity, anchor)

    /** 周期结束日（不含） */
    val endDate: LocalDate = when (granularity) {
        ReportGranularity.DAY -> startDate.plusDays(1)
        ReportGranularity.MONTH -> startDate.plusMonths(1)
        ReportGranularity.YEAR -> startDate.plusYears(1)
    }

    val startMillis: Long = startDate.atStartOfDay(ZONE).toInstant().toEpochMilli()

    val endMillis: Long = endDate.atStartOfDay(ZONE).toInstant().toEpochMilli()

    /** 周期总天数 */
    val daysTotal: Int = when (granularity) {
        ReportGranularity.DAY -> 1
        ReportGranularity.MONTH -> startDate.lengthOfMonth()
        ReportGranularity.YEAR -> startDate.lengthOfYear()
    }

    /**
     * 是否就是当前（今天 / 本月 / 今年）。
     * <p>
     * 这里**必须**用 [startOf] 直接算，不能写成 `current().startDate`：属性初始化发生在构造期，
     * 而 `current()` 又会构造一个新的 ReportPeriod，于是它又要初始化自己的 isCurrent……
     * 一发不可收拾的无限递归（启动即 StackOverflowError）。
     */
    val isCurrent: Boolean = startDate == startOf(granularity, LocalDate.now())

    /**
     * 还能不能往后翻：不允许翻到未来。
     * <p>
     * 用 getter 而不是初始化器：`plus(1)` 会构造新的 ReportPeriod，如果这里在构造期执行，
     * 新实例又会给自己算 canGoNext、再造一个…… 递归下去直接栈溢出。
     * 惰性求值就只算当前这一层，代价是两次 LocalDate 比较。
     */
    val canGoNext: Boolean
        get() = !plus(1).startDate.isAfter(LocalDate.now())

    /**
     * 参与「日均」计算的天数：当期算到今天（含），已结束的周期算整段。
     * 用整段天数去除当期数据会把日均算小，失去参考价值 —— 所以必须区分。
     */
    val daysCounted: Int = when {
        isCurrent -> when (granularity) {
            ReportGranularity.DAY -> 1
            ReportGranularity.MONTH -> anchor.dayOfMonth
            ReportGranularity.YEAR -> anchor.dayOfYear
        }

        endDate.isAfter(LocalDate.now()) -> 1
        else -> daysTotal
    }

    /** 完整展示：2026 年 10 月 16 日 / 2026 年 10 月 / 2026 年 */
    val label: String = when (granularity) {
        ReportGranularity.DAY -> anchor.format(DAY_LABEL)
        ReportGranularity.MONTH -> anchor.format(MONTH_LABEL)
        ReportGranularity.YEAR -> anchor.format(YEAR_LABEL)
    }

    /** 紧凑展示：10 月 16 日（周五） / 10 月 / 2026 */
    val shortLabel: String = when (granularity) {
        ReportGranularity.DAY -> anchor.format(SHORT_DAY_LABEL) + "（" + weekdayText(anchor) + "）"
        ReportGranularity.MONTH -> anchor.format(SHORT_MONTH_LABEL)
        ReportGranularity.YEAR -> anchor.year.toString()
    }

    /*
     * 下面两个窗口同样用 getter：它们都要构造新的 ReportPeriod，
     * 放在属性初始化器里会形成「构造 → 再构造」的无限递归（真机上表现为一打开统计页就闪退）。
     */

    /**
     * 趋势图第 [index] 根柱子对应的时间窗（左闭右开，epoch 毫秒）。
     * <p>
     * 日粒度 = 第 index 个小时、月粒度 = 第 index 天、年粒度 = 第 index 个月 ——
     * 与 `ReportRepository.trend` 的横轴口径一一对应，点柱子看明细 / 跳账单都靠它换算。
     */
    fun bucketMillis(index: Int): Pair<Long, Long> {
        val start = when (granularity) {
            // 日粒度：第 index 个小时（0 时 = 当天 0 点）
            ReportGranularity.DAY -> startDate.atStartOfDay(ZONE).plusHours(index.toLong())
            ReportGranularity.MONTH -> startDate.plusDays(index.toLong()).atStartOfDay(ZONE)
            ReportGranularity.YEAR -> startDate.plusMonths(index.toLong()).atStartOfDay(ZONE)
        }
        val end = when (granularity) {
            ReportGranularity.DAY -> start.plusHours(1)
            ReportGranularity.MONTH -> start.plusDays(1)
            ReportGranularity.YEAR -> start.plusMonths(1)
        }
        return start.toInstant().toEpochMilli() to end.toInstant().toEpochMilli()
    }

    /** 趋势图第 [index] 根柱子的可读标题（比横轴标签多出日期 / 年份上下文） */
    fun bucketLabel(index: Int): String = when (granularity) {
        ReportGranularity.DAY ->
            anchor.format(SHORT_DAY_LABEL) + " " + "%02d:00 - %02d:00".format(index, (index + 1) % 24)

        ReportGranularity.MONTH -> {
            val date = startDate.plusDays(index.toLong())
            date.format(SHORT_DAY_LABEL) + "（" + weekdayText(date) + "）"
        }

        ReportGranularity.YEAR -> "${startDate.year} 年 ${index + 1} 月"
    }

    /** 环比窗口：前一天 / 上个月 / 上一年 */
    val previousPeriod: ReportPeriod
        get() = plus(-1)

    /** 同比窗口：去年同期（年粒度的同比就是上一年，与环比相同） */
    val lastYearPeriod: ReportPeriod
        get() = when (granularity) {
            ReportGranularity.DAY -> ReportPeriod(ReportGranularity.DAY, anchor.minusYears(1))
            ReportGranularity.MONTH -> ReportPeriod(ReportGranularity.MONTH, anchor.minusYears(1))
            ReportGranularity.YEAR -> previousPeriod
        }

    fun plus(offset: Long): ReportPeriod {
        val nextAnchor = when (granularity) {
            ReportGranularity.DAY -> anchor.plusDays(offset)
            ReportGranularity.MONTH -> anchor.plusMonths(offset)
            ReportGranularity.YEAR -> anchor.plusYears(offset)
        }
        return ReportPeriod(granularity, nextAnchor)
    }

    fun previous(): ReportPeriod = plus(-1)

    fun next(): ReportPeriod = plus(1)

    /** 换粒度但保留锚点（切到「月」就是看锚点所在的月） */
    fun withGranularity(target: ReportGranularity): ReportPeriod = ReportPeriod(target, anchor)

    /** 是否已经翻到未来（用于把「下一期」按钮置灰后的兜底判断） */
    val future: Boolean get() = startDate.isAfter(startOf(granularity, LocalDate.now()))

    companion object {
        private val ZONE: ZoneId = ZoneId.systemDefault()

        /** 某个粒度下锚点所属周期的首日：日=当天，月=当月 1 号，年=1 月 1 日 */
        fun startOf(granularity: ReportGranularity, date: LocalDate): LocalDate = when (granularity) {
            ReportGranularity.DAY -> date
            ReportGranularity.MONTH -> YearMonth.from(date).atDay(1)
            ReportGranularity.YEAR -> LocalDate.of(date.year, 1, 1)
        }

        private val DAY_LABEL = DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")
        private val MONTH_LABEL = DateTimeFormatter.ofPattern("yyyy 年 M 月")
        private val YEAR_LABEL = DateTimeFormatter.ofPattern("yyyy 年")
        private val SHORT_DAY_LABEL = DateTimeFormatter.ofPattern("M 月 d 日")
        private val SHORT_MONTH_LABEL = DateTimeFormatter.ofPattern("M 月")

        private val WEEKDAY_TEXT = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

        fun weekdayText(date: LocalDate): String = WEEKDAY_TEXT[date.dayOfWeek.value - 1]

        fun current(granularity: ReportGranularity = ReportGranularity.MONTH): ReportPeriod =
            ReportPeriod(granularity, LocalDate.now())
    }
}
