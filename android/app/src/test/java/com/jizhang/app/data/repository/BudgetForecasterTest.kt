package com.jizhang.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * 预算预测的回归测试：只锁住「方向性」结论（概率高低、建议额度是否归零、是否降级），
 * 不锁具体小数 —— 蒙特卡洛带固定种子，但参数微调时不该让测试变成改数字游戏。
 */
class BudgetForecasterTest {

    /** 2026-10：31 天，今天 10-17 → 已过 17 天（含今天），剩 14 天 */
    private val today: LocalDate = LocalDate.of(2026, 10, 17)
    private val period: String = "2026-10"

    private fun samples(days: Int, amount: Long): List<DailySpend> =
        (0 until days)
            .map { today.minusDays(it.toLong()) }
            .sorted()
            .map { DailySpend(it, amount) }

    @Test
    fun `稳定支出不会判超支`() {
        val forecast = BudgetForecaster.forecast(
            period = period,
            // 31 天 × 100 元 = 3100，预算给到 3200，留 100 元缓冲
            totalBudget = 320_000L,
            spentSoFar = 170_000L, // 17 天 × 100 元
            fixed = FixedReservation(),
            dailySpends = samples(60, 10_000L),
            today = today
        )

        assertEquals(17, forecast.elapsedDays)
        // 剩余天数含今天：10-17..10-31 共 15 天（明天起可模拟的天数是 14 天）
        assertEquals(15, forecast.daysLeft)
        assertFalse(forecast.degraded)
        assertTrue("概率应很低，实际 ${forecast.overrunProbability}", forecast.overrunProbability < 0.2)
        assertTrue("建议额度应接近 100 元，实际 ${forecast.suggestedDaily}", forecast.suggestedDaily in 8_000L..11_000L)
    }

    @Test
    fun `花得太快会判超支且建议额度归零`() {
        val forecast = BudgetForecaster.forecast(
            period = period,
            totalBudget = 200_000L, // 2000 元，已花 1700
            spentSoFar = 170_000L,
            fixed = FixedReservation(),
            dailySpends = samples(60, 10_000L),
            today = today
        )

        assertTrue("概率应很高，实际 ${forecast.overrunProbability}", forecast.overrunProbability > 0.9)
        // 剩 300 元 / 15 天 = 20 元/天；样本方差为 0，安全边际为 0，建议额度就是 20 元
        assertEquals(2_000L, forecast.suggestedDaily)
    }

    @Test
    fun `样本不足时退回布朗近似`() {
        val forecast = BudgetForecaster.forecast(
            period = period,
            totalBudget = 300_000L,
            spentSoFar = 50_000L,
            fixed = FixedReservation(),
            dailySpends = samples(5, 10_000L),
            today = today
        )

        assertTrue(forecast.degraded)
        assertTrue(forecast.overrunProbability in 0.0..1.0)
        // 曲线仍然要完整：从中位线到周期末都有值
        assertEquals(forecast.daysTotal, forecast.median.lastIndex)
        assertTrue(forecast.median.last() != null)
        assertTrue(forecast.low.last()!! <= forecast.median.last()!!)
        assertTrue(forecast.high.last()!! >= forecast.median.last()!!)
    }

    @Test
    fun `预算越大建议额度越高`() {
        fun suggested(budget: Long) = BudgetForecaster.forecast(
            period = period,
            totalBudget = budget,
            spentSoFar = 170_000L,
            fixed = FixedReservation(),
            dailySpends = samples(60, 10_000L),
            today = today
        ).suggestedDaily

        assertTrue(suggested(400_000L) > suggested(300_000L))
    }

    @Test
    fun `已花超出预算时概率为一`() {
        val forecast = BudgetForecaster.forecast(
            period = period,
            totalBudget = 100_000L,
            spentSoFar = 170_000L,
            fixed = FixedReservation(),
            dailySpends = samples(60, 10_000L),
            today = today
        )

        assertEquals(1.0, forecast.overrunProbability, 1e-9)
    }
}
