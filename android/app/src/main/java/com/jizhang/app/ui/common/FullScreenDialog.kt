package com.jizhang.app.ui.common

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier

/**
 * 项目内唯一的全屏页容器 —— 渲染在 **Activity 内容区里的覆盖层**，不是 `Dialog`。
 *
 * ## 为什么不用 Dialog（真机 dumpsys + 无障碍节点坐标实测）
 *
 * 症状：记账页底部数字键盘最后一行只剩一条边、「备注/标签」整行看不见，且中间拖不动。
 *
 * 实测（Android 16 / targetSdk 36）：
 * - 弹窗窗口是 `WRAP_CONTENT` 且 `pfl=EDGE_TO_EDGE_ENFORCED`（edge-to-edge 被强制）；
 * - 框架把内容按**整个屏幕高度**测量并撑满，而窗口帧只落在系统栏之间
 *   （实测 `frame=[0,140][1200,2618]`），底部多出来的部分被硬裁 ——
 *   同时 Scaffold 的 `weight(1f)` 拿不到正确高度，滚动区随之失效（「看不见又拖不动」）；
 * - 又因为宿主是 `Dialog` 对象而非 View，`LocalView.current.parent as? DialogWindowProvider`
 *   在该 Compose 版本上恒为 null，**没有任何办法从内容侧把窗口改成 MATCH_PARENT**。
 *
 * 所以这里彻底不走弹窗窗口：直接把页面铺在 Activity 的内容区里（覆盖层）。
 * 它拿到的高度约束就是普通页面的那套（确定且与窗口帧一致），系统栏 insets 也正常下发，
 * `weight(1f)`、滚动、`safeDialogContent()` 全部可靠 —— 与系统版本、Compose 版本无关。
 *
 * ## 使用约定
 *
 * - 调用点必须放在**页面根部**（`MainScreen` 里 `Scaffold` 之后、与之同级），
 *   这样覆盖层才能盖住底部导航栏与悬浮按钮；不要塞进某个页面的内部布局里；
 * - 覆盖层自身已吃掉点击（不会穿透到底下的页面），并接管返回键；
 * - 内部页面：顶部用 `TopAppBar` 默认 windowInsets（别传 `WindowInsets(0,0,0,0)`），
 *   底部固定区/可滚动列表加 `Modifier.safeDialogContent()`，有输入框的加 `imePadding()`；
 * - 「顶栏 + 底部固定区 + 中间滚动」的中部用 `Modifier.weight(1f)`。
 */
@Composable
fun AppFullScreenDialog(
    onDismissRequest: () -> Unit,
    content: @Composable () -> Unit
) {
    // 覆盖层是 Scaffold 的兄弟节点，返回键需要自己接管
    BackHandler(enabled = true) { onDismissRequest() }

    Surface(
        modifier = Modifier
            .fillMaxSize()
            // 吃掉点击，避免穿透到底下的页面（子节点会先处理事件，不影响页面内部交互）
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {}
            ),
        color = MaterialTheme.colorScheme.background
    ) {
        content()
    }
}
