package com.jizhang.app.ui.common

import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val DATE_TIME_FORMAT = DateTimeFormatter.ofPattern("MM-dd HH:mm")
private val MONTH_FORMAT = DateTimeFormatter.ofPattern("yyyy年MM月")

/** 分 → 元，保留两位小数 */
fun formatAmount(fen: Long): String = String.format("%.2f", fen / 100.0)

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
