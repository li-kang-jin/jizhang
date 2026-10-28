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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jizhang.app.ui.theme.JizhangTheme
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/**
 * 宠物形象。
 *
 * 用 Compose Canvas 绘制，不依赖任何美术资源。三种形象共用同一套「软糖脸」结构
 * （低饱和渐变身体 + 大眼 + 腮红 + 柔和落地影），靠耳朵 / 背刺 / 投币口与猪鼻区分物种。
 *
 * 配色原则：
 * - **每个物种一套独立的高级灰调**（燕麦拿铁 / 鼠尾草绿 / 雾玫瑰），互不撞色；
 * - 颜色只从这三组里取，不随主题变化 —— 宠物是「物」，跟 UI 主题无关，
 *   而且低饱和浅色在明暗两套主题下都能压得住；
 * - 表情由 [mood] 驱动（眯眼、眉毛、汗滴），不靠换色传达情绪。
 */
@Composable
fun PetAvatar(
    type: PetType,
    mood: PetMood,
    modifier: Modifier = Modifier,
    animated: Boolean = true
) {
    val transition = rememberInfiniteTransition(label = "pet")
    val bounce by transition.animateFloat(
        initialValue = 0f,
        targetValue = if (animated) 1f else 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1500),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bounce"
    )

    Canvas(modifier = modifier) {
        drawPet(type, mood, bounce)
    }
}

/**
 * 该形象的主题色。
 *
 * 给「选中态底色 / 强调描边」这类地方用，保证同一个宠物在头像和选项里是同一套颜色。
 */
val PetType.tint: Color
    get() = paletteOf(this).shade

/* ---------------- 配色 ---------------- */

/**
 * 一个形象的全部用色。
 *
 * @param top 身体受光面的浅色
 * @param bottom 身体背光面的深色
 * @param shade 耳朵内衬 / 背刺 / 鼻子等点缀（也是 [tint]）
 * @param line 眼睛、嘴、胡须等线条
 * @param blush 腮红
 * @param glow 身后那圈若隐若现的光晕
 */
private data class PetPalette(
    val top: Color,
    val bottom: Color,
    val shade: Color,
    val line: Color,
    val blush: Color,
    val glow: Color
)

/** 燕麦拿铁：暖、耐看，不像亮黄那么廉价 */
private val CAT_PALETTE = PetPalette(
    top = Color(0xFFF4E4CF),
    bottom = Color(0xFFDCC0A0),
    shade = Color(0xFFC9A17C),
    line = Color(0xFF54402F),
    blush = Color(0xFFE3A090),
    glow = Color(0xFFE4C8A7)
)

/** 鼠尾草：绿里带灰，跟「记账」这件事的克制感一致 */
private val DINO_PALETTE = PetPalette(
    top = Color(0xFFDCE8DD),
    bottom = Color(0xFFAFCAB5),
    shade = Color(0xFF8DB29A),
    line = Color(0xFF34483C),
    blush = Color(0xFFDFA99C),
    glow = Color(0xFFAFCBB6)
)

/** 雾玫瑰：豆沙粉，甜而不艳 */
private val PIGGY_PALETTE = PetPalette(
    top = Color(0xFFF8E2DD),
    bottom = Color(0xFFE2B5AF),
    shade = Color(0xFFCB968F),
    line = Color(0xFF5A403B),
    blush = Color(0xFFD98D86),
    glow = Color(0xFFE0B3AC)
)

private fun paletteOf(type: PetType): PetPalette = when (type) {
    PetType.CAT -> CAT_PALETTE
    PetType.DINO -> DINO_PALETTE
    PetType.PIGGY -> PIGGY_PALETTE
}

/* ---------------- 绘制 ---------------- */

