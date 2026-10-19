package com.jizhang.app.data.imports

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.StandardCharsets
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * 账单 CSV 导出器（纯 Kotlin，不依赖 Android，便于单测）。
 *
 * 与 [BillCsvParser] 对称：写出的就是解析器认识的标准 12 列格式，
 * 所以「导出 → 再导入」能原样回来；表头是中文，用 Excel / WPS 直接打开也能看。
 *
 * 具体约定：
 * - 金额写正数（正负由「收支类型」列表达），固定两位小数；
 * - 转账行的「类别」写「转账」占位（解析时转账会忽略这一列）；
 * - 字段含逗号 / 引号 / 换行时按 RFC 4180 加引号，备注里写什么都不怕。
 */
object BillCsvWriter {

    /** 标准 12 列表头，与 [BillCsvParser] 的识别规则一一对应 */
    private val HEADERS = listOf(
        "日期", "收支类型", "金额", "优惠", "类别", "二级分类",
        "所属账本", "收支账户", "备注", "标签", "地址", "转入账户"
    )

    /** 文件开头的 UTF-8 BOM：没有它 Excel 打开中文会乱码 */
    private val BOM = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte())

    /** 行尾用 CRLF：Excel 与 Windows 记事本都认 */
    private const val CRLF = "\r\n"

    private val DATE_TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * 导出用的一行。
     * <p>
     * 只带「库里的原始值」（时间戳、分、id 翻译出来的名字），
     * 格式化交给本类统一处理 —— 这样各调用方写出来的文件格式完全一致。
     */
    data class Row(
        /** 交易时间（epoch millis） */
        val tradeTime: Long,
        /** 1=支出 2=收入 3=转账 */
        val type: Int,
        /** 金额（分），恒为正 */
        val amount: Long,
        /** 一级分类名 */
        val category: String? = null,
        /** 二级分类名（挂在父分类下的那层） */
        val subCategory: String? = null,
        /** 所属账本名（只是给人看的，导入时不参与匹配） */
        val bookName: String? = null,
        /** 收支账户名 */
        val account: String? = null,
        val remark: String? = null,
        /** 标签原文，多个用逗号分隔 */
        val tags: String? = null,
        /** 转账的对方账户名 */
        val toAccount: String? = null
    )

    /** 生成 CSV 文本（不含 BOM） */
    fun write(rows: List<Row>): String = buildString {
        append(HEADERS.joinToString(","))
        append(CRLF)
        rows.forEach { row ->
            append(
                listOf(
                    DATE_TIME.format(Instant.ofEpochMilli(row.tradeTime).atZone(ZoneId.systemDefault())),
                    typeLabel(row.type),
                    yuanOf(row.amount),
                    // 优惠：本工程没有这个概念，留空占位以保持 12 列
                    "",
                    row.category.orEmpty(),
                    row.subCategory.orEmpty(),
                    row.bookName.orEmpty(),
                    row.account.orEmpty(),
                    row.remark.orEmpty(),
                    row.tags.orEmpty(),
                    // 地址：导入时已经并进备注，导出不再拆出来
                    "",
                    row.toAccount.orEmpty()
                ).joinToString(",") { escape(it) }
            )
            append(CRLF)
        }
    }

    /** 生成可直接写盘的字节（带 UTF-8 BOM） */
    fun writeBytes(rows: List<Row>): ByteArray = write(rows).toByteArray(StandardCharsets.UTF_8)
        .let { BOM + it }

    /** 分 → 元，固定两位小数（12 → "0.12"） */
    private fun yuanOf(cents: Long): String =
        BigDecimal.valueOf(cents).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

    /** 类型文本：必须是解析器认得的词（支出 / 收入 / 转账） */
    private fun typeLabel(type: Int): String = when (type) {
        2 -> "收入"
        3 -> "转账"
        else -> "支出"
    }

    /**
     * 字段转义：含分隔符 / 引号 / 换行时用双引号包起来，内部引号翻倍（RFC 4180）。
     * 与 [BillCsvParser] 的读取逻辑对应，因此写出去的一定能原样读回来。
     */
    private fun escape(value: String): String =
        if (value.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            '"' + value.replace("\"", "\"\"") + '"'
        } else {
            value
        }
}
