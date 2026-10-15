package com.jizhang.app.ui.budget

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.remote.dto.BudgetDto
import com.jizhang.app.data.remote.dto.BudgetOverviewDto
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.report.BudgetDialog
import com.jizhang.app.ui.theme.AppColors
import kotlin.math.roundToInt

/**
 * 预算页：周期切换、总预算进度、分类预算、超支提示。
 */
@Composable
fun BudgetScreen(
    bookId: Long,
    viewModel: BudgetViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showDialog by remember { mutableStateOf(false) }
    var dialogCategoryId by remember { mutableLongStateOf(0L) }

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
            contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                PeriodBar(
                    state = state,
                    onCycle = viewModel::switchCycle,
                    onPrev = { viewModel.switchPeriod(-1) },
                    onNext = { viewModel.switchPeriod(1) },
                    onCurrent = viewModel::backToCurrent
                )
            }
            if (state.inheritedCount > 0) {
                item { InheritHint(state.inheritedCount) }
            }
            item {
                TotalBudgetCard(
                    overview = state.overview,
                    onEdit = {
                        dialogCategoryId = 0L
                        showDialog = true
                    }
                )
            }
            item {
                CategoryBudgetCard(
                    overview = state.overview,
                    onEdit = { categoryId ->
                        dialogCategoryId = categoryId
                        showDialog = true
                    }
                )
            }
            item { TipsCard() }
        }

        ExtendedFloatingActionButton(
            onClick = {
                dialogCategoryId = 0L
                showDialog = true
            },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            icon = { Icon(Icons.Filled.Savings, contentDescription = null) },
            text = { Text("设置预算") },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        )

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }

    if (showDialog) {
        BudgetDialog(
            periodLabel = state.periodLabel,
            initialCategoryId = dialogCategoryId,
            expenseCategories = state.expenseCategories,
            budget = state.overview,
            onInherit = viewModel::inheritPrevious,
            onSave = { categoryId, amount, onResult ->
                viewModel.saveBudget(categoryId, amount, onResult)
            },
            onDelete = viewModel::deleteBudget,
            onDismiss = { showDialog = false }
        )
    }
}

/** 周期选择 + 时间前后翻页 */
@Composable
private fun PeriodBar(
    state: BudgetViewModel.UiState,
    onCycle: (BudgetCycle) -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            BudgetCycle.entries.forEach { cycle ->
                FilterChip(
                    selected = state.cycle == cycle,
                    onClick = { onCycle(cycle) },
                    label = { Text(cycle.label) }
                )
            }
        }

        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周期")
            }
            Text(
                text = state.periodLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            if (!state.isCurrent) {
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "回到本期",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onCurrent)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onNext, enabled = state.canGoNext) {
                Icon(
                    Icons.Filled.ChevronRight,
                    contentDescription = "下一周期",
                    tint = if (state.canGoNext) {
                        MaterialTheme.colorScheme.onSurface
                    } else {
                        MaterialTheme.colorScheme.outline
                    }
                )
            }
        }
    }
}

@Composable
private fun InheritHint(count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(AppColors.accent.copy(alpha = 0.14f))
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "本期已自动沿用上一周期的 $count 条预算，可直接调整。",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun TotalBudgetCard(overview: BudgetOverviewDto?, onEdit: () -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "总预算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = if (overview?.hasBudget == true) "调整" else "去设置",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        if (overview == null || !overview.hasBudget) {
            Text(
                text = "还没设置预算。设一个额度，超支时这里会提醒你。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "本周期已支出 ${formatAmount(overview?.monthExpense ?: 0)} 元",
                style = MaterialTheme.typography.bodyMedium
            )
            return@CardBox
        }

        val ratio = (overview.usedRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
        val barColor = if (overview.exceeded) AppColors.expense else AppColors.accent

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = formatAmount(overview.totalUsed),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = if (overview.exceeded) AppColors.expense else MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.width(4.dp))
            Text(
                text = "/ ${formatAmount(overview.totalBudget)} 元",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "${((overview.usedRatio ?: 0.0) * 100).roundToInt()}%",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = barColor
            )
        }

        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(8.dp)
                .clip(RoundedCornerShape(4.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )

        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth()) {
            StatItem(
                label = "剩余",
                value = "${formatAmount(overview.remaining)} 元",
                color = if (overview.exceeded) AppColors.expense else AppColors.income,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "日均可用",
                value = "${formatAmount(overview.dailyAvailable)} 元",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            StatItem(
                label = "剩余天数",
                value = "${overview.daysLeft} 天",
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
        }

        if (overview.exceeded) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "已超支 ${formatAmount(overview.totalUsed - overview.totalBudget)} 元",
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.expense
            )
        } else if (overview.suggestDaily > 0) {
            Spacer(Modifier.height(10.dp))
            Text(
                text = "按额度平摊，每天约 ${formatAmount(overview.suggestDaily)} 元",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CategoryBudgetCard(overview: BudgetOverviewDto?, onEdit: (Long) -> Unit) {
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "分类预算",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "添加",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onEdit(0L) }
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }

        Spacer(Modifier.height(10.dp))

        val items = overview?.items.orEmpty()
        if (items.isEmpty()) {
            Text(
                text = "还没有分类预算。给「餐饮」「购物」这类容易超支的分类单独设个额度。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@CardBox
        }

        items.forEachIndexed { index, item ->
            if (index > 0) Spacer(Modifier.height(12.dp))
            CategoryBudgetRow(item = item, onClick = { onEdit(item.categoryId) })
        }
    }
}

@Composable
private fun CategoryBudgetRow(item: BudgetDto, onClick: () -> Unit) {
    val ratio = (item.usedRatio ?: 0.0).toFloat().coerceIn(0f, 1f)
    val barColor = if (item.exceeded) AppColors.expense else AppColors.accent

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(item.categoryName, style = MaterialTheme.typography.bodyMedium)
            if (item.exceeded) {
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "超支",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.expense
                )
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "${formatAmount(item.used)} / ${formatAmount(item.amount)}",
                style = MaterialTheme.typography.labelSmall,
                color = if (item.exceeded) {
                    AppColors.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
        }
        Spacer(Modifier.height(6.dp))
        LinearProgressIndicator(
            progress = { ratio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp)),
            color = barColor,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
    }
}

@Composable
private fun TipsCard() {
    CardBox {
        Text(
            text = "预算说明",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "· 总预算统计本期全部支出，分类预算只统计该分类\n" +
                "· 进入新周期时会自动沿用上一周期的额度，可随时调整\n" +
                "· 超支时总预算与对应分类都会标红提醒\n" +
                "· 预算数据保存在本地，断网也能正常查看",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/** 统一卡片容器：大圆角 + 无描边，靠留白分层 */
@Composable
private fun CardBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun StatItem(label: String, value: String, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = color,
            fontWeight = FontWeight.SemiBold
        )
    }
}
