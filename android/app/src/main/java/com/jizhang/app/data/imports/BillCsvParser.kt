package com.jizhang.app.data.imports

import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

/** 解析成功的一行账单 */
data class ParsedBill(
    /** 在 CSV 中的行号（从 1 开始，含表头行） */
    val line: Int,
    /** 交易时间（epoch millis） */
    val tradeTime: Long,
    /** 1=支出 2=收入 3=转账 */
    val type: Int,
    /** 金额（分），恒为正 */
    val amount: Long,
    /** 一级分类名，如「餐饮」 */
    val categoryName: String?,
    /** 二级分类名，如「水果」 */
    val subCategoryName: String?,
    /** 收支账户名，如「微信钱包」 */
    val accountName: String?,
    val remark: String?,
    val tags: List<String>,
    val address: String?,
    /** 转账的对方账户名，如「腾讯理财通基金」 */
    val toAccountName: String?
)

/** 被跳过的行及原因 */
data class SkippedBill(val line: Int, val reason: String)

data class CsvParseResult(
    val bills: List<ParsedBill>,
    val skipped: List<SkippedBill>,
    /** 实际使用的编码名 */
    val encoding: String,
    /** 识别出的表头（无表头时为标准模板表头） */
    val headers: List<String>
) {
    val total: Int get() = bills.size + skipped.size
}

/** CSV 中的一条记录（一行，含引号内的换行会被合并到同一条） */
private data class CsvRecord(val line: Int, val cells: List<String>)

/** 各逻辑字段在表头中的列下标，-1 表示该列不存在 */
private data class ColumnIndex(
    val date: Int,
    val type: Int,
    val amount: Int,
    val category: Int,
    val subCategory: Int,
    val account: Int,
    val remark: Int,
    val tag: Int,
    val address: Int,
    val toAccount: Int
)

/**
 * 第三方记账 App 导出的账单 CSV 解析器（纯 Kotlin，不依赖 Android，便于单测）。
 *
 * 目标格式为 12 列模板：
 * `日期, 收支类型, 金额, 优惠, 类别, 二级分类, 所属账本, 收支账户, 备注, 标签, 地址, 转入账户`
 *
 * 解析策略：
 * 1. 自动识别编码（UTF-8 / UTF-8 BOM / GBK）；
 * 2. 优先按**表头名称**定位列（列顺序变化也能解析），表头缺失时按标准 12 列顺序兜底；
 * 3. 金额、日期做容错解析，坏行记入 [CsvParseResult.skipped] 而不是整份文件失败。
 */
object BillCsvParser {

    /** 标准 12 列表头 */
    private val DEFAULT_HEADERS = listOf(
        "日期", "收支类型", "金额", "优惠", "类别", "二级分类",
        "所属账本", "收支账户", "备注", "标签", "地址", "转入账户"
    )

    /** 未提供时间时使用的小时（取中午，避免时区换算后跨日） */
    private const val DEFAULT_HOUR = 12

    // 注意：日期与时间之间的空白由 `[ T]+` 自己吃掉，前面不能再写 `\s*`，
    // 否则 `\s*` 会贪婪吞掉空格，导致时间部分永远匹配不上。
    private val DATE_REGEX = Regex(
        "(\\d{4})\\s*[/\\-.年]\\s*(\\d{1,2})\\s*[/\\-.月]\\s*(\\d{1,2})日?" +
            "(?:[ T]+(\\d{1,2})\\s*[:：时]\\s*(\\d{1,2})(?:\\s*[:：分]\\s*(\\d{1,2}))?)?"
    )

    /** 多标签分隔符 */
    private val TAG_SEPARATOR = Regex("[,，;；、|/]+")

    private val DATE_ALIASES = listOf("日期", "交易日期", "交易时间", "时间", "date", "time")
    private val TYPE_ALIASES = listOf("收支类型", "收支", "类型", "type")
    private val AMOUNT_ALIASES = listOf("金额", "交易金额", "money", "amount")
    private val CATEGORY_ALIASES = listOf("类别", "分类", "一级分类", "category")
    private val SUB_CATEGORY_ALIASES = listOf("二级分类", "子分类", "subcategory")
    private val ACCOUNT_ALIASES = listOf("收支账户", "账户", "支付账户", "account")
    private val REMARK_ALIASES = listOf("备注", "说明", "remark", "note")
    private val TAG_ALIASES = listOf("标签", "tag", "tags")
    private val ADDRESS_ALIASES = listOf("地址", "位置", "address")
    private val TO_ACCOUNT_ALIASES = listOf("转入账户", "转入", "对方账户", "toaccount")

