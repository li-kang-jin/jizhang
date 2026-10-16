package com.jizhang.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 统计时间窗测试。
 * <p>
 * 这里有一条血泪教训：`canGoNext` / `previousPeriod` 这类「会构造新 ReportPeriod」的成员
 * 一旦写成属性初始化器，构造期就会互相触发，直接栈溢出闪退。所以第一个用例专门盯死这件事。
 */
class ReportPeriodTest {

    private val oct16 = LocalDate.of(2026, 10, 16)

    @Test
    fun `构造与派生窗口不会无限递归`() {
        val period = ReportPeriod(ReportGranularity.MONTH, oct16)

        // 这几个属性都会触发新的 ReportPeriod 构造，能正常返回就说明没有递归
        assertFalse(period.previousPeriod.startDate.isAfter(period.startDate))
        assertTrue(period.lastYearPeriod.startDate.year == 2025)
        period.canGoNext
        period.future
        ReportPeriod.current()
        ReportPeriod.current(ReportGranularity.DAY).next()
    }

    @Test
    fun `月的区间是整月左闭右开`() {
        val period = ReportPeriod(ReportGranularity.MONTH, oct16)

        assertEquals(LocalDate.of(2026, 10, 1), period.startDate)
        assertEquals(LocalDate.of(2026, 11, 1), period.endDate)
        assertEquals(31, period.daysTotal)
        assertEquals(epochOf(2026, 10, 1), period.startMillis)
        assertEquals(epochOf(2026, 11, 1), period.endMillis)
    }

    @Test
    fun `日与年的区间按自然日与自然年`() {
        val day = ReportPeriod(ReportGranularity.DAY, oct16)
        assertEquals(LocalDate.of(2026, 10, 16), day.startDate)
        assertEquals(LocalDate.of(2026, 10, 17), day.endDate)
        assertEquals(1, day.daysTotal)

        val year = ReportPeriod(ReportGranularity.YEAR, oct16)
        assertEquals(LocalDate.of(2026, 1, 1), year.startDate)
        assertEquals(LocalDate.of(2027, 1, 1), year.endDate)
        assertEquals(365, year.daysTotal)
    }

    @Test
    fun `翻页步长随粒度变化`() {
        assertEquals(
            LocalDate.of(2026, 10, 15),
            ReportPeriod(ReportGranularity.DAY, oct16).previous().startDate
        )
        assertEquals(
            LocalDate.of(2026, 9, 1),
            ReportPeriod(ReportGranularity.MONTH, oct16).previous().startDate
        )
        assertEquals(
            LocalDate.of(2025, 1, 1),
            ReportPeriod(ReportGranularity.YEAR, oct16).previous().startDate
        )
    }

    @Test
    fun `换粒度保留锚点`() {
        val year = ReportPeriod(ReportGranularity.MONTH, oct16).withGranularity(ReportGranularity.YEAR)

        assertEquals(ReportGranularity.YEAR, year.granularity)
        assertEquals(LocalDate.of(2026, 1, 1), year.startDate)
        assertEquals(oct16, year.anchor)
    }

    @Test
    fun `当期比到现在为止的天数而不是整段`() {
        // 今天（10-16）看本月：日均要摊 16 天，不是 31 天
        val currentMonth = ReportPeriod(ReportGranularity.MONTH, LocalDate.now())
        assertEquals(LocalDate.now().dayOfMonth, currentMonth.daysCounted)

        // 已结束的月份算整段
        val pastMonth = ReportPeriod(ReportGranularity.MONTH, LocalDate.now().minusMonths(1))
        assertEquals(pastMonth.daysTotal, pastMonth.daysCounted)
    }

    @Test
    fun `不允许翻到未来`() {
        val currentMonth = ReportPeriod(ReportGranularity.MONTH, LocalDate.now())
        assertTrue(currentMonth.isCurrent)
        assertFalse(currentMonth.canGoNext)
        assertTrue(currentMonth.next().future)

        val lastMonth = ReportPeriod(ReportGranularity.MONTH, LocalDate.now().minusMonths(1))
        assertFalse(lastMonth.isCurrent)
        assertTrue(lastMonth.canGoNext)
    }

    @Test
    fun `同比窗口是去年同期`() {
        val day = ReportPeriod(ReportGranularity.DAY, oct16)
        assertEquals(LocalDate.of(2025, 10, 16), day.lastYearPeriod.startDate)

        val month = ReportPeriod(ReportGranularity.MONTH, oct16)
        assertEquals(LocalDate.of(2025, 10, 1), month.lastYearPeriod.startDate)

        // 年粒度的同比就是上一年
        val year = ReportPeriod(ReportGranularity.YEAR, oct16)
        assertEquals(LocalDate.of(2025, 1, 1), year.lastYearPeriod.startDate)
    }

    @Test
    fun `展示文案符合现有日期格式`() {
        assertEquals("2026 年 10 月", ReportPeriod(ReportGranularity.MONTH, oct16).label)
        assertEquals("2026 年 10 月 16 日", ReportPeriod(ReportGranularity.DAY, oct16).label)
        assertEquals("2026 年", ReportPeriod(ReportGranularity.YEAR, oct16).label)
        assertEquals("10 月 16 日（周五）", ReportPeriod(ReportGranularity.DAY, oct16).shortLabel)
    }

    @Test
    fun `趋势柱子对应的时间窗按粒度换算`() {
        // 月粒度：第 7 根柱子 = 10 月 8 日那一整天（0 号柱子是 1 日）
        val month = ReportPeriod(ReportGranularity.MONTH, oct16)
        val (dayStart, dayEnd) = month.bucketMillis(7)
        assertEquals(epochOf(2026, 10, 8), dayStart)
        assertEquals(epochOf(2026, 10, 9), dayEnd)

        // 年粒度：第 2 根柱子 = 3 月整月
        val year = ReportPeriod(ReportGranularity.YEAR, oct16)
        val (marchStart, marchEnd) = year.bucketMillis(2)
        assertEquals(epochOf(2026, 3, 1), marchStart)
        assertEquals(epochOf(2026, 4, 1), marchEnd)

        // 日粒度：第 14 根柱子 = 14:00 - 15:00
        val day = ReportPeriod(ReportGranularity.DAY, oct16)
        val (hourStart, hourEnd) = day.bucketMillis(14)
        assertEquals(epochOfHour(2026, 10, 16, 14), hourStart)
        assertEquals(epochOfHour(2026, 10, 16, 15), hourEnd)
    }

    @Test
    fun `趋势柱子的标题带上日期上下文`() {
        assertEquals("10 月 8 日（周四）", ReportPeriod(ReportGranularity.MONTH, oct16).bucketLabel(7))
        assertEquals("2026 年 3 月", ReportPeriod(ReportGranularity.YEAR, oct16).bucketLabel(2))
        assertEquals("10 月 16 日 14:00 - 15:00", ReportPeriod(ReportGranularity.DAY, oct16).bucketLabel(14))
        // 最后一格不能写成 24:00
        assertEquals("10 月 16 日 23:00 - 00:00", ReportPeriod(ReportGranularity.DAY, oct16).bucketLabel(23))
    }

    private fun epochOf(year: Int, month: Int, day: Int): Long =
        LocalDateTime.of(year, month, day, 0, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private fun epochOfHour(year: Int, month: Int, day: Int, hour: Int): Long =
        LocalDateTime.of(year, month, day, hour, 0)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
}
