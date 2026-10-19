package com.jizhang.app.data.imports

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 导出器测试。
 * <p>
 * 重点是「导出 → 再导入」的往返：写出去的文件必须是解析器认识的标准 12 列格式，
 * 否则用户导出的备份就再也导不回来了。
 */
class BillCsvWriterTest {

    private fun millisOf(year: Int, month: Int, day: Int, hour: Int, minute: Int): Long =
        LocalDateTime.of(year, month, day, hour, minute)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()

    private val rows = listOf(
        BillCsvWriter.Row(
            tradeTime = millisOf(2026, 10, 14, 19, 30),
            type = 1,
            amount = 1439,
            category = "餐饮",
            bookName = "越来越好",
            account = "微信钱包",
            remark = "晚餐",
            tags = "外卖,加班"
        ),
        BillCsvWriter.Row(
            tradeTime = millisOf(2026, 10, 14, 20, 5),
            type = 2,
            amount = 500000,
            category = "工资",
            bookName = "越来越好",
            account = "招商银行",
            remark = "10 月工资"
        ),
        BillCsvWriter.Row(
            tradeTime = millisOf(2026, 10, 13, 9, 0),
            type = 3,
            amount = 20000,
            category = "转账",
            bookName = "越来越好",
            account = "微信钱包",
            remark = "买基金",
            toAccount = "腾讯理财通基金"
        ),
        BillCsvWriter.Row(
            tradeTime = millisOf(2026, 10, 12, 12, 0),
            type = 1,
            amount = 3050,
            category = "餐饮",
            subCategory = "水果",
            account = "现金",
            remark = "苹果\"香蕉\", 梨"
        )
    )

    @Test
    fun `表头是解析器认得的 12 列`() {
        val text = BillCsvWriter.write(rows)

        assertEquals(
            "日期,收支类型,金额,优惠,类别,二级分类,所属账本,收支账户,备注,标签,地址,转入账户",
            text.lineSequence().first()
        )

        val parsed = BillCsvParser.parseText(text)
        assertEquals(12, parsed.headers.size)
        assertEquals(rows.size, parsed.bills.size)
        assertTrue(parsed.skipped.isEmpty())
    }

    @Test
    fun `导出再导入时间与金额分毫不差`() {
        val dinner = BillCsvParser.parseText(BillCsvWriter.write(rows)).bills
            .first { it.remark == "晚餐" }

        assertEquals(1439L, dinner.amount)
        assertEquals(1, dinner.type)
        assertEquals(millisOf(2026, 10, 14, 19, 30), dinner.tradeTime)
        assertEquals("餐饮", dinner.categoryName)
        assertEquals("微信钱包", dinner.accountName)
        assertEquals(listOf("外卖", "加班"), dinner.tags)
    }

    @Test
    fun `转账的类别占位不会变成业务分类`() {
        val transfer = BillCsvParser.parseText(BillCsvWriter.write(rows)).bills
            .first { it.type == 3 }

        assertEquals(20000L, transfer.amount)
        assertEquals("微信钱包", transfer.accountName)
        assertEquals("腾讯理财通基金", transfer.toAccountName)
        assertNull(transfer.categoryName)
    }

    @Test
    fun `备注里的引号与逗号原样往返`() {
        val apple = BillCsvParser.parseText(BillCsvWriter.write(rows)).bills
            .first { it.subCategoryName == "水果" }

        assertEquals("苹果\"香蕉\", 梨", apple.remark)
        assertEquals("餐饮", apple.categoryName)
        assertEquals("水果", apple.subCategoryName)
    }

    @Test
    fun `写入的字节带 BOM 且能被解析`() {
        val bytes = BillCsvWriter.writeBytes(rows)

        // Excel 靠 BOM 认出 UTF-8，没有它中文会乱码
        assertEquals(0xEF, bytes[0].toInt() and 0xFF)
        assertEquals(0xBB, bytes[1].toInt() and 0xFF)
        assertEquals(0xBF, bytes[2].toInt() and 0xFF)

        val parsed = BillCsvParser.parse(bytes)
        assertEquals("UTF-8", parsed.encoding)
        assertEquals(rows.size, parsed.bills.size)
    }

    @Test
    fun `金额写正数且固定两位小数`() {
        val line = BillCsvWriter.write(rows).lineSequence().first { it.contains("晚餐") }

        assertTrue(line.contains(",14.39,"))
    }
}
