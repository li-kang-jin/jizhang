package com.jizhang.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/** 可选主题 */
enum class AppTheme(val key: String, val label: String, val emoji: String) {
    MINT("mint", "薄荷", "🌿"),
    SUNSET("sunset", "暖阳", "🌅"),
    SKY("sky", "晴空", "☁️"),
    BLOSSOM("blossom", "花开", "🌸");

    companion object {
        fun fromKey(key: String?): AppTheme =
            entries.firstOrNull { it.key == key } ?: MINT
    }
}

/**
 * 一套主题的完整色板。
 * <p>
 * 除了 Material 的主色 / 背景，还额外定义了「支出 / 收入 / 强调色」，
 * 让收支颜色也跟随主题变化，而不是写死正红正绿。
 */
data class ThemePalette(
    val primary: Color,
    val onPrimary: Color,
    val secondary: Color,
    val background: Color,
    val surface: Color,
    val surfaceVariant: Color,
    val outline: Color,
    val onSurface: Color,
    val onSurfaceVariant: Color,
    val expense: Color,
    val income: Color,
    val accent: Color
) {
    fun toLightScheme() = lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        // 容器色必须给：M3 组件（FilterChip / FilledTonalButton / 导航选中底）默认走
        // secondaryContainer，不覆盖就会露出 Material3 baseline 的紫色，跟主题打架。
        primaryContainer = lerp(Color.White, primary, 0.18f),
        onPrimaryContainer = onSurface,
        secondary = secondary,
        secondaryContainer = lerp(Color.White, secondary, 0.24f),
        onSecondaryContainer = onSurface,
        tertiary = accent,
        tertiaryContainer = lerp(Color.White, accent, 0.26f),
        onTertiaryContainer = onSurface,
        background = background,
        surface = surface,
        surfaceVariant = surfaceVariant,
        outline = outline,
        outlineVariant = lerp(background, outline, 0.45f),
        onSurface = onSurface,
        onSurfaceVariant = onSurfaceVariant,
        error = expense,
        errorContainer = lerp(Color.White, expense, 0.18f),
        onErrorContainer = onSurface
    )

    fun toDarkScheme() = darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = lerp(Color(0xFF1C1B1F), primary, 0.30f),
        onPrimaryContainer = Color(0xFFE6E1E9),
        secondary = secondary,
        secondaryContainer = lerp(Color(0xFF1C1B1F), secondary, 0.32f),
        onSecondaryContainer = Color(0xFFE6E1E9),
        tertiary = accent,
        tertiaryContainer = lerp(Color(0xFF1C1B1F), accent, 0.28f),
        onTertiaryContainer = Color(0xFFE6E1E9),
        surface = Color(0xFF1C1B1F),
        background = Color(0xFF141317),
        surfaceVariant = Color(0xFF2A2830),
        outline = Color(0xFF8C8A93),
        outlineVariant = lerp(Color(0xFF141317), Color(0xFF8C8A93), 0.5f),
        onSurface = Color(0xFFE6E1E9),
        onSurfaceVariant = Color(0xFFB8B5BD),
        error = expense,
        errorContainer = lerp(Color(0xFF1C1B1F), expense, 0.24f),
        onErrorContainer = Color(0xFFE6E1E9)
    )
}

private val MintPalette = ThemePalette(
    primary = Color(0xFF3DC9B0),
    onPrimary = Color.White,
    secondary = Color(0xFF7ED8C8),
    background = Color(0xFFF6FCFA),
    surface = Color.White,
    surfaceVariant = Color(0xFFE6F5F1),
    outline = Color(0xFF9BB5AE),
    onSurface = Color(0xFF1F2D2A),
    onSurfaceVariant = Color(0xFF6B807A),
    expense = Color(0xFFFF7B8A),
    income = Color(0xFF5FD3A0),
    accent = Color(0xFFFFCE5C)
)

private val SunsetPalette = ThemePalette(
    primary = Color(0xFFFF9A56),
    onPrimary = Color.White,
    secondary = Color(0xFFFFC08A),
    background = Color(0xFFFFF9F4),
    surface = Color.White,
    surfaceVariant = Color(0xFFFFEEE0),
    outline = Color(0xFFC0A48E),
    onSurface = Color(0xFF33261C),
    onSurfaceVariant = Color(0xFF8A7565),
    expense = Color(0xFFF4685F),
    income = Color(0xFF6BCB77),
    accent = Color(0xFFFFD166)
)

private val SkyPalette = ThemePalette(
    primary = Color(0xFF4FA8F5),
    onPrimary = Color.White,
    secondary = Color(0xFF8FCBFB),
    background = Color(0xFFF5FAFF),
    surface = Color.White,
    surfaceVariant = Color(0xFFE4F0FB),
    outline = Color(0xFF93AEC6),
    onSurface = Color(0xFF1B2733),
    onSurfaceVariant = Color(0xFF64798C),
    expense = Color(0xFFFF7B8A),
    income = Color(0xFF4FC3A1),
    accent = Color(0xFFFFCE5C)
)

private val BlossomPalette = ThemePalette(
    primary = Color(0xFFE58BC4),
    onPrimary = Color.White,
    secondary = Color(0xFFF3B6D8),
    background = Color(0xFFFFF7FB),
    surface = Color.White,
    surfaceVariant = Color(0xFFFBE6F3),
    outline = Color(0xFFC296B4),
    onSurface = Color(0xFF33202B),
    onSurfaceVariant = Color(0xFF8C6E80),
    expense = Color(0xFFF4685F),
    income = Color(0xFF6BCB77),
    accent = Color(0xFFFFD166)
)

fun paletteOf(theme: AppTheme): ThemePalette = when (theme) {
    AppTheme.MINT -> MintPalette
    AppTheme.SUNSET -> SunsetPalette
    AppTheme.SKY -> SkyPalette
    AppTheme.BLOSSOM -> BlossomPalette
}

val LocalPalette = staticCompositionLocalOf { MintPalette }

/** 主题相关的语义色，随主题切换 */
object AppColors {
    val expense: Color
        @Composable get() = LocalPalette.current.expense

    val income: Color
        @Composable get() = LocalPalette.current.income

    val accent: Color
        @Composable get() = LocalPalette.current.accent

    val palette: ThemePalette
        @Composable get() = LocalPalette.current
}

@Composable
fun JizhangTheme(
    theme: AppTheme = AppTheme.MINT,
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val palette = paletteOf(theme)
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(
            colorScheme = if (darkTheme) palette.toDarkScheme() else palette.toLightScheme(),
            content = content
        )
    }
}
