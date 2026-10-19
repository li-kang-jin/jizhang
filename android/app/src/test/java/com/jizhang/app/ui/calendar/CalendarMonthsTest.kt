package com.jizhang.app.ui.calendar

import com.jizhang.app.data.local.entity.LocalTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId

/**
 * 日历页的纯逻辑测试。
 * <p>
 * 翻月、跨年、边界这些逻辑错一格，界面上只是「数字挪了一格」，肉眼很难发现，
 * 所以放在这里用单测盯死；顺带覆盖「当天小计」的口径（转账不算收支但单独记数）。
 */
class CalendarMonthsTest {

    @Test
    fun `页码与年月一一对应且能跨年`() {
        val oct = YearMonth.of(2026, 10)
        assertEquals(oct, CalendarMonths.monthOf(CalendarMonths.pageOf(oct)))

        // 相邻页 = 相邻月，跨年不需要特判
        val dec = YearMonth.of(2025, 12)
        assertEquals(YearMonth.of(2026, 1), CalendarMonths.monthOf(CalendarMonths.pageOf(dec) + 1))
        assertEquals(YearMonth.of(2025, 11), CalendarMonths.monthOf(CalendarMonths.pageOf(dec) - 1))
    }

    @Test
    fun `时间戳按本机时区归月`() {
        val millis = LocalDate.of(2026, 10, 16)
            .atTime(23, 30)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

        assertEquals(YearMonth.of(2026, 10), CalendarMonths.monthOfMillis(millis))
    }

    @Test
    fun `上界固定当前月而下界照顾最早账单`() {
        val today = YearMonth.of(2026, 10)
        val todayPage = CalendarMonths.pageOf(today)

        // 一笔账都没有：也要留出一年空月份可以往回翻
        val plain = CalendarMonths.range(today, null)
        assertEquals(todayPage, plain.last)
        assertEquals(todayPage - CalendarMonths.DEFAULT_BACK_MONTHS, plain.first)

        // 最早一笔在两年前：下界跟着放宽到那一页
        val old = CalendarMonths.range(today, YearMonth.of(2024, 3))
        assertEquals(CalendarMonths.pageOf(YearMonth.of(2024, 3)), old.first)

        // 账都是近期的：下界不会比默认的一年更靠后
        val fresh = CalendarMonths.range(today, today)
        assertEquals(todayPage - CalendarMonths.DEFAULT_BACK_MONTHS, fresh.first)
        assertTrue(fresh.contains(todayPage))
    }

    @Test
    fun `距离用于淘汰离当前月最远的缓存`() {
        val visible = YearMonth.of(2026, 10)
        assertEquals(0, CalendarMonths.distance(visible, visible))
        assertEquals(2, CalendarMonths.distance(visible, YearMonth.of(2026, 12)))
        assertEquals(3, CalendarMonths.distance(visible, YearMonth.of(2026, 7)))
    }

    @Test
    fun `日期是否落在这一页的月份里`() {
        val month = YearMonth.of(2026, 10)
        assertTrue(CalendarMonths.isInMonth(LocalDate.of(2026, 10, 1), month))
        assertTrue(CalendarMonths.isInMonth(LocalDate.of(2026, 10, 31), month))
        assertFalse(CalendarMonths.isInMonth(LocalDate.of(2026, 9, 30), month))
    }

    @Test
    fun `当天小计按类型分别统计`() {
        val figures = figuresOf(
            listOf(
                tx(id = 1L, type = 1, amount = 3800L),
                tx(id = 2L, type = 1, amount = 1200L),
                tx(id = 3L, type = 2, amount = 500000L),
                tx(id = 4L, type = 3, amount = 20000L)
            )
        )

        assertEquals(5000L, figures.expense)
        assertEquals(2, figures.expenseCount)
        assertEquals(500000L, figures.income)
        assertEquals(1, figures.incomeCount)
        assertEquals(1, figures.transferCount)
        // 收支笔数不含转账
        assertEquals(3, figures.txCount)
        assertEquals(495000L, figures.balance)
        assertFalse(figures.isEmpty)

        assertTrue(figuresOf(emptyList()).isEmpty)
    }

    private fun tx(id: Long, type: Int, amount: Long) = LocalTransaction(
        id = id,
        bookId = 1L,
        type = type,
        amount = amount,
        accountId = 1L,
        tradeTime = 0L
    )
}
