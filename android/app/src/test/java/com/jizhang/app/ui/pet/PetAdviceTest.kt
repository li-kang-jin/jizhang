package com.jizhang.app.ui.pet

import com.jizhang.app.data.local.entity.LocalTransaction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/**
 * 宠物理财建议引擎的测试。
 *
 * 约定：每条用例都**固定 today**，而且只锁「方向性的结论」（有没有这条建议、属于哪一类），
 * 不锁具体的措辞 —— 文案会调，语义不该跟着漂。
 */
class PetAdviceTest {

    private val zone = ZoneId.systemDefault()
    private val names = mapOf(
        1L to "餐饮",
        2L to "交通",
        3L to "购物",
        10L to "工资"
    )

    /** 2026-10-20，本月已经过了 20 天 */
    private val today = LocalDate.of(2026, 10, 20)

    private fun expense(
        id: Long,
        fen: Long,
        dayOfMonth: Int,
        categoryId: Long = 1L,
        month: Int = 10,
        hour: Int = 12
    ): LocalTestTxSpec = LocalTestTxSpec(
        id = id,
        type = 1,
        fen = fen,
        date = LocalDate.of(2026, month, dayOfMonth),
        hour = hour,
        categoryId = categoryId
    )

    private fun income(
        id: Long,
        fen: Long,
        dayOfMonth: Int,
        month: Int = 10,
        categoryId: Long = 10L
    ): LocalTestTxSpec = LocalTestTxSpec(
        id = id,
        type = 2,
        fen = fen,
        date = LocalDate.of(2026, month, dayOfMonth),
        hour = 9,
        categoryId = categoryId
    )

    private fun tx(spec: LocalTestTxSpec) = LocalTransaction(
        id = spec.id,
        bookId = 1L,
        type = spec.type,
        amount = spec.fen,
        accountId = 1L,
        categoryId = spec.categoryId,
        tradeTime = spec.date.atTime(spec.hour, 0).atZone(zone).toInstant().toEpochMilli(),
        pendingSync = false
    )

    private fun analyze(
        specs: List<LocalTestTxSpec>,
        streakDays: Int = 0,
        today: LocalDate = this.today
    ): PetInsight = PetAdvice.analyze(
        PetAdvice.factsOf(
            transactions = specs.map { tx(it) },
            categoryNames = names,
            today = today,
            streakDays = streakDays,
            zone = zone
        )
    )

    private fun kindsOf(insight: PetInsight): Set<PetTipKind> = insight.tips.map { it.kind }.toSet()

    @Test
    fun `本月没有账单时只给入口提示`() {
        val insight = analyze(emptyList())

        assertEquals(1, insight.tips.size)
        assertEquals(PetTipKind.HABIT, insight.tips.first().kind)
        assertEquals(PetMood.NORMAL, insight.mood)
        assertEquals("本月还没有账", insight.statusLine)
    }

    @Test
    fun `支出超过收入时预警并且心情变差`() {
        val insight = analyze(
            listOf(
                income(1, 500_000, dayOfMonth = 5),
                expense(2, 620_000, dayOfMonth = 8, categoryId = 3L)
            )
        )

        assertTrue(
            "应给出倒挂预警，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.ALERT && it.text.contains("超过收入") }
        )
        assertEquals(PetMood.WORRIED, insight.mood)
    }

    @Test
    fun `没记收入时不算入不敷出`() {
        // 本月没记工资不代表真的没有收入，只看支出会误判
        val insight = analyze(listOf(expense(1, 300_000, dayOfMonth = 8)))

        assertFalse(
            insight.tips.any { it.text.contains("超过收入") }
        )
    }

    @Test
    fun `某分类比上月同期涨得快时点名`() {
        val specs = buildList {
            // 上月同期：餐饮 500 元
            add(expense(101, 20_000, dayOfMonth = 3, month = 9))
            add(expense(102, 30_000, dayOfMonth = 18, month = 9))
            // 本月同期：餐饮 800 元（+300）
            add(expense(201, 30_000, dayOfMonth = 4))
            add(expense(202, 50_000, dayOfMonth = 15))
        }
        val insight = analyze(specs)

        assertTrue(
            "应点名餐饮的环比涨幅，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.ALERT && it.text.contains("餐饮") }
        )
    }

    @Test
    fun `小额波动不算趋势`() {
        // 上月 100 元 vs 本月 110 元：增量够小，不该报趋势
        val specs = listOf(
            expense(101, 10_000, dayOfMonth = 5, month = 9),
            expense(201, 11_000, dayOfMonth = 6)
        )
        val insight = analyze(specs)

        assertFalse(
            "小额波动不该当作趋势：${insight.tips.map { it.text }}",
            insight.tips.any { it.text.contains("比上月同期多花了") }
        )
    }

    @Test
    fun `照当前速度月底会超上月时提前预警`() {
        val specs = buildList {
            // 上月整月 1000 元
            add(expense(101, 100_000, dayOfMonth = 10, month = 9))
            // 本月已经过了 20 天，花了 1000 元 → 月底约 1550 元，明显超过上月
            add(expense(201, 100_000, dayOfMonth = 5))
        }
        val insight = analyze(specs)

        assertTrue(
            "应预测月底超上月，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.ALERT && it.text.contains("月底要花到") }
        )
    }