private fun DrawScope.drawPet(type: PetType, mood: PetMood, bounce: Float) {
    val p = paletteOf(type)
    val unit = min(size.width, size.height)
    val cx = size.width / 2f
    // 轻微上下浮动 + 极小幅度缩放，像在呼吸
    val cy = size.height / 2f + unit * 0.025f * bounce
    val r = unit * 0.30f * (1f - 0.015f * bounce)

    drawHalo(cx, cy, unit, p)
    drawGroundShadow(cx, cy, r, p)

    when (type) {
        PetType.CAT -> drawCatEars(cx, cy, r, p)
        PetType.DINO -> drawDinoSpikes(cx, cy, r, p)
        PetType.PIGGY -> drawPiggyEars(cx, cy, r, p)
    }

    drawBody(cx, cy, r, p)

    // 投币口和猪鼻都压在身体之上，画在身体之前会被盖掉
    if (type == PetType.PIGGY) {
        drawPiggySlot(cx, cy, r, p)
        drawPiggySnout(cx, cy, r, p)
    }

    drawFace(type, mood, cx, cy, r, p)

    if (mood == PetMood.EXCITED) {
        drawSparkle(cx - r * 0.92f, cy - r * 0.78f, r, p.shade)
        drawSparkle(cx + r * 0.92f, cy - r * 0.62f, r * 0.75f, p.shade)
    }
}

/**
 * 身后那圈柔光：让浅色身体在深浅两种主题里都有落点。
 *
 * alpha 压得很低 —— 卡片是白底，圈一重就像宠物底下一块灰印子。
 */
private fun DrawScope.drawHalo(cx: Float, cy: Float, unit: Float, p: PetPalette) {
    val radius = unit * 0.5f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(p.glow.copy(alpha = 0.20f), Color.Transparent),
            center = Offset(cx, cy),
            radius = radius
        ),
        radius = radius,
        center = Offset(cx, cy)
    )
}

/** 落地影：把宠物「放」在卡片上，而不是飘着 */
private fun DrawScope.drawGroundShadow(cx: Float, cy: Float, r: Float, p: PetPalette) {
    drawOval(
        color = p.line.copy(alpha = 0.09f),
        topLeft = Offset(cx - r * 0.62f, cy + r * 0.84f),
        size = Size(r * 1.24f, r * 0.20f)
    )
}

/** 身体：上浅下深的竖向渐变，边缘靠大圆角保持「软」 */
private fun DrawScope.drawBody(cx: Float, cy: Float, r: Float, p: PetPalette) {
    val top = cy - r * 0.99f
    val height = r * 1.98f
    drawOval(
        brush = Brush.verticalGradient(
            colors = listOf(p.top, p.bottom),
            startY = top,
            endY = top + height
        ),
        topLeft = Offset(cx - r, top),
        size = Size(r * 2f, height)
    )
}

/**
 * 猫耳：三角形 + 圆润的尖。
 *
 * 之前的版本通体圆润、又窄又高，看着像兔子耳朵；猫耳必须「底宽、边直、尖一点」，
 * 只在尖端留一点点圆，才既像猫又不扎眼。
 */
private fun DrawScope.drawCatEars(cx: Float, cy: Float, r: Float, p: PetPalette) {
    listOf(-1f, 1f).forEach { dir ->
        val ear = Path().apply {
            moveTo(cx + dir * r * 0.10f, cy - r * 0.76f)
            lineTo(cx + dir * r * 0.30f, cy - r * 1.32f)
            // 只在尖上做一次小圆角
            quadraticBezierTo(cx + dir * r * 0.52f, cy - r * 1.50f, cx + dir * r * 0.72f, cy - r * 1.16f)
            lineTo(cx + dir * r * 0.92f, cy - r * 0.42f)
            close()
        }
        drawPath(ear, p.bottom)

        val inner = Path().apply {
            moveTo(cx + dir * r * 0.30f, cy - r * 0.74f)
            lineTo(cx + dir * r * 0.40f, cy - r * 1.14f)
            quadraticBezierTo(cx + dir * r * 0.50f, cy - r * 1.24f, cx + dir * r * 0.60f, cy - r * 1.04f)
            lineTo(cx + dir * r * 0.72f, cy - r * 0.62f)
            close()
        }
        drawPath(inner, p.shade.copy(alpha = 0.70f))
    }
}

