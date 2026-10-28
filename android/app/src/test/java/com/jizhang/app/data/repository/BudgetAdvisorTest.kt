package com.jizhang.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 预算建议引擎的测试。
 *
 * 约定：
 * - 金额一律用「分」，和业务字段一致，避免测试里再换算；
 * - 历史序列 [0] 是最近一个月，和 [BudgetAdvisor] 的口径一致；
 * - 只锁「方向性结论」和关键数值（压缩到上限、固定支出不被砍），不锁文案措辞。
 */
class BudgetAdvisorTest {

    private fun history(
        id: Long,
        name: String,
        vararg months: Long,
        lastYear: Long? = null
    ) = CategoryHistory(
        categoryId = id,
        name = name,
        monthlyFen = months.toList(),
        samePeriodLastYearFen = lastYear
    )

    @Test
    fun `一次性大额支出不会被当成常态`() {
        // 5 个月 300 元 + 1 个月 2000 元（年费）：均值 583 元，但真实月度开销是 300 元
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "保险", 30_000, 200_000, 30_000, 30_000, 30_000, 30_000)
            ),
            monthlyIncomeFen = emptyList()
        )

        val item = advice.items.single()
        // 300 × 5/6 + 2000/12 ≈ 416.7 元 → 取整 420 元
        assertEquals(42_000L, item.amountFen)
        assertTrue("不应被均值带跑：${item.amountFen}", item.amountFen < 58_333L)
        assertTrue("说明里应提到摊销：${item.reason}", item.reason.contains("摊销"))
    }

    @Test
    fun `没有历史但有固定支出时以固定支出为下限`() {
        val advice = BudgetAdvisor.advise(
            categories = listOf(history(1, "房租", 0, 0, 0, 0, 0, 0)),
            monthlyIncomeFen = emptyList(),
            recurringByCategoryFen = mapOf(1L to 300_000L)
        )

        val item = advice.items.single()
        assertEquals(300_000L, item.amountFen)
        assertTrue("应说明下限来源：${item.reason}", item.reason.contains("不低于固定支出"))
    }

    @Test
    fun `超过收入上限时按比例压缩`() {
        // 两个分类各 1000 元/月 → 建议 2000 元；收入 2000 元、储蓄率 20% → 上限 1600 元
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "餐饮", 100_000, 100_000, 100_000, 100_000, 100_000, 100_000),
                history(2, "交通", 100_000, 100_000, 100_000, 100_000, 100_000, 100_000)
            ),
            monthlyIncomeFen = List(6) { 200_000L },
            savingsRate = 0.2
        )

        assertEquals(160_000L, advice.ceilingFen)
        assertTrue(advice.compressed)
        assertTrue("总预算不能超过上限：${advice.totalFen}", advice.totalFen <= 160_000L)
        assertEquals(20, advice.savingsRatePercent)
        assertTrue("应说明被压缩：${advice.note}", advice.note?.contains("储蓄率") == true)
    }

    @Test
    fun `压缩时不动固定支出`() {
        // 房租 3000（固定）、餐饮 2000（弹性）；收入 4000、储蓄率 20% → 上限 3200
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "房租", 300_000, 300_000, 300_000, 300_000, 300_000, 300_000),
                history(2, "餐饮", 200_000, 200_000, 200_000, 200_000, 200_000, 200_000)
            ),
            monthlyIncomeFen = List(6) { 400_000L },
            recurringByCategoryFen = mapOf(1L to 300_000L),
            savingsRate = 0.2
        )

        val rent = advice.items.first { it.categoryId == 1L }
        val food = advice.items.first { it.categoryId == 2L }
        assertEquals("房租不应被砍", 300_000L, rent.amountFen)
        assertEquals("弹性支出压到上限剩余", 20_000L, food.amountFen)
        assertEquals(320_000L, advice.totalFen)
    }

    @Test
    fun `收入波动大时按偏低的分位算上限`() {
        // 一个月 5000，其余 1000：若按均值会高估承受力
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "餐饮", 50_000, 50_000, 50_000, 50_000, 50_000, 50_000)
            ),
            monthlyIncomeFen = listOf(500_000, 100_000, 100_000, 100_000, 100_000, 100_000),
            savingsRate = 0.2
        )

        assertEquals(100_000L, advice.incomeFen)
        assertEquals(80_000L, advice.ceilingFen)
    }

    @Test
    fun `数据不足时给出低可信度并说明`() {
        // 只有 6 个月前那一个月花过钱
        val advice = BudgetAdvisor.advise(
            categories = listOf(history(1, "购物", 0, 0, 0, 0, 0, 50_000)),
            monthlyIncomeFen = emptyList()
        )

        assertEquals(BudgetConfidence.LOW, advice.confidence)
        assertEquals(BudgetConfidence.LOW, advice.items.single().confidence)
        assertTrue("应说明样本少：${advice.note}", advice.note?.contains("只有 1 个月") == true)
        assertTrue("应说明没有收入：${advice.note}", advice.note?.contains("没有收入记录") == true)
    }

    @Test
    fun `完全没有账单时不给建议`() {
        val advice = BudgetAdvisor.advise(
            categories = emptyList(),
            monthlyIncomeFen = emptyList()
        )

        assertTrue(advice.items.isEmpty())
        assertEquals(0L, advice.totalFen)
        assertNotNull(advice.note)
    }

    @Test
    fun `所有建议金额都是十元的整数倍`() {
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "餐饮", 12_345, 23_456, 34_567, 45_678, 56_789, 67_890),
                history(2, "交通", 4_321, 5_432, 6_543, 7_654, 8_765, 9_876)
            ),
            monthlyIncomeFen = emptyList()
        )

        advice.items.forEach { item ->
            assertEquals("${item.name} 未取整：${item.amountFen}", 0L, item.amountFen % 1_000L)
        }
    }

    @Test
    fun `没有收入时不设上限但仍给建议`() {
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "餐饮", 100_000, 100_000, 100_000, 100_000, 100_000, 100_000)
            ),
            monthlyIncomeFen = emptyList()
        )

        assertEquals(null, advice.incomeFen)
        assertEquals(null, advice.ceilingFen)
        assertEquals(null, advice.savingsRatePercent)
        assertEquals(100_000L, advice.totalFen)
        assertTrue(!advice.compressed)
    }

    @Test
    fun `波动小的分类给出高可信度`() {
        val advice = BudgetAdvisor.advise(
            categories = listOf(
                history(1, "餐饮", 100_000, 101_000, 99_000, 100_500, 99_500, 100_000)
            ),
            monthlyIncomeFen = emptyList()
        )

        assertEquals(BudgetConfidence.HIGH, advice.items.single().confidence)
        assertEquals(BudgetConfidence.HIGH, advice.confidence)
    }

    @Test
    fun `去年同期的开销会校准建议`() {
        val base = listOf(
            history(1, "旅游", 0, 0, 0, 0, 0, 0).copy(monthlyFen = List(6) { 50_000L })
        )
        val plain = BudgetAdvisor.advise(base, emptyList())
        val seasonal = BudgetAdvisor.advise(
            listOf(base.single().copy(samePeriodLastYearFen = 80_000L)),
            emptyList()
        )

        assertTrue(
            "去年同月更高时应上调：${plain.totalFen} -> ${seasonal.totalFen}",
            seasonal.totalFen > plain.totalFen
        )
    }
}
