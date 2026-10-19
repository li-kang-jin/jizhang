package com.jizhang.app.ui.bill

import com.jizhang.app.data.local.entity.LocalTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * 账单时间窗测试。
 * <p>
 * 重点是「从日历 / 统计页跳过来时只看选中的那天」：
 * 跳转带的是**闭区间**（止日 23:59:59.999），筛选条件存的是**左闭右开**，
 * 换算错了就会把第二天的账也框进来（选 16 日看到 16、17 两天）。
 */
class BillFilterTest {

    private val zone: ZoneId = ZoneId.systemDefault()

    private fun startOfDay(date: LocalDate): Long =
        date.atStartOfDay(zone).toInstant().toEpochMilli()

    private fun txAt(date: LocalDate, hour: Int, minute: Int): LocalTransaction = LocalTransaction(
        id = date.toEpochDay() * 10000 + hour * 100 + minute,
        bookId = 1L,
        type = 1,
        amount = 100L,
        accountId = 1L,
        tradeTime = date.atTime(LocalTime.of(hour, minute)).atZone(zone).toInstant().toEpochMilli()
    )

    /** 日历「在账单页看这一天」传的就是这个：起日 0 点 ~ 止日 23:59:59.999 */
    private fun dayPreset(date: LocalDate): Pair<Long, Long> =
        startOfDay(date) to (startOfDay(date.plusDays(1)) - 1)

    @Test
    fun `从日历跳过来只看选中那一天`() {
        val day = LocalDate.of(2026, 10, 16)
        val (start, end) = dayPreset(day)
        val filter = BillFilter().withInclusiveRange(start, end)
        val bounds = filter.timeBounds()

        assertTrue("当天 23:59 的账属于这一天", filter.matches(txAt(day, 23, 59), bounds))
        assertFalse("次日 00:05 的账不该出现", filter.matches(txAt(day.plusDays(1), 0, 5), bounds))
        assertFalse("前一天 22:00 的账不该出现", filter.matches(txAt(day.minusDays(1), 22, 0), bounds))
    }

    @Test
    fun `从统计页按月跳过来只含当月`() {
        val monthStart = startOfDay(LocalDate.of(2026, 10, 1))
        val nextMonthStart = startOfDay(LocalDate.of(2026, 11, 1))
        val filter = BillFilter().withInclusiveRange(monthStart, nextMonthStart - 1)
        val bounds = filter.timeBounds()

        assertTrue(filter.matches(txAt(LocalDate.of(2026, 10, 1), 0, 0), bounds))
        assertTrue(filter.matches(txAt(LocalDate.of(2026, 10, 31), 23, 59), bounds))
        assertFalse(filter.matches(txAt(LocalDate.of(2026, 11, 1), 0, 0), bounds))
        assertFalse(filter.matches(txAt(LocalDate.of(2026, 9, 30), 23, 59), bounds))
    }

    @Test
    fun `日期选择器选的起止两天都整天算进`() {
        val from = LocalDate.of(2026, 10, 15)
        val to = LocalDate.of(2026, 10, 16)
        // 选择器给的是两端「当天 0 点」
        val filter = BillFilter().withDayRange(startOfDay(from), startOfDay(to))
        val bounds = filter.timeBounds()

        assertTrue(filter.matches(txAt(from, 0, 0), bounds))
        assertTrue(filter.matches(txAt(to, 23, 59), bounds))
        assertFalse(filter.matches(txAt(from.minusDays(1), 23, 59), bounds))
        assertFalse(filter.matches(txAt(to.plusDays(1), 0, 0), bounds))
    }

    @Test
    fun `单日范围只显示一个日期`() {
        val day = LocalDate.of(2026, 10, 16)
        val (start, end) = dayPreset(day)

        assertEquals("10月16日", BillFilter().withInclusiveRange(start, end).rangeLabel())
    }

    @Test
    fun `多日范围显示起止两天`() {
        val filter = BillFilter().withDayRange(
            startDayMillis = startOfDay(LocalDate.of(2026, 10, 1)),
            endDayMillis = startOfDay(LocalDate.of(2026, 10, 16))
        )

        assertEquals("10月1日 - 10月16日", filter.rangeLabel())
    }
}
