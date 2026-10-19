package com.jizhang.app.ui.common

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

/**
 * 通用的「起止日期」选择弹窗（Material3 的 DateRangePicker）。
 *
 * 账单页筛选用一次，导出账单用一次，所以放在 common 里，别两个页面各抄一份。
 * 回调给的是**本地时区的两个自然日 0 点**（闭区间语义，`start <= end`）；
 * 要拿去查询时记得把止日 +1 天，换成左闭右开。
 *
 * 头部不用 M3 自带的 title/headline 插槽（字号太大且会撑出固定最小高度），改为自己画一行摘要，
 * 详见 [DateRangePicker] 调用处的注释。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DateRangeDialog(
    /** 预选区间（本地 0 点，闭区间止日）：再次打开时把当前筛选原样亮出来，不用重新点一遍 */
    initialStartMillis: Long? = null,
    initialEndMillis: Long? = null,
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDateRangePickerState(
        initialSelectedStartDateMillis = initialStartMillis?.let(::localMillisToUtcPickerMillis),
        initialSelectedEndDateMillis = initialEndMillis?.let(::localMillisToUtcPickerMillis)
    )
    val startMillis = pickerState.selectedStartDateMillis
    val endMillis = pickerState.selectedEndDateMillis

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                // 一个日期都没选时点「确定」没意义，直接禁用，别让用户以为点坏了
                enabled = startMillis != null,
                onClick = {
                    val start = startMillis ?: return@TextButton
                    // 只选了开始日就当作单日：止日与开始日相同，闭区间语义
                    val end = endMillis ?: start
                    onConfirm(utcMillisToLocalStartOfDay(start), utcMillisToLocalStartOfDay(end))
                }
            ) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        // 头部干脆自己画，不让 DateRangePicker 渲染：
        // 它的 title/headline 插槽虽然在，但只要 title 非 null，内部就会套
        // `defaultMinSize(RangeSelectionHeaderContainerHeight - 60dp)` 顶住一大块空白，
        // 再配上 headlineSmall 的两个大日期，中文一长就换行、把日历挤到屏幕外。
        // 两个插槽都传 null 时既没有最小高度也没有分割线，日历能整体上提。
        Column(modifier = Modifier.padding(bottom = 2.dp)) {
            Text(
                text = "选择时间范围",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = rangeSummary(startMillis, endMillis),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp)
            )
            DateRangePicker(
                state = pickerState,
                title = null,
                headline = null,
                // 去掉「键盘输入 / 日历」切换：区间用打字选更慢，图标还白占一行
                showModeToggle = false
            )
        }
    }
}

/** 顶部摘要文案：没选 → 选了一半 → 选完（带天数，方便确认「只导周日这一天」这类需求） */
private fun rangeSummary(startMillis: Long?, endMillis: Long?): String = when {
    startMillis == null -> "点选开始日期"
    endMillis == null -> "开始 ${formatDay(startMillis)} · 再点结束日期"
    else -> {
        val days = ChronoUnit.DAYS.between(utcDate(startMillis), utcDate(endMillis)) + 1
        if (days <= 1L) {
            "${formatDay(startMillis)} · 仅这一天"
        } else {
            "${formatDay(startMillis)} - ${formatDay(endMillis)} · 共 $days 天"
        }
    }
}

private val DAY_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M月d日")

private fun formatDay(millis: Long): String = utcDate(millis).format(DAY_FORMATTER)

/** DateRangePicker 的 millis 是 UTC 0 点，取日期必须按 UTC 解读，否则东八区会差一天 */
private fun utcDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()

/**
 * `DateRangePicker` 给的是「所选日期 UTC 0 点」的 millis，
 * 直接当本地时间用会整体偏一天（东八区会漏掉开始日 0~8 点）。这里换算成本地当天 0 点。
 */
fun utcMillisToLocalStartOfDay(millis: Long): Long =
    utcDate(millis).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

/** 反过来：本地时间 → `DateRangePicker` 认的「UTC 当天 0 点」millis（预选时用） */
fun localMillisToUtcPickerMillis(millis: Long): Long =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
        .atStartOfDay(ZoneOffset.UTC)
        .toInstant()
        .toEpochMilli()
