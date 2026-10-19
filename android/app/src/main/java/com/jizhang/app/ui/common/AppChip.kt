package com.jizhang.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 全应用统一的「选择芯片」。
 *
 * 为什么不用 M3 的 FilterChip：
 * - 未选中时它会拉一条 1dp outline 描边，页面上全是没选中的芯片时，整片区域像一排灰框；
 * - 选中底色走 secondaryContainer，主题没覆盖到就会露出 Material3 基线紫；
 * - 宽度由内容撑开且不换行，横向空间不足时会出现「支付宝」被压成竖排三个字。
 *
 * 这里的规则是「淡底 + 选中才上色」：
 * 未选中 = surfaceVariant 的淡底、无描边；
 * 选中 = [tint] 18% 底 + 50% 同色细边 + 加粗深色文字。
 *
 * @param tint 选中色：筛选用分类色，类型选择用收支色，中性选项用主题色
 * @param compact 更小的一档，用于一排信息很密的场景（标签、元信息）
 */
@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    leadingIcon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    compact: Boolean = false
) {
    val shape = CircleShape
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSurface
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) tint.copy(alpha = 0.18f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f)
            )
            .then(
                if (selected) Modifier.border(1.dp, tint.copy(alpha = 0.5f), shape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (leadingIcon != null) {
            Icon(
                imageVector = leadingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 14.dp else 16.dp)
            )
            Spacer(Modifier.width(4.dp))
        }
        Text(
            text = label,
            style = if (compact) {
                MaterialTheme.typography.labelMedium
            } else {
                MaterialTheme.typography.bodySmall
            },
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            color = contentColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (trailingIcon != null) {
            Spacer(Modifier.width(2.dp))
            Icon(
                imageVector = trailingIcon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(if (compact) 16.dp else 18.dp)
            )
        }
    }
}
