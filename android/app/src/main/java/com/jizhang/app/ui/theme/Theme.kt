package com.jizhang.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Blue = Color(0xFF2C5282)
private val BlueLight = Color(0xFF4A7BB7)
private val Teal = Color(0xFF26A69A)
private val PageBg = Color(0xFFF5F7FA)

/** 支出红 / 收入绿，全局统一 */
val ExpenseColor = Color(0xFFE53935)
val IncomeColor = Color(0xFF43A047)

private val LightColors = lightColorScheme(
    primary = Blue,
    onPrimary = Color.White,
    secondary = Teal,
    background = PageBg,
    surface = Color.White
)

private val DarkColors = darkColorScheme(
    primary = BlueLight,
    secondary = Teal
)

@Composable
fun JizhangTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        content = content
    )
}