/** 恐龙背刺：三片圆头小帆，从头顶探出来 */
private fun DrawScope.drawDinoSpikes(cx: Float, cy: Float, r: Float, p: PetPalette) {
    listOf(-122f, -90f, -58f).forEach { angle ->
        drawSoftSpike(
            cx = cx,
            cy = cy,
            angleDeg = angle,
            inner = r * 0.82f,
            tip = r * 1.52f,
            halfWidth = r * 0.21f,
            color = p.shade
        )
    }
}

/** 圆头小刺：靠两条二次曲线把尖角磨圆，避免看起来像锯齿 */
private fun DrawScope.drawSoftSpike(
    cx: Float,
    cy: Float,
    angleDeg: Float,
    inner: Float,
    tip: Float,
    halfWidth: Float,
    color: Color
) {
    val rad = Math.toRadians(angleDeg.toDouble())
    val cosA = cos(rad).toFloat()
    val sinA = sin(rad).toFloat()
    // 垂直于射线的方向，用来把刺的底边撑开
    val nx = -sinA
    val ny = cosA

    val baseX = cx + cosA * inner
    val baseY = cy + sinA * inner
    val tipX = cx + cosA * tip
    val tipY = cy + sinA * tip

    val path = Path().apply {
        moveTo(baseX + nx * halfWidth, baseY + ny * halfWidth)
        quadraticBezierTo(
            tipX + nx * halfWidth * 0.25f,
            tipY + ny * halfWidth * 0.25f,
            tipX,
            tipY
        )
        quadraticBezierTo(
            tipX - nx * halfWidth * 0.25f,
            tipY - ny * halfWidth * 0.25f,
            baseX - nx * halfWidth,
            baseY - ny * halfWidth
        )
        close()
    }
    drawPath(path, color)
}

/** 存钱罐的耳朵：短、宽、钝，往两侧塌一点，跟猫的尖耳区分开 */
private fun DrawScope.drawPiggyEars(cx: Float, cy: Float, r: Float, p: PetPalette) {
    listOf(-150f, -30f).forEach { angle ->
        drawSoftSpike(
            cx = cx,
            cy = cy,
            angleDeg = angle,
            inner = r * 0.84f,
            tip = r * 1.22f,
            halfWidth = r * 0.25f,
            color = p.bottom
        )
    }
}

private fun DrawScope.drawPiggySlot(cx: Float, cy: Float, r: Float, p: PetPalette) {
    drawRoundRect(
        color = p.line.copy(alpha = 0.45f),
        topLeft = Offset(cx - r * 0.28f, cy - r * 0.74f),
        size = Size(r * 0.56f, r * 0.11f),
        cornerRadius = CornerRadius(r * 0.06f, r * 0.06f)
    )
}

private fun DrawScope.drawPiggySnout(cx: Float, cy: Float, r: Float, p: PetPalette) {
    val snoutCy = cy + r * 0.34f
    drawOval(
        color = p.shade,
        topLeft = Offset(cx - r * 0.30f, snoutCy - r * 0.21f),
        size = Size(r * 0.60f, r * 0.42f)
    )
    listOf(-1f, 1f).forEach { dir ->
        drawOval(
            color = p.line.copy(alpha = 0.60f),
            topLeft = Offset(cx + dir * r * 0.13f - r * 0.04f, snoutCy - r * 0.07f),
            size = Size(r * 0.08f, r * 0.14f)
        )
    }
}