    /** 从文件的原始字节解析（自动识别编码） */
    fun parse(bytes: ByteArray): CsvParseResult {
        val (text, encoding) = decode(bytes)
        return parseText(text, encoding)
    }

    /** 从文本解析 */
    fun parseText(text: String, encoding: String = "UTF-8"): CsvParseResult {
        val records = parseCsv(text).filter { record -> record.cells.any { it.isNotBlank() } }
        if (records.isEmpty()) {
            return CsvParseResult(emptyList(), emptyList(), encoding, DEFAULT_HEADERS)
        }

        val first = records.first()
        val hasHeader = looksLikeHeader(first.cells)
        val headers = if (hasHeader) first.cells.map { it.trim() } else DEFAULT_HEADERS
        val dataRecords = if (hasHeader) records.drop(1) else records
        val index = buildColumnIndex(headers)

        val bills = mutableListOf<ParsedBill>()
        val skipped = mutableListOf<SkippedBill>()
        dataRecords.forEach { record ->
            runCatching { parseRow(record, index) }
                .onSuccess { bills += it }
                .onFailure { error ->
                    skipped += SkippedBill(record.line, error.message ?: "解析失败")
                }
        }
        return CsvParseResult(bills, skipped, encoding, headers)
    }

    /* ---------------- 编码识别 ---------------- */

    private fun decode(bytes: ByteArray): Pair<String, String> {
        if (bytes.size >= 3 &&
            bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()
        ) {
            return String(bytes, 3, bytes.size - 3, StandardCharsets.UTF_8) to "UTF-8"
        }
        return runCatching {
            val decoder = StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
            decoder.decode(ByteBuffer.wrap(bytes)).toString() to "UTF-8"
        }.getOrElse {
            // 国内导出的账单常见 GBK，UTF-8 严格解码失败时回退
            String(bytes, Charset.forName("GBK")) to "GBK"
        }
    }

    /* ---------------- CSV 词法解析 ---------------- */

    private fun parseCsv(text: String): List<CsvRecord> {
        val records = mutableListOf<CsvRecord>()
        var cells = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var line = 1
        var recordStart = 1
        var i = 0

        fun endCell() {
            cells.add(cell.toString())
            cell.clear()
        }

        fun endRecord() {
            endCell()
            records.add(CsvRecord(recordStart, cells))
            cells = mutableListOf()
            recordStart = line
        }

        while (i < text.length) {
            val c = text[i]
            when {
                quoted -> when {
                    c == '"' && i + 1 < text.length && text[i + 1] == '"' -> {
                        cell.append('"')
                        i++
                    }
                    c == '"' -> quoted = false
                    else -> {
                        if (c == '\n') line++
                        cell.append(c)
                    }
                }

                c == '"' -> quoted = true
                c == ',' -> endCell()
                c == '\r' -> Unit
                c == '\n' -> {
                    line++
                    endRecord()
                }

                else -> cell.append(c)
            }
            i++
        }
        if (cell.isNotEmpty() || cells.isNotEmpty()) endRecord()
        return records
    }

    /* ---------------- 表头识别与列定位 ---------------- */

    private fun looksLikeHeader(cells: List<String>): Boolean {
        val joined = cells.joinToString("")
        if (joined.isBlank()) return false
        val hasHeaderWord = joined.contains("日期") || joined.contains("金额") || joined.contains("收支")
        // 数据行一定含数字日期，表头不会
        return hasHeaderWord && !DATE_REGEX.containsMatchIn(joined)
    }

