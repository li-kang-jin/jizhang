package com.jizhang.app.data.repository

import com.jizhang.app.data.local.entity.LocalInvestFlow
import com.jizhang.app.data.local.entity.PositionWithValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 投资模块的纯逻辑校验（不碰数据库）。
 * <p>
 * 重点盯两个「写错也不报错、只是数字悄悄变错」的地方：买卖方向的判定，
 * 以及未估值品种按成本还是按 0 计入市值。
 */
class InvestmentLogicTest {

    /* ==================== 买卖方向 ==================== */

    @Test
    fun `钱转入投资账户算买入`() {
        val side = investSideOf(
            positionAccountId = 7L,
            accountId = 1L,      // 储蓄卡
            toAccountId = 7L     // 投资账户
        )
        assertEquals(LocalInvestFlow.SIDE_BUY, side)
    }

    @Test
    fun `钱从投资账户转出算卖出`() {
        val side = investSideOf(
            positionAccountId = 7L,
            accountId = 7L,      // 投资账户
            toAccountId = 1L     // 银行卡
        )
        assertEquals(LocalInvestFlow.SIDE_SELL, side)
    }

    @Test
    fun `两端都不在品种账户上时不计入`() {
        // 其它两个账户之间互转：跟这个品种无关
        assertNull(investSideOf(positionAccountId = 7L, accountId = 1L, toAccountId = 2L))
        // 支出/收入压根没有转入账户，且 syncTransfer 会先按 type 拦掉，走不到方向判定
        assertNull(investSideOf(positionAccountId = 7L, accountId = 2L, toAccountId = null))
    }

    @Test
    fun `两个投资账户互转时对目标账户的品种算买入`() {
        // 券商 A 转到券商 B，对 B 名下的品种是买入（拿钱买了 B 里的仓位）
        val side = investSideOf(positionAccountId = 8L, accountId = 7L, toAccountId = 8L)
        assertEquals(LocalInvestFlow.SIDE_BUY, side)
    }

    /* ==================== 市值与盈亏口径 ==================== */

    @Test
    fun `未估值的品种按成本计入市值`() {
        val position = position(costFen = 50_000_00L, valueFen = null, valuedAt = null)
        // 按 0 计的话，刚建完品种总市值会凭空塌下去一截
        assertEquals(50_000_00L, position.marketValueFen)
        assertEquals(0L, position.profitFen)
        assertEquals(0.0, position.profitRate!!, 1e-9)
    }

    @Test
    fun `估值后盈亏按市值减成本`() {
        val position = position(costFen = 50_000_00L, valueFen = 62_300_00L, valuedAt = 1L)
        assertEquals(62_300_00L, position.marketValueFen)
        assertEquals(12_300_00L, position.profitFen)
        assertEquals(0.246, position.profitRate!!, 1e-6)
    }

    @Test
    fun `没有成本时收益率为 null 而不是 0`() {
        // 0% 会被读成「投了钱没赚」，null 才是「没投」
        val position = position(costFen = 0L, valueFen = 1_000_00L, valuedAt = 1L)
        assertNull(position.profitRate)
    }

    @Test
    fun `亏损时盈亏为负`() {
        val position = position(costFen = 50_000_00L, valueFen = 40_000_00L, valuedAt = 1L)
        assertEquals(-10_000_00L, position.profitFen)
        assertEquals(-0.2, position.profitRate!!, 1e-6)
    }

    private fun position(costFen: Long, valueFen: Long?, valuedAt: Long?) = PositionWithValue(
        id = 1L,
        bookId = 1L,
        accountId = 7L,
        name = "沪深300ETF",
        code = "510300",
        kind = 2,
        costFen = costFen,
        sharesScaled = null,
        targetPercent = 0,
        sort = 0,
        note = null,
        archived = false,
        valueFen = valueFen,
        valuedAt = valuedAt
    )
}
