package com.jizhang.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 预算周期计算测试。
 * <p>
 * 重点覆盖 ISO 周的边界（跨年、周一起始）与跨周期翻页。
 */
class BudgetPeriodsTest {

    @Test
    fun `按月生成与识别`() {
        val period = BudgetPeriods.of(BudgetCycle.MONTH, LocalDate.of(2026, 10, 5))

        assertEquals("2026-10", period)
        assertEquals(BudgetCycle.MONTH, BudgetPeriods.cycleOf(period))
    }

    @Test
    fun `按周使用 ISO 周编号`() {
        // 2026-01-01 是周四，按 ISO 规则属于 2026 年第 1 周
        val period = BudgetPeriods.of(BudgetCycle.WEEK, LocalDate.of(2026, 1, 1))

        assertEquals("2026-W01", period)
        assertEquals(BudgetCycle.WEEK, BudgetPeriods.cycleOf(period))
    }

    @Test
    fun `按年生成与识别`() {
        val period = BudgetPeriods.of(BudgetCycle.YEAR, LocalDate.of(2026, 10, 5))

        assertEquals("2026", period)
        assertEquals(BudgetCycle.YEAR, BudgetPeriods.cycleOf(period))
    }

    @Test
    fun `周周期区间是周一到下周一`() {
        val start = BudgetPeriods.startDate("2026-W42")

        assertEquals(1, start.dayOfWeek.value)
        assertEquals(7, ChronoUnit.DAYS.between(start, BudgetPeriods.endDate("2026-W42")))
    }

    @Test
    fun `跨年周归属正确`() {
        // 2026-12-31 是周四，属于 2026 年最后一周
        val period = BudgetPeriods.of(BudgetCycle.WEEK, LocalDate.of(2026, 12, 31))
        assertEquals("2026", period.substringBefore("-W"))
    }

    @Test
    fun `按月翻页跨年正确`() {
        assertEquals("2026-09", BudgetPeriods.previous("2026-10"))
        assertEquals("2026-11", BudgetPeriods.next("2026-10"))
        assertEquals("2025-12", BudgetPeriods.previous("2026-01"))
        assertEquals("2027-01", BudgetPeriods.next("2026-12"))
    }

    @Test
    fun `按年翻页正确`() {
        assertEquals("2025", BudgetPeriods.previous("2026"))
        assertEquals("2027", BudgetPeriods.next("2026"))
    }

    @Test
    fun `按周翻页正确`() {
        assertEquals("2026-W01", BudgetPeriods.previous("2026-W02"))
    }

    @Test
    fun `周期总天数`() {
        assertEquals(31, BudgetPeriods.daysTotal("2026-10"))
        assertEquals(7, BudgetPeriods.daysTotal("2026-W42"))
        assertEquals(365, BudgetPeriods.daysTotal("2026"))
    }

    @Test
    fun `剩余天数含今天`() {
        assertEquals(1, BudgetPeriods.daysLeft("2026", LocalDate.of(2026, 12, 31)))
        assertEquals(31, BudgetPeriods.daysLeft("2026-10", LocalDate.of(2026, 10, 1)))
    }

    @Test
    fun `已结束周期剩余为零_未开始为整段`() {
        assertEquals(0, BudgetPeriods.daysLeft("2026-09", LocalDate.of(2026, 10, 15)))
        assertEquals(7, BudgetPeriods.daysLeft("2026-W50", LocalDate.of(2026, 10, 15)))
    }

    @Test
    fun `展示文案随周期变化`() {
        assertEquals("2026 年 10 月", BudgetPeriods.label("2026-10"))
        assertEquals("2026 年第 42 周", BudgetPeriods.label("2026-W42"))
        assertEquals("2026 年", BudgetPeriods.label("2026"))
    }

    @Test
    fun `前后比较按起始日`() {
        org.junit.Assert.assertTrue(BudgetPeriods.isAfter("2026-11", "2026-10"))
        org.junit.Assert.assertFalse(BudgetPeriods.isAfter("2026-10", "2026-10"))
        org.junit.Assert.assertTrue(BudgetPeriods.isAfter("2026", "2025-12"))
    }
}