private fun DrawScope.drawFace(
    type: PetType,
    mood: PetMood,
    cx: Float,
    cy: Float,
    r: Float,
    p: PetPalette
) {
    val eyeY = cy - r * 0.12f
    val eyeDx = r * 0.36f

    // ---- 眼睛 ----
    if (mood == PetMood.EXCITED) {
        drawSmileEye(cx - eyeDx, eyeY, r, p.line)
        drawSmileEye(cx + eyeDx, eyeY, r, p.line)
    } else {
        drawOpenEye(cx - eyeDx, eyeY, r, p.line)
        drawOpenEye(cx + eyeDx, eyeY, r, p.line)
    }

    // ---- 眉毛（只有担心时才需要，别的表情加眉毛会显凶） ----
    if (mood == PetMood.WORRIED) {
        // 「八字眉」：内侧高、外侧低，才是担心；反过来就是生气
        listOf(-1f, 1f).forEach { dir ->
            drawLine(
                color = p.line.copy(alpha = 0.8f),
                start = Offset(cx + dir * r * 0.52f, cy - r * 0.30f),
                end = Offset(cx + dir * r * 0.20f, cy - r * 0.42f),
                strokeWidth = r * 0.05f,
                cap = StrokeCap.Round
            )
        }
        drawSweatDrop(cx + r * 0.68f, cy - r * 0.46f, r)
    }

    // ---- 鼻子 / 嘴 ----
    when (type) {
        PetType.CAT -> {
            drawCatNose(cx, cy, r, p)
            when (mood) {
                PetMood.HAPPY, PetMood.EXCITED -> drawCatMouth(cx, cy, r, p.line)
                PetMood.NORMAL -> drawTinySmile(cx, cy, r, p.line)
                PetMood.WORRIED -> drawWorriedMouth(cx, cy, r, p.line)
            }
        }

        PetType.DINO -> when (mood) {
            PetMood.HAPPY, PetMood.EXCITED -> drawSmile(cx, cy, r, p.line)
            PetMood.NORMAL -> drawTinySmile(cx, cy, r, p.line)
            PetMood.WORRIED -> drawWorriedMouth(cx, cy, r, p.line)
        }

        // 猪鼻把嘴的位置占了：鼻子上的两个孔本身就是它的「嘴」，
        // 再叠一道笑弧会和鼻孔打架，看起来像鼻子上长了张嘴
        PetType.PIGGY -> Unit
    }

    // ---- 腮红 ----
    listOf(-1f, 1f).forEach { dir ->
        drawOval(
            color = p.blush.copy(alpha = 0.45f),
            topLeft = Offset(cx + dir * r * 0.66f - r * 0.16f, cy + r * 0.16f - r * 0.10f),
            size = Size(r * 0.32f, r * 0.20f)
        )
    }

    // ---- 猫胡须 ----
    if (type == PetType.CAT) {
        listOf(-1f, 1f).forEach { dir ->
            listOf(-0.04f, 0.08f).forEach { dy ->
                drawLine(
                    color = p.line.copy(alpha = 0.35f),
                    start = Offset(cx + dir * r * 0.70f, cy + r * dy),
                    end = Offset(cx + dir * r * 1.08f, cy + r * (dy - 0.06f)),
                    strokeWidth = r * 0.032f,
                    cap = StrokeCap.Round
                )
            }
        }
    }
}

/** 睁眼：竖椭圆 + 两点高光。眼大是「可爱」的第一来源，别往小里调 */
private fun DrawScope.drawOpenEye(x: Float, y: Float, r: Float, line: Color) {
    drawOval(
        color = line,
        topLeft = Offset(x - r * 0.115f, y - r * 0.155f),
        size = Size(r * 0.23f, r * 0.31f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.95f),
        radius = r * 0.050f,
        center = Offset(x + r * 0.036f, y - r * 0.070f)
    )
    drawCircle(
        color = Color.White.copy(alpha = 0.65f),
        radius = r * 0.026f,
        center = Offset(x - r * 0.036f, y + r * 0.060f)
    )
}

