package com.jizhang.app.data.repository

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.roundToInt

/**
 * 财富规划的回归测试。
 * <p>
 * 和 [BudgetForecasterTest] 同样的原则：**只锁方向性与闭式解**，不锁蒙特卡洛的具体小数。
 * 种子固定能保证「同一输入 → 同一输出」，但真正要守住的是：
 * 存得多就更快、花得多就更慢、数据不足不硬编结论。
 */
class WealthPlannerTest {

    /** 一万元，换算成「分」 */
    private val yiWanFen = 1_000_000L

    private fun planOf(
        monthlyExpense: Long = 10_000 * 100L,
        monthlyIncome: Long = 20_000 * 100L,
        netWorth: Long = 500_000 * 100L,
        yearsToTarget: Int? = 20
    ): WealthPlan = WealthPlanner.plan(
        WealthInputs(
            netWorthFen = netWorth,
            monthlyExpenseFen = List(12) { monthlyExpense },
            monthlyIncomeFen = List(12) { monthlyIncome },
            yearsToTarget = yearsToTarget,
            simulations = 600,
            seed = 42L
        )
    )

    @Test
    fun `FI 数字等于年支出除以提取率`() {
        // 月支出 1 万 → 年支出 12 万；4% 提取率 → 300 万
        val plan = planOf()

        assertEquals(300 * yiWanFen, plan.fiNumberFen)
        assertEquals(12 * yiWanFen, plan.annualExpenseFen)
        // 年收入 24 万 - 年支出 12 万 = 12 万
        assertEquals(12 * yiWanFen, plan.annualSavingFen)
        assertEquals(0.5, plan.savingsRate!!, 1e-9)
        // 净资产 50 万 / 300 万
        assertEquals(1.0 / 6.0, plan.progress, 1e-9)
        assertEquals(250 * yiWanFen, plan.gapFen)
        // 50 万 ÷ (1 万/月) = 50 个月
        assertEquals(50.0, plan.freedomMonths!!, 1e-9)
    }

    @Test
    fun `确定性年数与闭式解一致`() {
        // 存一半、实际收益 5%、4% 提取率：经典结论约 16.6 年
        val years = WealthPlanner.deterministicYears(
            netWorthFen = 0L,
            annualSavingFen = 12 * yiWanFen,
            fiNumberFen = 300 * yiWanFen,
            realReturn = 0.05
        )

        assertEquals(16.62, years!!, 0.05)
    }

    @Test
    fun `已达标时不需要任何年份`() {
        val plan = planOf(netWorth = 400 * yiWanFen)

        assertEquals(0, plan.medianYears)
        assertEquals(1.0, plan.successProbability!!, 1e-9)
        assertEquals(0L, plan.gapFen)
        assertTrue(plan.progress > 1.0)
    }

    @Test
    fun `收入更高时更快达标`() {
        val base = planOf(monthlyIncome = 20_000 * 100L)
        val richer = planOf(monthlyIncome = 30_000 * 100L)

        assertEquals(base.fiNumberFen, richer.fiNumberFen)
        assertTrue(richer.deterministicYears!! < base.deterministicYears!!)
        assertTrue((richer.medianYears ?: Int.MAX_VALUE) <= (base.medianYears ?: Int.MAX_VALUE))
        assertTrue(richer.successProbability!! > base.successProbability!!)
    }

    @Test
    fun `支出更高时 FI 目标更大且更慢`() {
        val cheap = planOf(monthlyExpense = 10_000 * 100L)
        val pricey = planOf(monthlyExpense = 15_000 * 100L)

        assertTrue(pricey.fiNumberFen > cheap.fiNumberFen)
        assertTrue(pricey.deterministicYears!! > cheap.deterministicYears!!)
        assertTrue((pricey.medianYears ?: Int.MAX_VALUE) >= (cheap.medianYears ?: Int.MAX_VALUE))
    }

    @Test
    fun `样本不足时不硬给结论`() {
        val plan = WealthPlanner.plan(
            WealthInputs(
                netWorthFen = 10 * yiWanFen,
                monthlyExpenseFen = listOf(5_000 * 100L),
                monthlyIncomeFen = listOf(10_000 * 100L),
                simulations = 200
            )
        )

        assertEquals(1, plan.sampleMonths)
        assertFalse(plan.hasEnoughData)
        // 一个月的数据仍然能算出年化口径，只是标注样本不足
        assertTrue(plan.fiNumberFen > 0L)
    }

    @Test
    fun `没有支出数据时返回空结果而不是崩溃`() {
        val plan = WealthPlanner.plan(
            WealthInputs(
                netWorthFen = 10 * yiWanFen,
                monthlyExpenseFen = emptyList(),
                monthlyIncomeFen = emptyList()
            )
        )

        assertEquals(0L, plan.fiNumberFen)
        assertEquals(0.0, plan.progress, 1e-9)
        assertNull(plan.deterministicYears)
        assertNull(plan.medianYears)
        assertNull(plan.savingsRate)
        assertNull(plan.freedomMonths)
        assertFalse(plan.hasEnoughData)
    }

