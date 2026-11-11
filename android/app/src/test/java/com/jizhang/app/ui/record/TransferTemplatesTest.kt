package com.jizhang.app.ui.record

import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalTransaction
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 「常用转账」组合的挑选规则（纯函数）。
 * <p>
 * 这块出错的表现很隐蔽：芯片点了会填进一个已归档账户，或者顺序永远不是常用的那几个，
 * 用户会以为「常用转账」只是个摆设。所以把频次、并列、脏数据三条都钉住。
 */
class TransferTemplatesTest {

    private fun account(id: Long, name: String, archived: Boolean = false) = LocalAccount(
        id = id,
        bookId = 1L,
        name = name,
        type = 2,
        archived = archived
    )

    private fun transfer(id: Long, from: Long, to: Long, time: Long) = LocalTransaction(
        id = id,
        bookId = 1L,
        type = 3,
        amount = 10_000L,
        accountId = from,
        toAccountId = to,
        tradeTime = time
    )

    private val bank = account(1, "招行储蓄卡")
    private val wechat = account(2, "微信零钱")
    private val alipay = account(3, "支付宝")

    @Test
    fun `用得多的组合排前面`() {
        // 输入按时间倒序，和 DAO 的口径一致
        val rows = listOf(
            transfer(5, bank.id, wechat.id, 500),
            transfer(4, alipay.id, wechat.id, 400),
            transfer(3, bank.id, wechat.id, 300),
            transfer(2, alipay.id, wechat.id, 200),
            transfer(1, bank.id, wechat.id, 100)
        )
        val result = pickTransferTemplates(rows, listOf(bank, wechat, alipay), limit = 4)
        assertEquals(listOf(bank.id to wechat.id, alipay.id to wechat.id), result.map { it.fromId to it.toId })
        assertEquals("招行储蓄卡", result.first().fromName)
        assertEquals("微信零钱", result.first().toName)
    }

    @Test
    fun `次数相同取最近一次更近的`() {
        val rows = listOf(
            transfer(2, alipay.id, wechat.id, 900),
            transfer(1, bank.id, wechat.id, 100)
        )
        val result = pickTransferTemplates(rows, listOf(bank, wechat, alipay), limit = 4)
        assertEquals(alipay.id to wechat.id, result.first().fromId to result.first().toId)
    }

    @Test
    fun `归档或不存在的账户组合直接跳过`() {
        val archived = account(9, "注销的卡", archived = true)
        val rows = listOf(
            transfer(3, archived.id, wechat.id, 300),
            transfer(2, 404L, wechat.id, 200),
            transfer(1, bank.id, wechat.id, 100)
        )
        val result = pickTransferTemplates(rows, listOf(bank, wechat, archived), limit = 4)
        assertEquals(listOf(bank.id to wechat.id), result.map { it.fromId to it.toId })
    }

    @Test
    fun `自己转给自己不算组合`() {
        val rows = listOf(transfer(1, bank.id, bank.id, 100))
        assertEquals(emptyList<TransferTemplate>(), pickTransferTemplates(rows, listOf(bank), limit = 4))
    }

    @Test
    fun `limit 生效且空输入不炸`() {
        val rows = listOf(
            transfer(3, bank.id, wechat.id, 300),
            transfer(2, alipay.id, wechat.id, 200),
            transfer(1, wechat.id, alipay.id, 100)
        )
        assertEquals(2, pickTransferTemplates(rows, listOf(bank, wechat, alipay), limit = 2).size)
        assertEquals(emptyList<TransferTemplate>(), pickTransferTemplates(emptyList(), listOf(bank), limit = 4))
    }
}