    private fun buildColumnIndex(headers: List<String>): ColumnIndex {
        val normalized = headers.map { it.trim().lowercase() }
        val allowPositional = headers.size == DEFAULT_HEADERS.size

        fun find(aliases: List<String>, vararg positions: Int): Int {
            val byName = normalized.indexOfFirst { h ->
                h.isNotEmpty() && aliases.any { it.lowercase() == h }
            }
            if (byName >= 0) return byName
            if (!allowPositional) return -1
            return positions.firstOrNull() ?: -1
        }

        return ColumnIndex(
            date = find(DATE_ALIASES, 0),
            type = find(TYPE_ALIASES, 1),
            amount = find(AMOUNT_ALIASES, 2),
            category = find(CATEGORY_ALIASES, 4),
            subCategory = find(SUB_CATEGORY_ALIASES, 5),
            account = find(ACCOUNT_ALIASES, 7),
            remark = find(REMARK_ALIASES, 8),
            tag = find(TAG_ALIASES, 9),
            address = find(ADDRESS_ALIASES, 10),
            toAccount = find(TO_ACCOUNT_ALIASES, 11)
        )
    }

    /* ---------------- 单行解析 ---------------- */

    private fun parseRow(record: CsvRecord, index: ColumnIndex): ParsedBill {
        val cells = record.cells
        fun cell(at: Int): String = if (at < 0) "" else cells.getOrNull(at)?.trim().orEmpty()

        val rawDate = cell(index.date)
        val tradeTime = parseDateToMillis(rawDate) ?: error("日期无法识别：$rawDate")

        val rawAmount = cell(index.amount)
        val rawFen = parseAmountToFen(rawAmount) ?: error("金额无法识别：$rawAmount")
        val amount = abs(rawFen)
        require(amount > 0) { "金额无效：$rawAmount" }

        val rawCategory = cell(index.category)
        val type = parseType(cell(index.type), rawCategory, rawFen)
            ?: error("收支类型无法识别：${cell(index.type)}")

        val subCategory = cell(index.subCategory).ifBlank { null }
        // 转账时类别列往往是「转账」这类占位词，不作为业务分类
        val category = rawCategory
            .takeIf { it.isNotBlank() && !it.contains("转账") && !it.contains("转帐") }

        return ParsedBill(
            line = record.line,
            tradeTime = tradeTime,
            type = type,
            amount = amount,
            categoryName = category,
            subCategoryName = subCategory,
            accountName = cell(index.account).ifBlank { null },
            remark = cell(index.remark).ifBlank { null },
            tags = parseTags(cell(index.tag)),
            address = cell(index.address).ifBlank { null },
            toAccountName = cell(index.toAccount).ifBlank { null }
        )
    }

    /** 金额（元）→ 分，容错货币符号与千分位 */
    internal fun parseAmountToFen(raw: String): Long? {
        val cleaned = raw.trim()
            .replace(",", "")
            .replace("¥", "")
            .replace("￥", "")
            .replace("元", "")
            .replace(" ", "")
            .removePrefix("+")
        if (cleaned.isEmpty()) return null
        return runCatching {
            BigDecimal(cleaned)
                .setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2)
                .longValueExact()
        }.getOrNull()
    }

    /** 日期文本 → epoch millis，支持 `2026/10/14`、`2026-10-14 12:30`、`2026年10月14日` 等 */
    internal fun parseDateToMillis(raw: String): Long? {
        val match = DATE_REGEX.find(raw.trim()) ?: return null
        val groups = match.groupValues
        val year = groups[1].toIntOrNull() ?: return null
        val month = groups[2].toIntOrNull() ?: return null
        val day = groups[3].toIntOrNull() ?: return null
        val hour = groups[4].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: DEFAULT_HOUR
        val minute = groups[5].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        val second = groups[6].takeIf { it.isNotEmpty() }?.toIntOrNull() ?: 0
        return runCatching {
            LocalDateTime.of(year, month, day, hour, minute, second)
                .atZone(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        }.getOrNull()
    }

    /** 收支类型文本 → 1=支出 2=收入 3=转账 */
    internal fun parseType(raw: String, categoryName: String?, rawFen: Long?): Int? {
        val text = raw.trim()
        return when {
            text.contains("支出") || text.contains("消费") -> 1
            text.contains("收入") || text.contains("收款") -> 2
            text.contains("转账") || text.contains("转帐") -> 3
            categoryName?.contains("转账") == true || categoryName?.contains("转帐") == true -> 3
            rawFen != null && rawFen < 0 -> 1
            rawFen != null && rawFen > 0 -> 2
            else -> null
        }
    }

    /** 标签文本 → 标签列表 */
    internal fun parseTags(raw: String): List<String> =
        raw.split(TAG_SEPARATOR).map { it.trim() }.filter { it.isNotEmpty() }
}
