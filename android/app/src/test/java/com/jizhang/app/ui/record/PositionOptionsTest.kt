package com.jizhang.app.ui.record

import com.jizhang.app.data.local.entity.LocalAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 「转账能挂谁的品种」的纯逻辑测试。
 * <p>
 * 这条规则只影响「卖出侧能不能选到品种」：判错不会崩，但会让人从投资账户转出后
 * 只减本金、不减成本，投资页长期挂一条「本金比成本少」。所以两个方向都钉死。
 */
class PositionOptionsTest {

    private fun account(id: Long, name: String, type: Int) = LocalAccount(
        id = id,
        bookId = 1L,
        name = name,
        type = type
    )

    private val fundAccount = account(1, "腾讯理财通", type = 6)
    private val wechat = account(2, "微信", type = 5)
    private val savings = account(3, "招行卡", type = 2)

    private fun state(
        type: Int = 3,
        from: Long? = null,
        to: Long? = null
    ) = RecordViewModel.UiState(
        type = type,
        accounts = listOf(fundAccount, wechat, savings),
        selectedAccountId = from,
        selectedToAccountId = to
    )

    @Test
    fun `转入投资账户 用转入账户的品种`() {
        assertEquals(fundAccount.id, state(from = savings.id, to = fundAccount.id).positionOptionsAccountId())
    }

    @Test
    fun `转出投资账户 用转出账户的品种`() {
        // 赎回：基金 → 微信。只认转入的话这里会是 null，卖出就没处挂
        assertEquals(fundAccount.id, state(from = fundAccount.id, to = wechat.id).positionOptionsAccountId())
    }

    @Test
    fun `两端都是投资账户 以转入为准`() {
        val other = account(4, "天天基金", type = 6)
        val s = state(from = fundAccount.id, to = other.id)
            .copy(accounts = listOf(fundAccount, other))
        assertEquals(other.id, s.positionOptionsAccountId())
    }

    @Test
    fun `两端都不是投资账户 没有品种可选`() {
        assertNull(state(from = savings.id, to = wechat.id).positionOptionsAccountId())
    }

    @Test
    fun `不是转账时不挂品种`() {
        assertNull(state(type = 1, from = fundAccount.id, to = null).positionOptionsAccountId())
    }
}
