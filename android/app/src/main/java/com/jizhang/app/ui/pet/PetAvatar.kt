package com.jizhang.app.ui.pet

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme
import kotlin.math.min

/**
 * 宠物形象。
 *
 * 用 Compose Canvas 绘制，不依赖任何美术资源：
 * 三种形象共用「圆脸 + 眼睛 + 嘴 + 腮红」的基础结构，靠耳朵 / 背刺 / 投币口区分外形，
 * 表情由 [mood] 驱动。后续如果要换成专业矢量图，只需替换本文件的实现。
 */
@Composable
fun PetAvatar(
    type: PetType,
    mood: PetMood,
    modifier: Modifier = Modifier,
    bodyColor: Color = Color(0xFFFFCE5C),
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "pet")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = modifier) {
        drawPet(type, mood, bodyColor, bounce)
    }
}

private fun DrawScope.drawPet(type: PetType, mood: PetMood, bodyColor: Color, bounce: Float) {
    val w = size.width
    val h = size.height
    val cx = w / 2f
    val baseRadius = min(w, h) * 0.32f
    // 轻微上下浮动，让宠物看起来是「活」的
    val cy = h / 2f + h * 0.04f * bounce
    val radius = baseRadius * (1f - 0.02f * bounce)

    val detail = bodyColor.copy(alpha = 0.85f).darken(0.12f)
    val eyeColor = Color(0xFF33262B)
    val blush = Color(0xFFFF9AA2).copy(alpha = 0.55f)

    // ---- 外形差异部分（画在脸之前，形成轮廓） ----
    when (type) {
        PetType.CAT -> drawCatEars(cx, cy, radius, bodyColor, detail)
        PetType.DINO -> drawDinoSpikes(cx, cy, radius, bodyColor, detail)
        PetType.PIGGY -> Unit
    }

    // ---- 脸 ----
    drawCircle(color = bodyColor, radius = radius, center = Offset(cx, cy))

    // 存钱罐的小腿，让它看起来是坐着的
    if (type == PetType.PIGGY) {
        drawCircle(
            color = detail,
            radius = radius * 0.22f,
            center = Offset(cx - radius * 0.45f, cy + radius * 0.92f)
        )
        drawCircle(
            color = detail,
            radius = radius * 0.22f,
            center = Offset(cx + radius * 0.45f, cy + radius * 0.92f)
        )
        // 投币口
        drawRoundRect(
            color = eyeColor.copy(alpha = 0.7f),
            topLeft = Offset(cx - radius * 0.32f, cy - radius * 0.72f),
            size = Size(radius * 0.64f, radius * 0.12f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(radius * 0.06f)
        )
    }

    // ---- 眼睛 ----
    val eyeY = cy - radius * 0.12f
    val eyeOffset = radius * 0.36f
    val eyeRadius = radius * 0.11f

    if (mood == PetMood.EXCITED) {
        // 开心到眯眼：用两道弧线
        drawArc(
            color = eyeColor,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx - eyeOffset - eyeRadius, eyeY - eyeRadius),
            size = Size(eyeRadius * 2.4f, eyeRadius * 2.4f),
            style = Stroke(width = radius * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
        drawArc(
            color = eyeColor,
            startAngle = 200f,
            sweepAngle = 140f,
            useCenter = false,
            topLeft = Offset(cx + eyeOffset - eyeRadius * 1.2f, eyeY - eyeRadius),
            size = Size(eyeRadius * 2.4f, eyeRadius * 2.4f),
            style = Stroke(width = radius * 0.075f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    } else {
        drawCircle(eyeColor, radius = eyeRadius, center = Offset(cx - eyeOffset, eyeY))
        drawCircle(eyeColor, radius = eyeRadius, center = Offset(cx + eyeOffset, eyeY))
        // 高光
        drawCircle(
            Color.White.copy(alpha = 0.9f),
            radius = eyeRadius * 0.32f,
            center = Offset(cx - eyeOffset + eyeRadius * 0.3f, eyeY - eyeRadius * 0.35f)
        )
        drawCircle(
            Color.White.copy(alpha = 0.9f),
            radius = eyeRadius * 0.32f,
            center = Offset(cx + eyeOffset + eyeRadius * 0.3f, eyeY - eyeRadius * 0.35f)
        )
    }

    // ---- 嘴 ----
    val mouthY = cy + radius * 0.28f
    when (mood) {
        PetMood.HAPPY, PetMood.EXCITED -> {
            // 微笑弧
            drawArc(
                color = eyeColor,
                startAngle = 20f,
                sweepAngle = 140f,
                useCenter = false,
                topLeft = Offset(cx - radius * 0.22f, mouthY - radius * 0.16f),
                size = Size(radius * 0.44f, radius * 0.34f),
                style = Stroke(width = radius * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }

        PetMood.NORMAL -> {
            drawLine(
                color = eyeColor,
                start = Offset(cx - radius * 0.14f, mouthY),
                end = Offset(cx + radius * 0.14f, mouthY),
                strokeWidth = radius * 0.07f,
                cap = androidx.compose.ui.graphics.StrokeCap.Round
            )
        }

        PetMood.WORRIED -> {
            // 担心的波浪嘴
            val path = Path().apply {
                moveTo(cx - radius * 0.2f, mouthY + radius * 0.05f)
                quadraticBezierTo(cx, mouthY - radius * 0.12f, cx + radius * 0.2f, mouthY + radius * 0.05f)
            }
            drawPath(
                path = path,
                color = eyeColor,
                style = Stroke(width = radius * 0.07f, cap = androidx.compose.ui.graphics.StrokeCap.Round)
            )
        }
    }

    // ---- 腮红 ----
    drawCircle(blush, radius = radius * 0.16f, center = Offset(cx - radius * 0.62f, cy + radius * 0.16f))
    drawCircle(blush, radius = radius * 0.16f, center = Offset(cx + radius * 0.62f, cy + radius * 0.16f))

    // 猫的胡须
    if (type == PetType.CAT) {
        val whiskerColor = detail.copy(alpha = 0.6f)
        listOf(-1f, 1f).forEach { dir ->
            listOf(-0.06f, 0.06f).forEach { dy ->
                drawLine(
                    color = whiskerColor,
                    start = Offset(cx + dir * radius * 0.72f, cy + radius * dy),
                    end = Offset(cx + dir * radius * 1.12f, cy + radius * (dy - 0.05f)),
                    strokeWidth = radius * 0.035f,
                    cap = androidx.compose.ui.graphics.StrokeCap.Round
                )
            }
        }
    }
}

private fun DrawScope.drawCatEars(cx: Float, cy: Float, radius: Float, bodyColor: Color, detail: Color) {
    listOf(-1f, 1f).forEach { dir ->
        val path = Path().apply {
            moveTo(cx + dir * radius * 0.30f, cy - radius * 0.78f)
            lineTo(cx + dir * radius * 0.68f, cy - radius * 1.22f)
            lineTo(cx + dir * radius * 0.86f, cy - radius * 0.58f)
            close()
        }
        drawPath(path, bodyColor)
        // 内耳
        val inner = Path().apply {
            moveTo(cx + dir * radius * 0.44f, cy - radius * 0.76f)
            lineTo(cx + dir * radius * 0.64f, cy - radius * 1.04f)
            lineTo(cx + dir * radius * 0.74f, cy - radius * 0.70f)
            close()
        }
        drawPath(inner, Color(0xFFFFB3C1).copy(alpha = 0.7f))
    }
}

private fun DrawScope.drawDinoSpikes(cx: Float, cy: Float, radius: Float, bodyColor: Color, detail: Color) {
    // 背部三根角刺
    listOf(0f, 1f, 2f).forEach { index ->
        val angle = (-140f + index * 28f) * (Math.PI / 180f).toFloat()
        val baseX = cx + kotlin.math.cos(angle) * radius * 0.95f
        val baseY = cy + kotlin.math.sin(angle) * radius * 0.95f
        val tipX = cx + kotlin.math.cos(angle) * radius * 1.42f
        val tipY = cy + kotlin.math.sin(angle) * radius * 1.42f
        val path = Path().apply {
            moveTo(baseX - radius * 0.13f, baseY + radius * 0.10f)
            lineTo(tipX, tipY)
            lineTo(baseX + radius * 0.13f, baseY + radius * 0.10f)
            close()
        }
        drawPath(path, detail)
    }
}

/** 简易加深颜色，避免引入额外依赖 */
private fun Color.darken(amount: Float): Color = Color(
    red = (red * (1f - amount)).coerceIn(0f, 1f),
    green = (green * (1f - amount)).coerceIn(0f, 1f),
    blue = (blue * (1f - amount)).coerceIn(0f, 1f),
    alpha = alpha
)

/** 供外部按矩形区域绘制时使用（保留扩展点） */
fun DrawScope.petBounds(): Rect = Rect(Offset.Zero, size)

/* ==================== 预览 ==================== */

/**
 * 三种形象 × 四种心情的对照表。
 * 调绘制比例 / 颜色 / 角度时不用装机，直接在这里看全部组合的效果。
 */
@Preview(name = "宠物形象总览", showBackground = true, widthDp = 340, heightDp = 300)
@Composable
private fun PetAvatarGalleryPreview() {
    JizhangTheme {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Spacer(Modifier.width(52.dp))
                PetMood.entries.forEach { mood ->
                    Text(
                        text = mood.name.lowercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.width(64.dp)
                    )
                }
            }
            PetType.entries.forEach { type ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = type.label,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.width(52.dp)
                    )
                    PetMood.entries.forEach { mood ->
                        PetAvatar(
                            type = type,
                            mood = mood,
                            modifier = Modifier.size(64.dp),
                            bodyColor = AppColors.accent,
                            animated = false
                        )
                    }
                }
            }
        }
    }
}

/** 深色主题下的样子 */
@Preview(name = "宠物形象 · 深色", showBackground = true, widthDp = 260, heightDp = 110)
@Composable
private fun PetAvatarDarkPreview() {
    JizhangTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PetAvatar(PetType.CAT, PetMood.HAPPY, Modifier.size(72.dp), AppColors.accent, animated = false)
            PetAvatar(PetType.DINO, PetMood.WORRIED, Modifier.size(72.dp), AppColors.accent, animated = false)
            PetAvatar(PetType.PIGGY, PetMood.EXCITED, Modifier.size(72.dp), AppColors.accent, animated = false)
        }
    }
}
