package com.jizhang.app.data.repository

import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.PositionWithValue
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 投资汇总口径的守门测试。
 * <p>
 * 这几个数之间的关系是投资页 / 统计页 / 账户页能对上数字的唯一依据：
 * `本金 + 浮动 = 市值 + 未分配`。任何一处把它破坏掉，用户就会看到
 * 「账户页 5000、统计页 4500」这种对不上的情况。
 */
class InvestmentSummaryTest {

    @Test
    fun `本金加浮动等于市值加未分配`() {
        val summary = summarizeInvest(
            positions = listOf(position(cost = 5000_00L, value = 4500_00L)),
            investAccounts = listOf(account(balance = 5000_00L)),
            lastValuedAt = 1L
        )
        assertEquals(4500_00L, summary.marketValueFen)
        assertEquals(5000_00L, summary.costFen)
        assertEquals(5000_00L, summary.principalFen)
        assertEquals(0L, summary.unallocatedFen)
        assertEquals(-500_00L, summary.profitFen)
        // 恒等式：左侧是账户视角，右侧是持仓视角，两者必须相等
        assertEquals(summary.marketValueFen + summary.unallocatedFen, summary.assetFen)
        assertEquals(4500_00L, summary.assetFen)
    }

    @Test
    fun `转进来还没买成品种时未分配为正且资产包含现金`() {
        val summary = summarizeInvest(
            positions = listOf(position(cost = 6000_00L, value = 6000_00L)),
            investAccounts = listOf(account(balance = 10000_00L)),
            lastValuedAt = null
        )
        assertEquals(4000_00L, summary.unallocatedFen)
        // 直接拿市值当资产会吞掉这 4000
        assertEquals(10000_00L, summary.assetFen)
    }

    @Test
    fun `成本填多了时未分配为负且不虚增资产`() {
        // 手工建仓、没有对应转账：账户里一分钱没有，但成本填了 5000。
        // 这时账面只体现浮亏，不能凭空多出 4500 的身家。
        val summary = summarizeInvest(
            positions = listOf(position(cost = 5000_00L, value = 4500_00L)),
            investAccounts = listOf(account(balance = 0L)),
            lastValuedAt = 1L
        )
        // 未分配 = 本金 0 − 成本 5000 = −5000（这一整笔钱都没在账上出现过）
        assertEquals(-5000_00L, summary.unallocatedFen)
        // 资产 = 本金 0 + 浮动 −500，只体现浮亏，不虚增 4500
        assertEquals(-500_00L, summary.assetFen)
    }

    @Test
    fun `从未估值时按成本计市值`() {
        val summary = summarizeInvest(
            positions = listOf(position(cost = 5000_00L, value = null)),
            investAccounts = listOf(account(balance = 5000_00L)),
            lastValuedAt = null
        )
        assertEquals(5000_00L, summary.marketValueFen)
        assertEquals(0L, summary.profitFen)
        assertEquals(5000_00L, summary.assetFen)
    }

    @Test
    fun `没有成本时收益率为 null 而不是零`() {
        val summary = summarizeInvest(
            positions = listOf(position(cost = 0L, value = 1000_00L)),
            investAccounts = listOf(account(balance = 0L)),
            lastValuedAt = 1L
        )
        assertEquals(1000_00L, summary.profitFen)
        assertEquals(null, summary.profitRate)
    }

    private fun position(cost: Long, value: Long?) = PositionWithValue(
        id = 1L,
        bookId = 1L,
        accountId = 7L,
        name = "腾讯基金",
        code = null,
        kind = 2,
        costFen = cost,
        sharesScaled = null,
        targetPercent = 0,
        sort = 0,
        note = null,
        archived = false,
        valueFen = value,
        valuedAt = value?.let { 1L }
    )

    private fun account(balance: Long) = AccountWithBalance(
        id = 7L,
        bookId = 1L,
        name = "腾讯理财通基金",
        type = 6,
        initialBalance = 0L,
        icon = null,
        sort = 0,
        balance = balance
    )
}
