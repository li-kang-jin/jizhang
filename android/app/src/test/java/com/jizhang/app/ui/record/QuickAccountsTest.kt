package com.jizhang.app.ui.record

import com.jizhang.app.data.local.entity.LocalAccount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 记账页「三个常用账户」的纯逻辑测试。
 * <p>
 * 这段规则错了不会崩，只会让芯片行里挂着几个一次没用过的账户（用户以为「常用」是乱猜的），
 * 所以把「谁有资格占位」「怎么排」「选中的冷门账户怎么办」这三件事用单测钉死。
 */
class QuickAccountsTest {

    private fun account(id: Long, name: String, archived: Boolean = false) = LocalAccount(
        id = id,
        bookId = 1L,
        name = name,
        type = 2,
        archived = archived
    )

    private val cash = account(1, "现金")
    private val wechat = account(2, "微信")
    private val alipay = account(3, "支付宝")
    private val bankCard = account(4, "招行卡")

    private fun ids(accounts: List<LocalAccount>) = accounts.map { it.id }

    @Test
    fun `没记过账的账户不占坑位`() {
        val accounts = listOf(cash, wechat, alipay, bankCard)
        val usage = mapOf(wechat.id to 50L, alipay.id to 40L)

        // 只有两个账户真用过，就只铺两个：不能拿「现金 / 招行卡」把第三格补满
        assertEquals(listOf(wechat.id, alipay.id), ids(pickQuickAccounts(accounts, usage, null, 3)))
    }

    @Test
    fun `用得多的在前 次数相同保持手工顺序`() {
        val accounts = listOf(bankCard, wechat, alipay, cash)
        val usage = mapOf(wechat.id to 30L, alipay.id to 30L, bankCard.id to 12L)

        // 微信与支付宝次数相同，按账户管理里的顺序（微信在前）决胜，不看 id 大小
        assertEquals(
            listOf(wechat.id, alipay.id, bankCard.id),
            ids(pickQuickAccounts(accounts, usage, null, 3))
        )
    }

    @Test
    fun `选中的冷门账户顶掉最后一名`() {
        val accounts = listOf(wechat, alipay, bankCard, cash)
        val usage = mapOf(wechat.id to 50L, alipay.id to 40L, bankCard.id to 10L)

        // 从「更多」里选了现金：芯片行里必须看得见它，但只让位最后一个，不能挤掉前两名
        assertEquals(
            listOf(wechat.id, alipay.id, cash.id),
            ids(pickQuickAccounts(accounts, usage, cash.id, 3))
        )
    }

    @Test
    fun `一笔都没用过时退回手工顺序`() {
        val accounts = listOf(cash, wechat, alipay, bankCard)

        // 新账本 / 导入还没记：至少给三个可选，行为与改动前一致
        assertEquals(
            listOf(cash.id, wechat.id, alipay.id),
            ids(pickQuickAccounts(accounts, emptyMap(), null, 3))
        )
    }

    @Test
    fun `归档账户不占坑位`() {
        val archived = account(5, "旧工资卡", archived = true)
        val accounts = listOf(archived, wechat, alipay)
        val usage = mapOf(archived.id to 99L, wechat.id to 5L, alipay.id to 4L)

        // 归档账户历史次数再多也不再铺出来：用户已经停用了它
        assertEquals(listOf(wechat.id, alipay.id), ids(pickQuickAccounts(accounts, usage, null, 3)))
    }

    @Test
    fun `编辑旧账时归档账户仍然可见`() {
        val archived = account(5, "旧工资卡", archived = true)
        val accounts = listOf(wechat, alipay, archived)

        // 这笔账本来记在归档账户上，芯片行藏掉它会让用户以为账户被换掉了
        val shown = pickQuickAccounts(accounts, mapOf(wechat.id to 8L, alipay.id to 7L), archived.id, 3)
        assertTrue(shown.any { it.id == archived.id })
    }

    @Test
    fun `没有账户时返回空列表`() {
        assertEquals(emptyList<LocalAccount>(), pickQuickAccounts(emptyList(), emptyMap(), null, 3))
    }
}
