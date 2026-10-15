package com.jizhang.app.ui.bill

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.formatDateTime
import com.jizhang.app.ui.common.formatSignedAmount
import com.jizhang.app.ui.common.transactionTypeLabel
import com.jizhang.app.ui.pet.PetAvatar
import com.jizhang.app.ui.pet.PetMood
import com.jizhang.app.ui.pet.PetState
import com.jizhang.app.ui.pet.PetType
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme

@Composable
fun BillScreen(
    bookId: Long,
    onOpenAccounts: () -> Unit,
    viewModel: BillViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // 账本变化（登录 / 退出）时重新绑定数据源
    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 100.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                PetCard(
                    pet = state.pet,
                    mood = state.petMood,
                    monthIncome = state.monthIncome,
                    monthExpense = state.monthExpense,
                    pendingCount = state.pendingCount,
                    syncing = state.syncing,
                    onSync = viewModel::sync,
                    onOpenAccounts = onOpenAccounts
                )
            }

            if (state.transactions.isEmpty()) {
                item { EmptyHint() }
            } else {
                items(state.transactions, key = { it.id }) { item ->
                    TransactionRow(item = item, onDelete = { viewModel.delete(item.id) })
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

/** 顶部宠物卡片：宠物 + 本月收支 + 账户入口 */
@Composable
private fun PetCard(
    pet: PetState,
    mood: PetMood,
    monthIncome: Long,
    monthExpense: Long,
    pendingCount: Int,
    syncing: Boolean,
    onSync: () -> Unit,
    onOpenAccounts: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PetAvatar(
                    type = pet.type,
                    mood = mood,
                    modifier = Modifier.size(64.dp),
                    bodyColor = AppColors.accent
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "${pet.name} · Lv.${pet.level}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = moodText(mood),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { pet.levelProgress },
                        modifier = Modifier.fillMaxWidth(),
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = "再记 ${if (pet.coinsToNextLevel % PetState.COINS_PER_RECORD == 0) pet.coinsToNextLevel / PetState.COINS_PER_RECORD else pet.coinsToNextLevel / PetState.COINS_PER_RECORD + 1} 笔升级 · 连续 ${pet.streakDays} 天",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Filled.Refresh, contentDescription = "同步")
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                AmountColumn("收入", monthIncome, AppColors.income, Modifier.weight(1f))
                AmountColumn("支出", monthExpense, AppColors.expense, Modifier.weight(1f))
                AmountColumn("结余", monthIncome - monthExpense, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f))
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onOpenAccounts)
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.Filled.AccountBalanceWallet,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(8.dp))
                Text(
                    text = "账户管理",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.weight(1f))
                if (pendingCount > 0) {
                    Text(
                        text = "待同步 $pendingCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun moodText(mood: PetMood): String = when (mood) {
    PetMood.HAPPY -> "本月有结余，它很开心～"
    PetMood.NORMAL -> "今天也来记一笔吧"
    PetMood.WORRIED -> "本月支出有点多，它有点担心"
    PetMood.EXCITED -> "太棒了！"
}

@Composable
private fun AmountColumn(label: String, value: Long, color: androidx.compose.ui.graphics.Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatAmount(value),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun EmptyHint() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("还没有账单", style = MaterialTheme.typography.titleSmall)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "点右下角「记一笔」，小锦锦就能吃到金币啦",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TransactionRow(item: LocalTransaction, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.remark?.takeIf { it.isNotBlank() } ?: transactionTypeLabel(item.type),
                    style = MaterialTheme.typography.bodyLarge
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = buildString {
                        append(formatDateTime(item.tradeTime))
                        if (item.pendingSync) append(" · 待同步")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = formatSignedAmount(item.type, item.amount),
                color = when (item.type) {
                    1 -> AppColors.expense
                    2 -> AppColors.income
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Filled.DeleteOutline,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/* ==================== 预览 ==================== */

private fun previewPetState() = PetState(
    name = "小锦锦",
    type = PetType.CAT,
    coins = 130,
    streakDays = 6,
    lastRecordDate = "2026-10-14"
)

private fun previewTransactions(): List<LocalTransaction> {
    val now = System.currentTimeMillis()
    return listOf(
        LocalTransaction(
            id = 1L, bookId = 1L, type = 2, amount = 1850000, accountId = 2L, categoryId = 10L,
            tradeTime = now - 7_200_000, remark = "十月工资", pendingSync = false
        ),
        LocalTransaction(
            id = 2L, bookId = 1L, type = 1, amount = 12800, accountId = 2L, categoryId = 3L,
            tradeTime = now - 3_600_000, remark = "超市采购", pendingSync = true
        ),
        // 无备注时回落到「支出」类型文案
        LocalTransaction(
            id = 3L, bookId = 1L, type = 1, amount = 3500, accountId = 1L, categoryId = 1L,
            tradeTime = now, remark = null, pendingSync = true
        )
    )
}

@Composable
private fun BillPreviewContent(
    pet: PetState = previewPetState(),
    mood: PetMood = PetMood.HAPPY,
    monthIncome: Long = 1850000,
    monthExpense: Long = 1234500,
    pendingCount: Int = 2,
    transactions: List<LocalTransaction> = previewTransactions()
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            PetCard(
                pet = pet,
                mood = mood,
                monthIncome = monthIncome,
                monthExpense = monthExpense,
                pendingCount = pendingCount,
                syncing = false,
                onSync = {},
                onOpenAccounts = {}
            )
        }
        if (transactions.isEmpty()) {
            item { EmptyHint() }
        } else {
            items(transactions, key = { it.id }) { item ->
                TransactionRow(item = item, onDelete = {})
            }
        }
    }
}

@Preview(name = "账单页", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun BillScreenPreview() {
    JizhangTheme { BillPreviewContent() }
}

/** 本月超支：宠物变担心表情，结余为负 */
@Preview(name = "账单页 · 超支", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun BillScreenWorriedPreview() {
    JizhangTheme {
        BillPreviewContent(
            mood = PetMood.WORRIED,
            monthIncome = 320000,
            monthExpense = 1580000
        )
    }
}

@Preview(name = "账单页 · 空态", showBackground = true, widthDp = 380, heightDp = 460)
@Composable
private fun BillScreenEmptyPreview() {
    JizhangTheme { BillPreviewContent(transactions = emptyList(), pendingCount = 0) }
}

/** 升到 Lv.10 的兴奋态 + 深色主题 */
@Preview(name = "账单页 · 深色", showBackground = true, widthDp = 380, heightDp = 760)
@Composable
private fun BillScreenDarkPreview() {
    JizhangTheme(darkTheme = true) {
        BillPreviewContent(
            pet = previewPetState().copy(coins = 480),
            mood = PetMood.EXCITED
        )
    }
}