/** 弯眼笑：向上拱的短弧 */
private fun DrawScope.drawSmileEye(x: Float, y: Float, r: Float, line: Color) {
    drawArc(
        color = line,
        startAngle = 200f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(x - r * 0.11f, y - r * 0.06f),
        size = Size(r * 0.22f, r * 0.20f),
        style = Stroke(width = r * 0.055f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawCatNose(cx: Float, cy: Float, r: Float, p: PetPalette) {
    val nose = Path().apply {
        moveTo(cx - r * 0.085f, cy + r * 0.08f)
        quadraticBezierTo(cx, cy + r * 0.03f, cx + r * 0.085f, cy + r * 0.08f)
        quadraticBezierTo(cx, cy + r * 0.19f, cx - r * 0.085f, cy + r * 0.08f)
        close()
    }
    drawPath(nose, p.shade)
}

/** 猫嘴：两个小拱连成的「ω」，比单道弧线更像猫 */
private fun DrawScope.drawCatMouth(cx: Float, cy: Float, r: Float, line: Color) {
    val mouthY = cy + r * 0.26f
    val path = Path().apply {
        moveTo(cx - r * 0.16f, mouthY)
        quadraticBezierTo(cx - r * 0.08f, mouthY + r * 0.11f, cx, mouthY + r * 0.01f)
        quadraticBezierTo(cx + r * 0.08f, mouthY + r * 0.11f, cx + r * 0.16f, mouthY)
    }
    drawPath(path, line, style = Stroke(width = r * 0.045f, cap = StrokeCap.Round))
}

private fun DrawScope.drawSmile(cx: Float, cy: Float, r: Float, line: Color) {
    drawArc(
        color = line,
        startAngle = 20f,
        sweepAngle = 140f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.15f, cy + r * 0.12f),
        size = Size(r * 0.30f, r * 0.22f),
        style = Stroke(width = r * 0.045f, cap = StrokeCap.Round)
    )
}

/** 平淡时的一点点笑意：短、浅，别做成面无表情的直线 */
private fun DrawScope.drawTinySmile(cx: Float, cy: Float, r: Float, line: Color) {
    drawArc(
        color = line,
        startAngle = 30f,
        sweepAngle = 120f,
        useCenter = false,
        topLeft = Offset(cx - r * 0.09f, cy + r * 0.18f),
        size = Size(r * 0.18f, r * 0.14f),
        style = Stroke(width = r * 0.04f, cap = StrokeCap.Round)
    )
}

private fun DrawScope.drawWorriedMouth(cx: Float, cy: Float, r: Float, line: Color) {
    val mouthY = cy + r * 0.30f
    // 小小的撇嘴角就够了：张大嘴的哭脸在记账页太沉重
    val path = Path().apply {
        moveTo(cx - r * 0.11f, mouthY + r * 0.04f)
        quadraticBezierTo(cx, mouthY - r * 0.05f, cx + r * 0.11f, mouthY + r * 0.04f)
    }
    drawPath(path, line, style = Stroke(width = r * 0.045f, cap = StrokeCap.Round))
}

/** 担心时的汗滴：一滴就够，多了像哭 */
private fun DrawScope.drawSweatDrop(x: Float, y: Float, r: Float) {
    val drop = Path().apply {
        moveTo(x, y - r * 0.12f)
        quadraticBezierTo(x + r * 0.10f, y + r * 0.02f, x, y + r * 0.09f)
        quadraticBezierTo(x - r * 0.10f, y + r * 0.02f, x, y - r * 0.12f)
        close()
    }
    drawPath(drop, Color(0xFF9CBFD4).copy(alpha = 0.85f))
}

/** 兴奋时的小星光：两条交叉的圆头短线 */
private fun DrawScope.drawSparkle(x: Float, y: Float, r: Float, color: Color) {
    val arm = r * 0.13f
    val width = r * 0.035f
    drawLine(color, Offset(x - arm, y), Offset(x + arm, y), width, StrokeCap.Round)
    drawLine(color, Offset(x, y - arm), Offset(x, y + arm), width, StrokeCap.Round)
}

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
                            animated = false
                        )
                    }
                }
            }
        }
    }
}

/** 深色主题下的样子：浅色身体要能压住深底 */
@Preview(name = "宠物形象 · 深色", showBackground = true, widthDp = 280, heightDp = 120)
@Composable
private fun PetAvatarDarkPreview() {
    JizhangTheme(darkTheme = true) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PetAvatar(PetType.CAT, PetMood.HAPPY, Modifier.size(72.dp), animated = false)
            PetAvatar(PetType.DINO, PetMood.WORRIED, Modifier.size(72.dp), animated = false)
            PetAvatar(PetType.PIGGY, PetMood.EXCITED, Modifier.size(72.dp), animated = false)
        }
    }
}
