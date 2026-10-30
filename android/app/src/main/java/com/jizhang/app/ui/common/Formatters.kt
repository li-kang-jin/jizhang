package com.jizhang.app.ui.common

import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs
import kotlin.math.roundToInt

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月")

private val YUAN_DISPLAY: DecimalFormat = DecimalFormat("#,##0.00")

/**
 * 分 → 元，**全 App 的展示口径**：千分位、精确到分、角分位补零。
 * <p>
 * 记账列表与统计页统一保留两位小数，整数元也补 `.00`，免得同一笔钱在两个页面写法不一样
 * （`¥1.00` / `¥3.20` / `¥1.22` / `¥12,345.67`）。
 * <p>
 * 注意：这是**展示**用的，别拿它回填输入框 —— 千分位会让 `toDoubleOrNull()` 解析失败，
 * 那种场景用 [formatAmountInput]。
 */
fun formatAmount(fen: Long): String = YUAN_DISPLAY.format(fen / 100.0)

/** 分 → 元，**输入框回填口径**：不带千分位、去掉末尾 0（`1234.56` / `3.2` / `0`） */
fun formatAmountInput(fen: Long): String =
    BigDecimal.valueOf(fen).movePointLeft(2).stripTrailingZeros().toPlainString()

/** 带符号的金额展示：支出为负、收入为正 */
fun formatSignedAmount(type: Int, fen: Long): String {
    val sign = when (type) {
        1 -> "-"
        2 -> "+"
        else -> ""
    }
    return sign + formatAmount(fen)
}

fun formatDate(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate().format(DATE_FORMAT)

fun formatDateTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMAT)

fun formatMonth(yearMonth: YearMonth): String = yearMonth.format(MONTH_FORMAT)

fun currentMonth(): YearMonth = YearMonth.now()

/** 指定月份第一天的 0 点（epoch millis） */
fun monthStartMillis(yearMonth: YearMonth): Long =
    yearMonth.atDay(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 星期短标签。下标 0 = 周日，与 SQLite `strftime('%w')` 的口径一致 */
val WEEKDAY_SHORT: List<String> = listOf("日", "一", "二", "三", "四", "五", "六")

/**
 * 按「周一 = 0」的展示下标取星期短标签。
 * <p>
 * 日历 / 星期柱都是周一起算，而 [WEEKDAY_SHORT] 是周日起算，直接 `WEEKDAY_SHORT[i + 1]`
 * 会在周日（i = 6）越界崩掉 —— 走这个函数换算，别再手写 +1。
 */
fun weekdayShortOf(displayIndex: Int): String = WEEKDAY_SHORT[(displayIndex + 1) % 7]

/**
 * 隐私模式下的金额文案。
 * <p>
 * 只给「¥1,200.00」这一种写法 —— 之前写成「¥1,200.00 元」，¥ 和「元」重复得像打错字。
 */
fun money(fen: Long, hideAmounts: Boolean, suffix: String = ""): String =
    if (hideAmounts) "****$suffix" else "¥${formatAmount(fen)}$suffix"

/**
 * 「多久以前」的距离文案（估值时间、快照时间都用它）。
 * <p>
 * 超过一个月按整月说，避免出现「第 187 天」这种读不懂的数字。
 */
fun daysAgoText(at: Long): String {
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今天"
        days == 1 -> "昨天"
        days < 30 -> "$days 天前"
        else -> "${days / 30} 个月前"
    }
}

/** 带符号金额：+¥1,200.00 / -¥300.00；隐私模式下不泄露正负之外的任何信息 */
fun signedMoney(fen: Long, hideAmounts: Boolean): String =
    if (hideAmounts) "****" else (if (fen >= 0) "+" else "-") + "¥" + formatAmount(abs(fen))

/** 收益率：整数不带小数，其余保留一位（+24.6%） */
fun percentText(rate: Double): String {
    val percent = rate * 100.0
    val rounded = percent.roundToInt()
    val body = if (abs(percent - rounded) < 0.05) {
        "$rounded"
    } else {
        "${(percent * 10).roundToInt() / 10.0}"
    }
    return if (percent >= 0) "+$body%" else "$body%"
}

private val COMPACT_AMOUNT: DecimalFormat = DecimalFormat("0.##")

/**
 * 日历格子 / 柱状图上的紧凑金额：往上用 k / w，免得格子被长数字撑变形。
 * <p>
 * 千元以下**不取整**（保留到分，最多两位）：格子里写「3」而实际是 3.2 元，等于把账做错了；
 * k / w 保留两位，1 千多也不会被折成「1.2k」这种看不出来的误差。
 */
fun compactAmount(fen: Long): String {
    val yuan = fen / 100.0
    return when {
        yuan <= 0.0 -> "-"
        yuan < 1000 -> COMPACT_AMOUNT.format(yuan)
        yuan < 10000 -> COMPACT_AMOUNT.format(yuan / 1000) + "k"
        else -> COMPACT_AMOUNT.format(yuan / 10000) + "w"
    }
}

fun transactionTypeLabel(type: Int): String = when (type) {
    1 -> "支出"
    2 -> "收入"
    else -> "转账"
}

fun accountTypeLabel(type: Int): String = when (type) {
    1 -> "现金"
    2 -> "储蓄卡"
    3 -> "信用卡"
    4 -> "支付宝"
    5 -> "微信"
    6 -> "投资"
    else -> "其他"
}

fun isToday(millis: Long): Boolean =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate() == LocalDate.now()
