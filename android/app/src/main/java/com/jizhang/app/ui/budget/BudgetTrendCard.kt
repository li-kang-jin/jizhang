package com.jizhang.app.ui.budget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jizhang.app.data.repository.BudgetForecast
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.theme.AppColors
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

/**
 * 预算趋势卡：实际累计 / 预算基线 / 固定支出 / 预测扇形带，外加超支概率与每日建议额度。
 * <p>
 * 模型细节见 [com.jizhang.app.data.repository.BudgetForecaster]：
 * 零膨胀伽马 + 蒙特卡洛算概率，VaR 约束反解每日额度。
 */
@Composable
fun BudgetTrendCard(forecast: BudgetForecast, modifier: Modifier = Modifier) {
    val riskColor = when {
        forecast.overrunProbability >= 0.6 -> AppColors.expense
        forecast.overrunProbability >= 0.3 -> AppColors.expense.copy(alpha = 0.85f)
        forecast.overrunProbability >= 0.1 -> AppColors.accent
        else -> AppColors.income
    }
    val percent = (forecast.overrunProbability * 100).roundToInt()
    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "预算趋势",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "超支概率 $percent%",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = riskColor
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = forecast.riskLabel,
                style = MaterialTheme.typography.labelSmall,
                color = riskColor
            )
        }

        Spacer(Modifier.height(12.dp))

        BudgetTrendChart(
            forecast = forecast,
            modifier = Modifier
                .fillMaxWidth()
                .height(168.dp)
        )

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = "第 1 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "第 ${forecast.daysTotal} 天",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(10.dp))
        LegendRow()

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            TrendStat(
                label = "期末预计",
                value = "${formatAmount(forecast.finalMedian)} 元",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            TrendStat(
                label = "悲观 (P90)",
                value = "${formatAmount(forecast.finalP90)} 元",
                color = if (forecast.finalP90 > forecast.totalBudget) {
                    AppColors.expense
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f)
            )
            TrendStat(
                label = "每日建议",
                value = "${formatAmount(forecast.suggestedDaily)} 元",
                color = if (forecast.suggestedDaily == 0L) {
                    AppColors.expense
                } else {
                    AppColors.accent
                },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(8.dp))
        Text(
            text = adviceText(forecast),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        // 公式与口径：默认收起，避免每张卡都变成一页说明书
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .clickable { expanded = !expanded }
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "口径与公式",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Icon(
                imageVector = if (expanded) {
                    Icons.Filled.KeyboardArrowUp
                } else {
                    Icons.Filled.KeyboardArrowDown
                },
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (expanded) {
            FormulaNotes(forecast)
        }
    }
}

/* ==================== 口径说明 ==================== */

/**
 * 把「这些数是怎么算出来的」摊开写清楚。
 * <p>
 * 数字直接代入当前结果，不是泛泛的公式说明 —— 用户看到 63% 时能立刻对上 σ、D、z 分别是多少。
 */
@Composable
private fun FormulaNotes(forecast: BudgetForecast) {
    Column(modifier = Modifier.fillMaxWidth()) {

        NoteHeading("① 每日建议额度")
        NoteFormula("a = A / D − z · σ / √D")
        NoteText(
            "A 剩余可自由支配 ${formatAmount(forecast.freeBudget.coerceAtLeast(0L))} 元" +
                "（总预算 − 已花 − 未到期固定支出）"
        )
        NoteText(
            "D 剩余 ${forecast.daysLeft} 天 · σ 日均波动 ${formatAmount(forecast.dailySd)} 元 · " +
                "z(${forecast.confidencePercent}%) = ${formatZ(forecast.zValue)}"
        )
        NoteText(
            if (forecast.suggestedDaily <= 0L) {
                "代入后为负，按 0 显示：这表示「按当前波动留不出 ${forecast.confidencePercent}% 的安全边际」，" +
                    "不代表不能花钱"
            } else {
                "结果 ${formatAmount(forecast.suggestedDaily)} 元/天：按这个额度花，" +
                    "期末超支概率约为 ${100 - forecast.confidencePercent}%"
            }
        )

        NoteHeading("② 超支概率")
        if (forecast.degraded) {
            NoteFormula("P = 1 − Φ((A − μD) / (σ√D))")
            NoteText(
                "有支出的天数不足 14 天，不做模拟，直接用带漂移的布朗运动闭式解；" +
                    "μ 是日均 ${formatAmount(forecast.averageDaily)} 元。样本越少，这个数越粗。"
            )
        } else {
            NoteFormula("S = Σ 伽马(λ, k, θ) + 到期固定支出，P = P(S > 预算)")
            NoteText(
                "日支出按「零膨胀伽马」建模：某天有支出的概率 λ = ${formatLambda(forecast.lambda)}，" +
                    "金额服从 Gamma(k, θ)，k = μ²/σ²、θ = σ²/μ（矩估计）。"
            )
            NoteText(
                "从明天起模拟 ${forecast.simulations} 条路径，每条叠加到期日确定的固定支出，" +
                    "期末总支出超过总预算的路径占比就是上面的百分比。"
            )
        }

        NoteHeading("③ 曲线怎么读")
        NoteText("实线 = 实际累计支出；灰虚线 = 扣掉固定支出后匀速花的基线（固定支出在到期日阶跃）")
        NoteText("浅色扇形 = 预测的 10%~90% 区间；红虚线 = 总预算；竖虚线 = 今天")

        NoteHeading("④ 要注意的问题")
        NoteText("· 日支出按半衰期 10 天加权，金额做 5%/95% 缩尾：偶发的大额不会带偏日均与波动。")
        NoteText("· 固定支出只在到期日计入预测，不自动记账、不计入「已用」；到期前请记得实际还款/缴费。")
        NoteText("· 「余额调整」不计入统计：校准账户余额不会污染消费统计和预测样本。")
        NoteText(
            "· 概率是统计估计，不是承诺：目前只用了 ${forecast.sampleDays} 天样本（其中 " +
                "${forecast.positiveDays} 天有支出），遇到旅游、装修这类一次性大额会明显偏移。"
        )
        NoteText("· 模型不含收入变化与临时大额计划；有大额支出前，建议先手动调整预算。")
    }
}

@Composable
private fun NoteHeading(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun NoteFormula(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        fontFamily = FontFamily.Monospace,
        color = MaterialTheme.colorScheme.onSurface,
        modifier = Modifier.padding(bottom = 4.dp)
    )
}

@Composable
private fun NoteText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(bottom = 3.dp)
    )
}

