package com.jizhang.app.data.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.charset.Charset
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 解析器测试。
 * <p>
 * 样例数据取自第三方记账 App 的真实导出格式（12 列）。
 */
class BillCsvParserTest {

    /** 与参考格式一致的真实样例（各行字段数与表头对齐） */
    private val sample = """
        日期,收支类型,金额,优惠,类别,二级分类,所属账本,收支账户,备注,标签,地址,转入账户
        2026/10/14,支出,14.39,,餐饮,,,微信钱包,晚餐
        2026/10/14,支出,20.36,,餐饮,,,微信钱包,午餐
        2026/10/14,转账,200,,转账,,,微信钱包,购买白酒基金,,,腾讯理财通基金
        2026/10/14,支出,4,,交通,,,公交卡,地铁
        2026/10/14,支出,3.71,,交通,,,微信钱包,电驴充电。
        2026/10/13,支出,23,,餐饮,,,微信钱包,夜宵
        2026/10/13,支出,6.3,,水果,,,微信钱包
        2026/10/13,支出,4,,交通,,,公交卡,地铁
        2026/10/13,支出,2,,交通,,,公交卡,地铁
        2026/10/13,支出,10.8,,餐饮,,,微信钱包,借款
        2026/10/13,转账,200,,转账,,,微信钱包,借款,别人还钱,,微信钱包
        2026/10/13,支出,18.5,,餐饮,,,微信钱包,午餐
        2026/10/13,支出,4,,交通,,,公交卡,地铁
        2026/10/12,支出,3,,交通,,,公交卡,地铁
        2026/10/12,支出,3,,交通,,,公交卡,地铁
        2026/10/12,支出,6.5,,餐饮,,,微信钱包,饮料🥤
    """.trimIndent()

    private fun bills(): List<ParsedBill> = BillCsvParser.parseText(sample).bills

    private fun localDate(millis: Long): LocalDate =
        Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

    @Test
    fun `解析标准 12 列表头`() {
        val result = BillCsvParser.parseText(sample)

        assertEquals(16, result.bills.size)
        assertTrue(result.skipped.isEmpty())
        assertEquals("UTF-8", result.encoding)
        assertEquals(12, result.headers.size)
    }

    @Test
    fun `金额按分存储且日期落到当天`() {
        val bill = bills().first()

        assertEquals(1439L, bill.amount)
        assertEquals(1, bill.type)
        assertEquals(LocalDate.of(2026, 10, 14), localDate(bill.tradeTime))
        assertEquals("餐饮", bill.categoryName)
        assertEquals("微信钱包", bill.accountName)
        assertEquals("晚餐", bill.remark)
    }

    @Test
    fun `转账行带转入账户且不带业务分类`() {
        val transfer = bills().first { it.type == 3 }

        assertEquals(20000L, transfer.amount)
        assertEquals("微信钱包", transfer.accountName)
        assertEquals("腾讯理财通基金", transfer.toAccountName)
        assertNull(transfer.categoryName)
    }

    @Test
    fun `标签列被拆分保留`() {
        val tagged = bills().first { it.tags.isNotEmpty() }

        assertEquals(listOf("别人还钱"), tagged.tags)
        assertEquals(3, tagged.type)
        assertEquals("微信钱包", tagged.toAccountName)
    }

    @Test
    fun `列顺序变化时按表头名解析`() {
        val text = """
            金额,日期,收支账户,收支类型,备注
            35.5,2026-10-01,现金,支出,打车
        """.trimIndent()

        val bill = BillCsvParser.parseText(text).bills.single()

        assertEquals(3550L, bill.amount)
        assertEquals(LocalDate.of(2026, 10, 1), localDate(bill.tradeTime))
        assertEquals("现金", bill.accountName)
        assertEquals(1, bill.type)
        assertEquals("打车", bill.remark)
    }

    @Test
    fun `GBK 编码文件可解析`() {
        val text = "日期,收支类型,金额,类别,收支账户,备注\n" +
            "2026/10/14,支出,10,餐饮,微信钱包,早餐"
        val result = BillCsvParser.parse(bytes = text.toByteArray(Charset.forName("GBK")))

        assertEquals("GBK", result.encoding)
        assertEquals(1, result.bills.size)
        assertEquals("早餐", result.bills.single().remark)
    }

    @Test
    fun `非法金额的行被跳过而非整份失败`() {
        val text = """
            日期,收支类型,金额,收支账户,备注
            2026/10/14,支出,abc,微信钱包,坏行
            2026/10/14,支出,12.5,微信钱包,好行
        """.trimIndent()

        val result = BillCsvParser.parseText(text)

        assertEquals(1, result.bills.size)
        assertEquals(1, result.skipped.size)
        assertEquals(2, result.skipped.single().line)
        assertEquals("好行", result.bills.single().remark)
    }

    @Test
    fun `无表头时按标准 12 列顺序兜底`() {
        val text = "2026/10/14,支出,14.39,,餐饮,,,微信钱包,晚餐"

        val bill = BillCsvParser.parseText(text).bills.single()

        assertEquals(1439L, bill.amount)
        assertEquals("餐饮", bill.categoryName)
        assertEquals("微信钱包", bill.accountName)
        assertEquals("晚餐", bill.remark)
    }

    @Test
    fun `备注中的逗号不会拆列`() {
        val text = """
            日期,收支类型,金额,类别,收支账户,备注
            2026/10/14,支出,30,餐饮,微信钱包,"午餐,加饮料"
        """.trimIndent()

        val bill = BillCsvParser.parseText(text).bills.single()

        assertEquals("午餐,加饮料", bill.remark)
    }

    @Test
    fun `带时间的日期按实际时刻解析`() {
        val text = """
            日期,收支类型,金额,收支账户
            2026-10-14 08:30,支出,10,微信钱包
        """.trimIndent()

        val millis = BillCsvParser.parseText(text).bills.single().tradeTime
        val dateTime = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault())

        assertEquals(LocalDate.of(2026, 10, 14), dateTime.toLocalDate())
        assertEquals(8, dateTime.hour)
        assertEquals(30, dateTime.minute)
    }
}
