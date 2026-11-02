package com.jizhang.app.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.jizhang.app.data.local.entity.LocalAccount

/**
 * 账户芯片：和标签芯片刻意做成两套形状。
 *
 * 记账页上「常用账户」和「常用标签」原本都是同一枚圆角胶囊，唯一差别只有文字，
 * 手指扫过去很容易点错行。这里把账户改成**方角卡片 + 类型图标**：
 * 图标由账户类型决定（现金 / 储蓄卡 / 信用卡 / 支付宝 / 微信 / 投资），
 * 并按类型上色（现金绿、储蓄卡蓝、信用卡紫、投资橙）——颜色本身就是「钱放在哪」的信息，
 * 既能把账户行和标签行一眼分开，也能区分账户各自的类型。
 *
 * @param showType 名字下面是否补一行类型名。横向密排时关掉，列表里打开
 * @param compact 更小一档，用于记账页那一行元信息
 */
@Composable
fun AccountChip(
    account: LocalAccount,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showType: Boolean = false
) {
    val iconKey = accountIconKeyOf(account.icon, account.type)
    val typeColor = accountColorOf(account.icon, account.type)
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(
                if (selected) typeColor.copy(alpha = 0.16f)
                else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            )
            .then(
                if (selected) Modifier.border(1.dp, typeColor.copy(alpha = 0.55f), shape) else Modifier
            )
            .clickable(onClick = onClick)
            .padding(
                horizontal = if (compact) 8.dp else 12.dp,
                vertical = if (compact) 5.dp else 8.dp
            ),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(if (compact) 20.dp else 26.dp)
                .clip(CircleShape)
                .background(typeColor.copy(alpha = if (selected) 0.3f else 0.16f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = accountIconOf(iconKey),
                contentDescription = null,
                tint = accountIconTintOf(iconKey, typeColor),
                modifier = Modifier.size(if (compact) 12.dp else 15.dp)
            )
        }
        Spacer(Modifier.width(if (compact) 6.dp else 8.dp))
        Column {
            Text(
                text = account.name,
                style = if (compact) {
                    MaterialTheme.typography.labelMedium
                } else {
                    MaterialTheme.typography.bodySmall
                },
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                // 横滚行里宽度不受限，长名字得自己设一个上限，否则一枚芯片能占掉半屏
                modifier = Modifier.widthIn(max = if (compact) 96.dp else 132.dp)
            )
            if (showType) {
                Text(
                    text = accountTypeLabel(account.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        if (selected) {
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = typeColor,
                modifier = Modifier.size(if (compact) 13.dp else 16.dp)
            )
        }
    }
}