    @Test
    fun `结余为负且未达标时不编造年份`() {
        assertNull(
            WealthPlanner.deterministicYears(
                netWorthFen = 10 * yiWanFen,
                annualSavingFen = -1 * yiWanFen,
                fiNumberFen = 300 * yiWanFen,
                realReturn = 0.05
            )
        )
    }

    @Test
    fun `同一组参数必须给出同一组结果`() {
        val first = planOf()
        val second = planOf()

        assertEquals(first.medianYears, second.medianYears)
        assertEquals(first.p10Years, second.p10Years)
        assertEquals(first.p90Years, second.p90Years)
        assertEquals(first.fanP50, second.fanP50)
        assertEquals(first.neverReaches, second.neverReaches, 0.0)
    }

    @Test
    fun `分位数单调且扇形区间有序`() {
        val plan = planOf()

        val p10 = plan.p10Years
        val p50 = plan.medianYears
        val p90 = plan.p90Years
        if (p10 != null && p50 != null && p90 != null) {
            assertTrue(p10 <= p50)
            assertTrue(p50 <= p90)
        }
        plan.fanP10.indices.forEach { index ->
            assertTrue(plan.fanP10[index] <= plan.fanP50[index])
            assertTrue(plan.fanP50[index] <= plan.fanP90[index])
        }
        assertEquals(plan.fanYears.size, plan.fanP50.size)
    }

    @Test
    fun `杠杆榜方向正确且按提前年数降序`() {
        val inputs = WealthInputs(
            netWorthFen = 500_000 * 100L,
            monthlyExpenseFen = List(12) { 10_000 * 100L },
            monthlyIncomeFen = List(12) { 20_000 * 100L },
            simulations = 600,
            seed = 42L
        )

        val options = WealthPlanner.leverage(inputs)

        assertTrue(options.isNotEmpty())
        assertEquals(options.sortedByDescending { it.yearsSaved }, options)
        // 少花 10%、收益率 +1% 都应该提前，而不是推后
        assertTrue(options.first { it.label.contains("少花") }.yearsSaved > 0.0)
        assertTrue(options.first { it.label.contains("收益率") }.yearsSaved > 0.0)
        assertTrue(options.first { it.label.contains("多投") }.yearsSaved > 0.0)
    }

    @Test
    fun `没有支出样本时杠杆榜为空`() {
        assertTrue(WealthPlanner.leverage(WealthInputs(netWorthFen = 0L, monthlyExpenseFen = emptyList())).isEmpty())
    }

    @Test
    fun `压力测试只会让结论变差`() {
        val inputs = WealthInputs(
            netWorthFen = 50 * yiWanFen,
            monthlyExpenseFen = List(12) { 10_000 * 100L },
            monthlyIncomeFen = List(12) { 20_000 * 100L },
            yearsToTarget = 20,
            simulations = 600,
            seed = 42L
        )

        val results = WealthPlanner.stressTest(inputs)

        assertEquals(3, results.size)
        results.forEach { result ->
            assertTrue(
                "${result.label} 的推迟年数不该为负",
                result.yearsDelayed == null || result.yearsDelayed >= 0
            )
            result.successDrop?.let { assertTrue("${result.label} 概率不该上升", it >= 0.0) }
        }
        // 这两项是确定性的坏运气，一定推迟
        assertTrue(results.first { it.label.contains("−30%") }.yearsDelayed!! > 0)
        assertTrue(results.first { it.label.contains("1%") }.yearsDelayed!! > 0)
    }

    @Test
    fun `Coast FI 随目标年数折算且已算进结果`() {
        val plan = planOf()

        assertEquals(20, plan.targetYears)
        // 300 万、20 年、4.5% 实际收益：今天约 124 万就够
        val coast = plan.coastFiNumberFen!!
        assertTrue("coast=$coast", coast in 120 * yiWanFen..130 * yiWanFen)
    }

    /* ==================== 增长解剖 / 分布 ==================== */

    @Test
    fun `增长解剖与逐年递推一致`() {
        // 递推 W_t = W_{t-1}(1+r) + S 逐点迭代，必须和闭式分解逐点相等
        val start = 500_000 * 100L
        val saving = 120_000 * 100L
        val anatomy = WealthPlanner.anatomy(
            netWorthFen = start,
            annualSavingFen = saving,
            realReturn = 0.05,
            maxYears = 20
        )

        var expected = start.toDouble()
        for (year in 0..20) {
            val total = anatomy.contributed[year] + anatomy.compounded[year]
            assertEquals("第 $year 年", expected, total.toDouble(), 2.0)
            expected = expected * 1.05 + saving
        }
        assertEquals(21, anatomy.contributed.size)
        // 累计投入是直线：起点的本金 + 每年结余 × 年数，与收益率无关
        assertEquals(start + saving * 20, anatomy.contributed.last())
    }

