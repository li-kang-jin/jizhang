package com.jizhang.app.data.repository

import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields

/** 预算周期类型 */
enum class BudgetCycle(val label: String) {
    MONTH("每月"),
    WEEK("每周"),
    YEAR("每年")
}

/**
 * 预算周期的解析与计算。
 *
 * `period` 是纯字符串，格式随周期变化，因此**不需要改动数据库表结构**：
 * - 每月：`2026-10`
 * - 每周：`2026-W42`（ISO 周，周一为一周之始）
 * - 每年：`2026`
 *
 * 读取时按格式反推周期（[cycleOf]），所以历史数据（`yyyy-MM`）天然兼容。
 */
object BudgetPeriods {

    private val MONTH_REGEX = Regex("""\d{4}-\d{2}""")
    private val WEEK_REGEX = Regex("""\d{4}-W\d{2}""")
    private val YEAR_REGEX = Regex("""\d{4}""")

    private val ISO: WeekFields = WeekFields.ISO

    /** 从 period 字符串推断周期类型 */
    fun cycleOf(period: String): BudgetCycle = when {
        WEEK_REGEX.matches(period) -> BudgetCycle.WEEK
        YEAR_REGEX.matches(period) -> BudgetCycle.YEAR
        else -> BudgetCycle.MONTH
    }

    /** 生成某个周期下的 period 字符串（默认当前时间） */
    fun of(cycle: BudgetCycle, date: LocalDate = LocalDate.now()): String = when (cycle) {
        BudgetCycle.MONTH -> "%04d-%02d".format(date.year, date.monthValue)
        BudgetCycle.WEEK -> "%04d-W%02d".format(
            date.get(ISO.weekBasedYear()),
            date.get(ISO.weekOfWeekBasedYear())
        )

        BudgetCycle.YEAR -> "%04d".format(date.year)
    }

    /** 周期起始日（含） */
    fun startDate(period: String): LocalDate = when (cycleOf(period)) {
        BudgetCycle.MONTH -> YearMonth.parse(period).atDay(1)
        BudgetCycle.WEEK -> {
            val year = period.substringBefore("-W").toInt()
            val week = period.substringAfter("-W").toInt()
            // 以 1 月 4 日所在周为锚点定位该周，再回到周一
            LocalDate.of(year, 1, 4)
                .with(ISO.weekOfWeekBasedYear(), week.toLong())
                .with(ISO.dayOfWeek(), 1L)
        }

        BudgetCycle.YEAR -> LocalDate.of(period.toInt(), 1, 1)
    }

    /** 周期结束日（不含） */
    fun endDate(period: String): LocalDate {
        val start = startDate(period)
        return when (cycleOf(period)) {
            BudgetCycle.MONTH -> start.plusMonths(1)
            BudgetCycle.WEEK -> start.plusWeeks(1)
            BudgetCycle.YEAR -> start.plusYears(1)
        }
    }

    fun startMillis(period: String): Long = toMillis(startDate(period))

    /** 用于区间查询的右开边界 */
    fun endMillis(period: String): Long = toMillis(endDate(period))

    /** 偏移若干个周期，offset 为 -1 即上一个周期 */
    fun plus(period: String, offset: Long): String {
        val start = startDate(period)
        return when (cycleOf(period)) {
            BudgetCycle.MONTH -> of(BudgetCycle.MONTH, start.plusMonths(offset))
            BudgetCycle.WEEK -> of(BudgetCycle.WEEK, start.plusWeeks(offset))
            BudgetCycle.YEAR -> of(BudgetCycle.YEAR, start.plusYears(offset))
        }
    }

    fun previous(period: String): String = plus(period, -1)

    fun next(period: String): String = plus(period, 1)

    /** 周期总天数，用于「日均可用」建议值 */
    fun daysTotal(period: String): Int = when (cycleOf(period)) {
        BudgetCycle.MONTH -> startDate(period).lengthOfMonth()
        BudgetCycle.WEEK -> 7
        BudgetCycle.YEAR -> startDate(period).lengthOfYear()
    }

    /** 剩余天数（含今天）；已结束的周期为 0，未开始的周期为整段长度 */
    fun daysLeft(period: String, today: LocalDate = LocalDate.now()): Int {
        val start = startDate(period)
        val end = endDate(period)
        return when {
            !today.isBefore(end) -> 0
            today.isBefore(start) -> daysTotal(period)
            else -> ChronoUnit.DAYS.between(today, end).toInt()
        }
    }

    /** 展示文案：2026 年 10 月 / 2026 年第 42 周 / 2026 年 */
    fun label(period: String): String = when (cycleOf(period)) {
        BudgetCycle.MONTH -> YearMonth.parse(period).let { "${it.year} 年 ${it.monthValue} 月" }
        BudgetCycle.WEEK -> {
            val year = period.substringBefore("-W")
            val week = period.substringAfter("-W").toInt()
            "$year 年第 $week 周"
        }

        BudgetCycle.YEAR -> "$period 年"
    }

    /** 是否晚于另一个周期（用于禁止翻到未来） */
    fun isAfter(period: String, other: String): Boolean =
        startDate(period).isAfter(startDate(other))

    private fun toMillis(date: LocalDate): Long =
        date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
}
