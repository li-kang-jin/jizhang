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
 * **余额是这枚芯片的第三个身份标记**：名字（招行）看不出来里面还有多少钱，
 * 而「选错账户」最典型的信号恰恰是余额对不上（微信只剩 12 块却记了 200）。
 * 所以余额直接铺在芯片上，不用先点开二级选择器。
 *
 * @param showType 名字下面是否补一行类型名。横向密排时关掉（余额改走同一行），列表里打开
 * @param compact 更小一档，用于记账页那一行元信息
 * @param balanceFen 账户当前余额（分）。null 表示查不到，不显示
 * @param afterBalanceFen 「这笔记完」的余额（分）。非空时显示成 `¥12.00 → ¥-188.00`，
 *        让用户在按保存之前就看到这笔钱从哪个账户扣、扣完剩多少
 * @param warn 这笔会把余额刷成负数（信用卡除外，见 `isBalanceShort`）：余额行转成错误色
 */
@Composable
fun AccountChip(
    account: LocalAccount,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    showType: Boolean = false,
    balanceFen: Long? = null,
    afterBalanceFen: Long? = null,
    hideAmounts: Boolean = false,
    warn: Boolean = false
) {
    val iconKey = accountIconKeyOf(account.icon, account.type)
    val typeColor = accountColorOf(account.icon, account.type)
    val shape = RoundedCornerShape(if (compact) 10.dp else 12.dp)
    val balance = balanceLabel(balanceFen, afterBalanceFen, hideAmounts)
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
                    text = listOfNotNull(accountTypeLabel(account.type), balance).joinToString(" · "),
                    style = MaterialTheme.typography.labelSmall,
                    color = if (warn) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
        // 横排（记账页元信息条）没有第二行的位置：余额跟名字挤在同一行，
        // 不额外加高芯片 —— 那一行高度是分类网格让出来的，多一行就要滚分类
        if (!showType && balance != null) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = balance,
                style = MaterialTheme.typography.labelSmall,
                color = if (warn) MaterialTheme.colorScheme.error
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
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

/**
 * 芯片上的余额文案：有「记完」预览时写成 `¥12.00 → -¥188.00`，否则只写当前余额。
 * <p>
 * 金额未知（查不到）或预览与当前值相同（金额为 0）时只写一个数，免得出现 `¥12 → ¥12` 这种噪音。
 */
private fun balanceLabel(
    balanceFen: Long?,
    afterBalanceFen: Long?,
    hideAmounts: Boolean
): String? {
    val balance = balanceFen ?: return null
    val before = compactMoney(balance, hideAmounts)
    val after = afterBalanceFen?.let { compactMoney(it, hideAmounts) }
    return if (after == null || after == before) before else "$before → $after"
}