    @Test
    fun `飞轮启动年只由收益率决定而与存多少无关`() {
        // W₀ = 0 时 t* = ln2 / ln(1+r)：解与 S 无关，这才是「半衰期」那个结论的可测形式
        val poor = WealthPlanner.anatomy(0L, 100_000 * 100L, 0.05, 60)
        val rich = WealthPlanner.anatomy(0L, 900_000 * 100L, 0.05, 60)

        assertEquals(poor.flywheelYear, rich.flywheelYear)
        assertEquals((ln(2.0) / ln(1.05)).roundToInt(), poor.flywheelYear!!)
        // 收益率越高，换挡越早
        val fast = WealthPlanner.anatomy(0L, 100_000 * 100L, 0.08, 60)
        assertTrue(fast.flywheelYear!! < poor.flywheelYear!!)
    }

    @Test
    fun `净资产越高飞轮启动越早且临界资产等于结余除以收益率`() {
        val base = WealthPlanner.anatomy(0L, 120_000 * 100L, 0.05, 60)
        val ahead = WealthPlanner.anatomy(3_000_000 * 100L, 120_000 * 100L, 0.05, 60)

        assertTrue(ahead.flywheelYear!! < base.flywheelYear!!)
        assertEquals(2_400_000 * 100L, base.criticalAssetFen)
        // 越过临界资产之后复利已经占上风
        assertEquals(0, ahead.flywheelYear)
    }

    @Test
    fun `结余非正时不编造飞轮年`() {
        assertNull(WealthPlanner.anatomy(1_000_000 * 100L, -50_000 * 100L, 0.05, 30).flywheelYear)
        assertNull(WealthPlanner.anatomy(1_000_000 * 100L, 0L, 0.05, 30).flywheelYear)
        assertNull(WealthPlanner.anatomy(1_000_000 * 100L, 50_000 * 100L, 0.0, 30).flywheelYear)
    }

    @Test
    fun `中位数路径与确定性骨架吻合`() {
        // 中位数路径的几何增速就是设定收益，所以它必须贴着确定性解走；
        // 差太多说明抽样或漂移项写错了（这是比"图好不好看"重要得多的校验）
        val plan = WealthPlanner.plan(
            WealthInputs(
                netWorthFen = 500_000 * 100L,
                monthlyExpenseFen = List(12) { 10_000 * 100L },
                monthlyIncomeFen = List(12) { 20_000 * 100L },
                simulations = 2_000,
                maxYears = 20,
                seed = 42L
            )
        )

        val skeleton = plan.anatomy.contributed[20] + plan.anatomy.compounded[20]
        val median = plan.fanP50.last()
        val deviation = abs(median - skeleton).toDouble() / skeleton

        assertTrue("median=$median skeleton=$skeleton", deviation < 0.10)
    }

    @Test
    fun `均值路径不低于中位数路径`() {
        val plan = planOf()

        assertEquals(plan.fanP50.size, plan.fanMean.size)
        plan.fanMean.forEachIndexed { index, mean ->
            assertTrue("第 $index 年", mean >= plan.fanP50[index] - 1)
        }
        // σ=15%、模拟窗口末端的裂口应有几个百分点以上，不能被抹成 0
        val gap = plan.fanMean.last().toDouble() / plan.fanP50.last() - 1.0
        assertTrue("gap=$gap", gap > 0.05)
    }

    @Test
    fun `达标年份直方图与达标概率自洽`() {
        val plan = planOf()
        val reached = plan.reachedHistogram.sum()
        val never = (plan.neverReaches * plan.simulations).roundToInt()

        assertEquals(plan.maxYears + 1, plan.reachedHistogram.size)
        assertTrue("reached=$reached never=$never", abs(reached + never - plan.simulations) <= 2)
        // 直方图不该出现负计数，也不该有超出窗口的桶
        assertTrue(plan.reachedHistogram.all { it >= 0 })
    }

    @Test
    fun `Coast FI 与机会成本按复利折算`() {
        // 300 万、20 年、5% 实际收益 → 今天约 113 万就够（不再追加）
        val coast = WealthPlanner.coastFiNumber(300 * yiWanFen, 0.05, 20)
        assertTrue("coast=$coast", coast in 112 * yiWanFen..114 * yiWanFen)

        // 30 元放 25 年、5% 实际收益 ≈ 101.6 元
        val future = WealthPlanner.opportunityCost(3_000L, 0.05, 25)
        assertTrue("future=$future", future in 10_100L..10_200L)
    }
}