private fun formatZ(z: Double): String = String.format(Locale.US, "%.2f", z)

private fun formatLambda(lambda: Double): String = String.format(Locale.US, "%.2f", lambda)

/** 建议额度的解释文案：和当前日均比，差多少；额度为 0 时给出「平摊值」而不是只说不行 */
private fun adviceText(forecast: BudgetForecast): String {
    val head = when {
        forecast.degraded ->
            "样本偏少（${forecast.positiveDays} 天有支出），按当前日均与波动估算"

        forecast.positiveDays >= forecast.sampleDays ->
            "最近 ${forecast.sampleDays} 天都有支出 · 模拟 2000 次"

        else ->
            "最近 ${forecast.sampleDays} 天（${forecast.positiveDays} 天有支出）· 模拟 2000 次"
    }
    val body = when {
        forecast.suggestedDaily <= 0L -> {
            // 额度为 0 不是「不能花」，而是「按这个把握算，留不出安全边际」
            val flat = if (forecast.daysLeft > 0) {
                forecast.freeBudget.coerceAtLeast(0L) / forecast.daysLeft
            } else {
                0L
            }
            " · 按 ${forecast.confidencePercent}% 把握已无安全额度：剩余 " +
                "${formatAmount(forecast.freeBudget.coerceAtLeast(0L))} 元平摊到 " +
                "${forecast.daysLeft} 天只有 ${formatAmount(flat)} 元/天"
        }

        forecast.averageDaily > forecast.suggestedDaily ->
            " · 目前日均 ${formatAmount(forecast.averageDaily)} 元，需比现在少花 " +
                "${formatAmount(forecast.averageDaily - forecast.suggestedDaily)} 元"

        else ->
            " · 目前日均 ${formatAmount(forecast.averageDaily)} 元，在建议额度内"
    }
    return head + body
}

@Composable
private fun LegendRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        LegendItem(AppColors.accent, "实际")
        LegendItem(MaterialTheme.colorScheme.outline, "预算基线")
        LegendItem(AppColors.accent.copy(alpha = 0.35f), "预测区间")
        LegendItem(AppColors.expense, "总预算")
    }
}

@Composable
private fun LegendItem(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(width = 12.dp, height = 3.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TrendStat(
    label: String,
    value: String,
    color: Color,
    modifier: Modifier
) {
    Column(modifier = modifier) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = color
        )
    }
}

/**
 * 折线图本体。
 * <p>
 * 四层：预测扇形带 → 预算基线（已含固定支出阶跃）→ 实际累计 → 预测中位线，
 * 另外画总预算水平线与「今天」竖线。固定支出到期日用短竖线标出来。
 */
