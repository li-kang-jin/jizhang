package com.jizhang.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jizhang.app.data.repository.DayAmount
import com.jizhang.app.ui.theme.AppColors
import java.time.LocalDate
import java.time.YearMonth
import kotlin.math.roundToInt

/**
 * 月历网格：一行七天，格子里是「日期 + 当日支出量级」。
 * <p>
 * 统计页的收支日历卡片与日历页共用这一个实现 —— 同一个东西在两处长得不一样，
 * 用户会以为是两个功能。
 *
 * @param minRows 最少画几行。日历页把它放在横向分页里，固定 6 行才不会滑动时高度乱跳；
 * 统计页按真实行数（5 / 6 行）排，省一行的高度。
 */
@Composable
internal fun MonthGrid(
    month: YearMonth,
    byDate: Map<LocalDate, DayAmount>,
    maxExpense: Long,
    today: LocalDate,
    selected: LocalDate?,
    hideAmounts: Boolean,
    minRows: Int = 0,
    onPickDay: (LocalDate) -> Unit
) {
    val leading = month.atDay(1).dayOfWeek.value % 7
    val cells = leading + month.lengthOfMonth()
    val rows = maxOf((cells + 6) / 7, minRows)

    Column {
        Row(modifier = Modifier.fillMaxWidth()) {
            WEEKDAY_SHORT.forEach { label ->
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(Modifier.height(4.dp))

        (0 until rows).forEach { row ->
            Row(modifier = Modifier.fillMaxWidth()) {
                repeat(7) { column ->
                    val day = row * 7 + column - leading + 1
                    if (day in 1..month.lengthOfMonth()) {
                        val date = month.atDay(day)
                        HeatCell(
                            date = date,
                            expense = byDate[date]?.expense ?: 0L,
                            income = byDate[date]?.income ?: 0L,
                            maxExpense = maxExpense,
                            selected = date == selected,
                            today = date == today,
                            future = date.isAfter(today),
                            hideAmounts = hideAmounts,
                            modifier = Modifier.weight(1f),
                            onClick = { onPickDay(date) }
                        )
                    } else {
                        Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * 日历格子。
 * <p>
 * 支出越多底色越深；没记录的日子只有浅灰底，没到的日子连底都没有（不可点）——
 * 这样「没花钱」和「还没到」不会再混淆。
 * <p>
 * 「今天」和「选中」必须是两种形态，否则用户分不清那个圈到底指哪个：
 * **选中 = 整圈主题色粗描边**，**今天 = 日期数字用主题色并加粗**（选中今天时两者同时出现）。
 */
@Composable
internal fun HeatCell(
    date: LocalDate,
    expense: Long,
    /** 当天收入：跟支出分开显示，否则「哪几天有进账」在月历上完全看不出来 */
    income: Long = 0L,
    maxExpense: Long,
    selected: Boolean,
    today: Boolean,
    future: Boolean,
    hideAmounts: Boolean,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    onClick: () -> Unit
) {
    val ratio = if (maxExpense <= 0L) 0f else (expense.toFloat() / maxExpense).coerceIn(0f, 1f)
    // 五档色阶：视觉上能分档，又不至于每笔差一点就换颜色
    val level = if (expense <= 0L) 0 else (ratio * 4).roundToInt().coerceIn(1, 4)
    val alpha = when (level) {
        0 -> 0f
        1 -> 0.28f
        2 -> 0.5f
        3 -> 0.72f
        else -> 1f
    }
    val background = when {
        future -> Color.Transparent
        expense > 0L -> AppColors.expense.copy(alpha = alpha)
        // 只有进账、没有支出的日子给一点绿底：否则它和「没记账」长得一模一样
        income > 0L -> AppColors.income.copy(alpha = 0.18f)
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    }
    val shape = RoundedCornerShape(if (compact) 3.dp else 6.dp)
    // 月视图里「选中」独占整圈描边；年视图格子太小画不下数字，今天只能靠一圈浅描边表示
    val markToday = compact && today
    val borderWidth = when {
        selected -> 2.dp
        markToday -> 1.5.dp
        else -> 0.dp
    }
    val borderColor = when {
        selected -> MaterialTheme.colorScheme.primary
        markToday -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> Color.Transparent
    }

    Box(
        modifier = modifier
            .padding(if (compact) 1.dp else 2.dp)
            // 接近正方形：格子里要竖排「日期 + 支出 + 收入」三行，
            // 之前的 1.12 太扁，收入的最后一行会被 clip 裁掉底部
            .let { if (compact) it.size(14.dp) else it.aspectRatio(1.02f) }
            .clip(shape)
            .background(background)
            .border(width = borderWidth, color = borderColor, shape = shape)
            .clickable(enabled = !future, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (!compact) {
            /*
             * 行高要略大于字号：设得比字号还紧，字形下半部分（数字的基线）会被行盒裁掉，
             * 表现出来就是「收入那行底部缺了一点」。日期行也压到 14sp，三行合起来才放得下。
             */
            val amountStyle = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp, lineHeight = 11.sp)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = date.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelSmall.copy(lineHeight = 14.sp),
                    color = when {
                        future -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
                        // 「今天」只靠颜色 + 字重表达，不跟选中抢那圈描边
                        today -> MaterialTheme.colorScheme.primary
                        alpha > 0.6f -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    fontWeight = if (today || selected) FontWeight.Bold else FontWeight.Normal
                )
                // 写「几十」而不是完整金额：格子太窄，只给量级，精确数字点开看。
                // 支出在上、收入在下，都带符号，深色底上用白字保证对比度。
                if (!future && expense > 0L) {
                    Text(
                        text = if (hideAmounts) "**" else "-${compactAmount(expense)}",
                        style = amountStyle,
                        color = if (alpha > 0.6f) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Medium
                    )
                }
                if (!future && income > 0L) {
                    Text(
                        text = if (hideAmounts) "**" else "+${compactAmount(income)}",
                        style = amountStyle,
                        color = if (alpha > 0.6f) Color.White else AppColors.income,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}
