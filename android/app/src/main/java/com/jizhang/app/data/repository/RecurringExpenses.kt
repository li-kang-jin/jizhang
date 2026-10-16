package com.jizhang.app.data.repository

import com.jizhang.app.data.local.entity.LocalRecurringExpense
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.TemporalAdjusters

/**
 * 固定支出的发生日展开。
 * <p>
 * 纯函数，不碰数据库 —— 预算预留的计算和「下次什么时候扣」的展示都用它，
 * 保证两边对「哪天该扣」的理解一致。
 */
object RecurringExpenses {

    /**
     * 某个固定支出在 {@code [from, untilExclusive)} 内的所有发生日。
     * <p>
     * 已经停用的返回空；同时会按 `startDate` / `endDate` 裁剪。
     */
    fun occurrences(
        item: LocalRecurringExpense,
        from: LocalDate,
        untilExclusive: LocalDate
    ): List<LocalDate> {
        if (!item.enabled) {
            return emptyList()
        }

        val itemStart = millisToDate(item.startDate)
        val itemEndExclusive = item.endDate?.let { millisToDate(it).plusDays(1) }

        val start = if (itemStart.isAfter(from)) itemStart else from
        val end = if (itemEndExclusive != null && itemEndExclusive.isBefore(untilExclusive)) {
            itemEndExclusive
        } else {
            untilExclusive
        }
        if (!start.isBefore(end)) {
            return emptyList()
        }

        return when (item.freq) {
            LocalRecurringExpense.FREQ_WEEKLY -> weeklyOccurrences(item, start, end)
            LocalRecurringExpense.FREQ_YEARLY -> yearlyOccurrences(item, start, end)
            else -> monthlyOccurrences(item, start, end)
        }
    }

    /** 下一次发生日（含今天），找不到返回 null */
    fun nextOccurrence(item: LocalRecurringExpense, today: LocalDate): LocalDate? =
        occurrences(item, today, today.plusYears(2)).firstOrNull()

    /** 频率文案，例如「每月 5 日」「每周一」「每年 3 月 1 日」 */
    fun scheduleLabel(item: LocalRecurringExpense): String = when (item.freq) {
        LocalRecurringExpense.FREQ_WEEKLY -> "每${weekdayLabel(item.dayOfWeek)}"
        LocalRecurringExpense.FREQ_YEARLY -> "每年 ${item.monthOfYear} 月 ${item.dayOfMonth} 日"
        else -> "每月 ${item.dayOfMonth} 日"
    }

    fun freqLabel(freq: Int): String = when (freq) {
        LocalRecurringExpense.FREQ_WEEKLY -> "每周"
        LocalRecurringExpense.FREQ_YEARLY -> "每年"
        else -> "每月"
    }

    private fun weeklyOccurrences(
        item: LocalRecurringExpense,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {
        val target = DayOfWeek.of(item.dayOfWeek.coerceIn(1, 7))
        val result = mutableListOf<LocalDate>()
        var date = start.with(TemporalAdjusters.nextOrSame(target))
        while (date.isBefore(end)) {
            result += date
            date = date.plusWeeks(1)
        }
        return result
    }

    private fun monthlyOccurrences(
        item: LocalRecurringExpense,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var month = YearMonth.from(start)
        while (true) {
            val date = occurrenceInMonth(month, item.dayOfMonth)
            if (!date.isBefore(end)) {
                break
            }
            if (!date.isBefore(start)) {
                result += date
            }
            month = month.plusMonths(1)
        }
        return result
    }

    private fun yearlyOccurrences(
        item: LocalRecurringExpense,
        start: LocalDate,
        end: LocalDate
    ): List<LocalDate> {
        val result = mutableListOf<LocalDate>()
        var year = start.year
        while (true) {
            val date = occurrenceInMonth(YearMonth.of(year, item.monthOfYear.coerceIn(1, 12)), item.dayOfMonth)
            if (!date.isBefore(end)) {
                break
            }
            if (!date.isBefore(start)) {
                result += date
            }
            year++
        }
        return result
    }

    /**
     * 某月里的发生日。
     * <p>
     * 31 号的固定支出在 2 月要落到当月最后一天，否则 `atDay(31)` 会直接抛异常。
     */
    private fun occurrenceInMonth(month: YearMonth, dayOfMonth: Int): LocalDate =
        month.atDay(dayOfMonth.coerceIn(1, month.lengthOfMonth()))

    private fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    private fun millisToDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
}