@Composable
private fun BudgetTrendChart(forecast: BudgetForecast, modifier: Modifier) {
    val accent = AppColors.accent
    val expense = AppColors.expense
    val muted = MaterialTheme.colorScheme.outline
    val days = forecast.daysTotal
    if (days <= 0) return

    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        val yMax = yMaxOf(forecast)
        fun x(index: Int): Float = w * index / days
        fun y(value: Long): Float = h - (value / yMax) * h

        val band = Path()
        var bandStarted = false
        for (i in forecast.elapsedDays..days) {
            val v = forecast.high[i]
            if (v == null) continue
            if (!bandStarted) {
                band.moveTo(x(i), y(v))
                bandStarted = true
            } else {
                band.lineTo(x(i), y(v))
            }
        }
        for (i in days downTo forecast.elapsedDays) {
            val v = forecast.low[i] ?: continue
            band.lineTo(x(i), y(v))
        }
        if (bandStarted) {
            band.close()
            drawPath(band, color = accent.copy(alpha = 0.16f))
        }

        // 预算基线：扣掉固定支出后匀速 + 到期日阶跃
        val baseline = Path()
        forecast.baseline.forEachIndexed { i, v ->
            if (i == 0) baseline.moveTo(x(i), y(v)) else baseline.lineTo(x(i), y(v))
        }
        drawPath(
            path = baseline,
            color = muted.copy(alpha = 0.7f),
            style = Stroke(width = 1.2.dp.toPx(), pathEffect = dashed(6f, 5f))
        )

        // 实际累计
        val actual = Path()
        var actualStarted = false
        for (i in 0..forecast.elapsedDays) {
            val v = forecast.actual[i] ?: continue
            if (!actualStarted) {
                actual.moveTo(x(i), y(v))
                actualStarted = true
            } else {
                actual.lineTo(x(i), y(v))
            }
        }
        if (actualStarted) {
            drawPath(actual, color = accent, style = Stroke(width = 2.4.dp.toPx()))
        }

        // 预测中位线
        val median = Path()
        var medianStarted = false
        for (i in forecast.elapsedDays..days) {
            val v = forecast.median[i] ?: continue
            if (!medianStarted) {
                median.moveTo(x(i), y(v))
                medianStarted = true
            } else {
                median.lineTo(x(i), y(v))
            }
        }
        if (medianStarted) {
            drawPath(
                path = median,
                color = accent.copy(alpha = 0.9f),
                style = Stroke(width = 1.8.dp.toPx(), pathEffect = dashed(8f, 6f))
            )
        }

        // 总预算线
        drawLine(
            color = expense.copy(alpha = 0.8f),
            start = Offset(0f, y(forecast.totalBudget)),
            end = Offset(w, y(forecast.totalBudget)),
            strokeWidth = 1.dp.toPx(),
            pathEffect = dashed(4f, 4f)
        )

        // 今天
        if (forecast.elapsedDays in 0..days) {
            val tx = x(forecast.elapsedDays)
            drawLine(
                color = muted.copy(alpha = 0.5f),
                start = Offset(tx, 0f),
                end = Offset(tx, h),
                strokeWidth = 1.dp.toPx(),
                pathEffect = dashed(3f, 4f)
            )
        }

        // 固定支出到期日：短竖线，说明那天为什么会突然变陡
        val tick = 10.dp.toPx()
        val startDate = BudgetPeriods.startDate(forecast.period)
        forecast.fixedSteps.forEach { step ->
            val index = ChronoUnit.DAYS.between(startDate, step.date).toInt() + 1
            if (index !in 1..days) return@forEach
            val sx = x(index)
            drawLine(
                color = expense.copy(alpha = 0.45f),
                start = androidx.compose.ui.geometry.Offset(sx, h - tick),
                end = androidx.compose.ui.geometry.Offset(sx, h),
                strokeWidth = 1.6.dp.toPx()
            )
        }
    }
}

private fun yMaxOf(forecast: BudgetForecast): Float {
    val values = sequenceOf(
        forecast.totalBudget,
        forecast.high.filterNotNull().maxOrNull() ?: 0L,
        forecast.actual.filterNotNull().maxOrNull() ?: 0L,
        forecast.baseline.maxOrNull() ?: 0L
    )
    return (values.maxOrNull() ?: 1L).coerceAtLeast(1L) * 1.06f
}

private fun DrawScope.dashed(on: Float, off: Float): PathEffect =
    PathEffect.dashPathEffect(floatArrayOf(on * density, off * density))
