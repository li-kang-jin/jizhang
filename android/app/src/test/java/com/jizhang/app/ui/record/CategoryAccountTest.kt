package com.jizhang.app.ui.record

import com.jizhang.app.data.local.entity.LocalAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 记账页「分类 → 账户」默认值的纯逻辑测试。
 * <p>
 * 这条规则决定的是「打开记账页时钱默认从哪个账户出」：判错了不会崩，
 * 但会让人每笔都改一次账户，而改漏的那一次就是余额记错的开始。
 * 所以「几次才算习惯」「次数相同听谁的」「归档账户怎么办」都钉死。
 */
class CategoryAccountTest {

    private fun account(id: Long, name: String, archived: Boolean = false) = LocalAccount(
        id = id,
        bookId = 1L,
        name = name,
        type = 5,
        archived = archived
    )

    private val wechat = account(1, "微信")
    private val alipay = account(2, "支付宝")
    private val cash = account(3, "现金")

    @Test
    fun `只记过一次的账户不当默认`() {
        // 一次巧合可能正是那次记错账：拿它当默认等于把错误固化成习惯
        assertNull(pickAccountForCategory(listOf(wechat, alipay), mapOf(wechat.id to 1L)))
    }

    @Test
    fun `够次数时取用得最多的账户`() {
        val usage = mapOf(alipay.id to 7L, wechat.id to 2L)
        assertEquals(alipay.id, pickAccountForCategory(listOf(wechat, alipay), usage))
    }

    @Test
    fun `次数相同时按账户管理里的顺序`() {
        // 与默认分类同一套决胜规则：次数打平就听手工顺序（列表里靠前的那个）
        val usage = mapOf(wechat.id to 3L, alipay.id to 3L, cash.id to 3L)
        assertEquals(wechat.id, pickAccountForCategory(listOf(wechat, alipay, cash), usage))
    }

    @Test
    fun `归档账户不当默认`() {
        // 历史次数再多也不能选中已停用的账户：用户已经把它收起来了
        val archived = account(4, "旧工资卡", archived = true)
        val usage = mapOf(archived.id to 99L, wechat.id to 2L)
        assertEquals(wechat.id, pickAccountForCategory(listOf(archived, wechat), usage))
    }

    @Test
    fun `完全没有历史时返回空`() {
        assertNull(pickAccountForCategory(listOf(wechat, alipay), emptyMap()))
        assertNull(pickAccountForCategory(emptyList(), mapOf(wechat.id to 5L)))
    }
}
