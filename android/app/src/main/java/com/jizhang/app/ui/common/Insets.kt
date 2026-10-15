package com.jizhang.app.ui.common

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * 全屏弹窗里「底部固定区 / 可滚动区」的安全区修饰符。
 *
 * 工程 `targetSdk = 36`，Android 15 起（Android 16 强制）弹窗窗口也是 edge-to-edge，
 * `decorFitsSystemWindows` 会被系统忽略：**系统不会替内容留边**，底部的按钮、数字键盘、
 * 列表最后一项会被导航栏 / 手势条压住，看起来就是「被切掉」。必须由内容自己补安全区。
 *
 * 在还没有强制 edge-to-edge 的旧系统上，这些 insets 已被 decor view 消费，本修饰符实际
 * 补 0，所以统一加上不会出现「双重留白」。
 *
 * 约定（全屏弹窗见 [AppFullScreenDialog]）：
 * - 顶部交给 `TopAppBar` 自己的默认 windowInsets，不要传 `WindowInsets(0, 0, 0, 0)`；
 * - 底部固定区（按钮行、计算器键盘）与可滚动列表 / 滚动区加本修饰符；
 * - 非全屏的 `AlertDialog` 由系统自己处理，不需要加。
 */
@Composable
fun Modifier.safeDialogContent(): Modifier =
    windowInsetsPadding(
        WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom)
    )
