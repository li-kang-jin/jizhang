package com.jizhang.app.ui.record

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记账页「余额预览 / 余额不足」的纯逻辑测试。
 * <p>
 * 这几个函数决定了记账时能不能当场发现「选错账户」：符号反了会把「钱不够」显示成「钱够」，
 * 把信用卡算进「不足」则会对每一笔正常刷卡都喊狼来了。所以符号与豁免规则都钉在单测里。
 */
class RecordBalanceTest {

    private val expense = 1
    private val income = 2
    private val transfer = 3

    @Test
    fun `支出减少余额 收入增加余额`() {
        assertEquals(-2000L, amountDelta(expense, 2000L))
        assertEquals(2000L, amountDelta(income, 2000L))
    }

    @Test
    fun `转账对转出方是减 对转入方是加`() {
        // 同一笔转账两端口径相反：方向搞反等于把两个账户的余额都算错
        assertEquals(-5000L, amountDelta(transfer, 5000L, isToAccount = false))
        assertEquals(5000L, amountDelta(transfer, 5000L, isToAccount = true))
    }

    @Test
    fun `没填金额时不给预览`() {
        // 金额为空或 0 时不显示「¥12 → ¥12」这种噪音，也不该把 0 当成一笔账
        assertNull(amountDelta(expense, null))
        assertNull(amountDelta(expense, 0L))
    }

    @Test
    fun `记完余额等于当前余额加上这笔的影响`() {
        assertEquals(8000L, balanceAfter(10000L, expense, 2000L))
        assertEquals(12000L, balanceAfter(10000L, income, 2000L))
        // 余额未知（查询失败 / 老数据）时不猜数字
        assertNull(balanceAfter(null, expense, 2000L))
        assertNull(balanceAfter(10000L, expense, null))
    }

    @Test
    fun `余额被刷成负数就是余额不足`() {
        // 微信只剩 12 块，这笔 200：正是「选错账户」最典型的样子
        assertTrue(isBalanceShort(accountType = 5, afterFen = -18800L))
        // 刚好花光不算不足
        assertFalse(isBalanceShort(accountType = 5, afterFen = 0L))
    }

    @Test
    fun `信用卡刷成负余额不算余额不足`() {
        // 信用卡的负余额就是欠款，刷卡让它更负属于正常语义，不能提示「余额不足」
        assertFalse(isBalanceShort(ACCOUNT_TYPE_CREDIT_CARD, -18800L))
        // 其它类型同一金额就该提示
        assertTrue(isBalanceShort(accountType = 2, afterFen = -18800L))
    }

    @Test
    fun `余额未知时不判断不足`() {
        assertFalse(isBalanceShort(accountType = 1, afterFen = null))
    }
}