    @Test
    fun `月初几天不做月底预测`() {
        // 只过了 2 天，样本太少，任何预测都是瞎猜
        val earlyToday = LocalDate.of(2026, 10, 2)
        val specs = listOf(
            expense(101, 100_000, dayOfMonth = 10, month = 9),
            expense(201, 50_000, dayOfMonth = 1)
        )
        val insight = analyze(specs, today = earlyToday)

        assertFalse(
            "月初不该给月底预测：${insight.tips.map { it.text }}",
            insight.tips.any { it.text.contains("月底") }
        )
    }

    @Test
    fun `某个分类占比过高时被点名`() {
        // 餐饮 800 元，占本月支出 80%
        val specs = listOf(
            expense(1, 80_000, dayOfMonth = 6, categoryId = 1L),
            expense(2, 20_000, dayOfMonth = 9, categoryId = 2L)
        )
        val insight = analyze(specs)

        assertTrue(
            "应点名占比最高的分类，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.SAVING && it.text.contains("「餐饮」") }
        )
    }

    @Test
    fun `深夜账单够多才提醒`() {
        val specs = buildList {
            repeat(3) { index ->
                add(expense(index.toLong() + 1, 6_000, dayOfMonth = 5 + index, hour = 23))
            }
        }
        val insight = analyze(specs)
        assertTrue(
            "应提醒深夜消费，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.SAVING && it.text.contains("22 点以后") }
        )

        val quiet = analyze(listOf(expense(1, 6_000, dayOfMonth = 5, hour = 23)))
        assertFalse(quiet.tips.any { it.text.contains("22 点以后") })
    }

    @Test
    fun `有结余时给正向反馈`() {
        val insight = analyze(
            listOf(
                income(1, 1_000_000, dayOfMonth = 5),
                expense(2, 400_000, dayOfMonth = 8)
            )
        )

        assertTrue(
            "应给出结余反馈，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.CHEER && it.text.contains("结余") }
        )
        assertEquals(PetMood.HAPPY, insight.mood)
    }

    @Test
    fun `连续记账天数带来的成就感`() {
        val insight = analyze(
            listOf(expense(1, 10_000, dayOfMonth = 8)),
            streakDays = 9
        )

        assertTrue(
            "应给出连续记账反馈，实际 ${insight.tips.map { it.text }}",
            insight.tips.any { it.kind == PetTipKind.CHEER && it.text.contains("连续记账") }
        )
    }

    @Test
    fun `转账不参与任何计算`() {
        // 只有一笔 10 万元的转账：它既不是收入也不是支出，不该让宠物「看」出任何结论
        val transferOnly = listOf(
            LocalTestTxSpec(
                id = 1,
                type = 3,
                fen = 10_000_000,
                date = LocalDate.of(2026, 10, 6),
                hour = 10,
                categoryId = null
            )
        )
        val insight = analyze(transferOnly)

        assertEquals(1, insight.tips.size)
        assertEquals(PetTipKind.HABIT, insight.tips.first().kind)
    }

    @Test
    fun `预警排在省钱之前`() {
        val specs = buildList {
            // 收入 500 元、支出 1000 元：倒挂 + 餐饮占 88%
            add(income(1, 50_000, dayOfMonth = 5))
            add(expense(2, 88_000, dayOfMonth = 8, categoryId = 1L))
            add(expense(3, 12_000, dayOfMonth = 9, categoryId = 2L))
        }
        val insight = analyze(specs)

        assertEquals(PetTipKind.ALERT, insight.tips.first().kind)
        assertTrue(insight.tips.size > 1)
        assertTrue(kindsOf(insight).contains(PetTipKind.SAVING))
    }

    @Test
    fun `建议数量有上限`() {
        val specs = buildList {
            add(income(1, 100_000, dayOfMonth = 5))
            repeat(30) { index ->
                add(
                    expense(
                        id = index.toLong() + 10,
                        fen = 20_000,
                        dayOfMonth = 1 + index % 20,
                        categoryId = 1L,
                        hour = 23
                    )
                )
            }
        }
        val insight = analyze(specs, streakDays = 10)

        assertTrue("建议最多 5 条，实际 ${insight.tips.size}", insight.tips.size <= 5)
    }

    @Test
    fun `近况一行包含日均与连续天数`() {
        val insight = analyze(
            listOf(expense(1, 100_000, dayOfMonth = 10)),
            streakDays = 4
        )

        assertTrue(insight.statusLine.contains("日均支出"))
        assertTrue(insight.statusLine.contains("连续记账 4 天"))
        assertTrue(insight.statusLine.contains("还剩"))
    }

    private data class LocalTestTxSpec(
        val id: Long,
        val type: Int,
        val fen: Long,
        val date: LocalDate,
        val hour: Int,
        val categoryId: Long?
    )
}
