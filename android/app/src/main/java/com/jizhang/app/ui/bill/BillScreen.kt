package com.jizhang.app.ui.bill

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.ui.common.categoryIconOf
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
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.Locale

/**
 * 账单页。
 *
 * 三件事：
 * 1. 顶部一行筛选（时间 / 类型 / 账户 / 分类 / 排序 + 搜索），结果汇总实时显示；
 * 2. 主列表按「日」分组，组头给出当天收支小计，条目左侧带分类图标 —— 比平铺列表直观得多；
 * 3. 一张随筛选联动的迷你图表（近 14 天分布 + 分类占比 Top5）。
 *
 * 列表行样式仍由 `billTemplate` 决定（1 清爽列表 / 2 信息卡片 / 3 紧凑单行）。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BillScreen(
    bookId: Long,
    /** 家庭账本才显示「谁记的」 */
    shared: Boolean = false,
    /** 账单模板：1 清爽列表 / 2 信息卡片 / 3 紧凑单行 */
    billTemplate: Int = 1,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    /** 点条目进「编辑账单」；记账页由 MainScreen 以编辑模式打开 */
    onEdit: (LocalTransaction) -> Unit,
    /** 从统计页等地方跳进来时带的筛选预设（用完即弃） */
    preset: BillPreset? = null,
    onPresetConsumed: () -> Unit = {},
    viewModel: BillViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var searchExpanded by remember { mutableStateOf(false) }
    var showCustomRange by remember { mutableStateOf(false) }
    // 非空表示正在确认删除这笔账（误点删除的代价太高，必须二次确认）
    var deleting by remember { mutableStateOf<LocalTransaction?>(null) }

    // 账本变化（登录 / 退出）时重新绑定数据源
    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    // 统计页跳过来：带上分类 / 账户 / 标签与时间范围，搜索框自动展开好让用户看见筛选条件
    LaunchedEffect(preset) {
        preset?.let {
            if (!it.keyword.isNullOrBlank()) {
                searchExpanded = true
            }
            viewModel.applyPreset(it)
            onPresetConsumed()
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        BillFilterBar(
            state = state,
            searchExpanded = searchExpanded,
            onToggleSearch = {
                val next = !searchExpanded
                searchExpanded = next
                if (!next) viewModel.setKeyword("")
            },
            onRange = viewModel::setDateRange,
            onOpenCustomRange = { showCustomRange = true },
            onType = viewModel::setTypeFilter,
            onAccount = viewModel::setAccountFilter,
            onToggleCategory = viewModel::toggleCategory,
            onClearCategories = viewModel::clearCategories,
            onKeyword = viewModel::setKeyword,
            onSort = viewModel::setSort,
            onClear = viewModel::clearFilter
        )

        Box(modifier = Modifier.weight(1f)) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = 4.dp, bottom = 100.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item(key = "pet") {
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        PetCard(
                            pet = state.pet,
                            mood = state.petMood,
                            monthIncome = state.monthIncome,
                            monthExpense = state.monthExpense,
                            pendingCount = state.pendingCount,
                            syncing = state.syncing,
                            onSync = viewModel::sync,
                            onOpenAccounts = onOpenAccounts,
                            onOpenCategories = onOpenCategories
                        )
                    }
                }

                if (state.chart.days.isNotEmpty() || state.chart.topCategories.isNotEmpty()) {
                    item(key = "chart") {
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            BillChartCard(state.chart)
                        }
                    }
                }

                if (state.filteredTransactions.isEmpty()) {
                    item(key = "empty") {
                        Box(Modifier.padding(horizontal = 12.dp)) {
                            EmptyHint(
                                hasAnyBill = state.transactions.isNotEmpty(),
                                onClear = viewModel::clearFilter
                            )
                        }
                    }
                } else {
                    state.dayGroups.forEach { group ->
                        stickyHeader(key = "day-${group.date}") {
                            DayHeader(date = group.date, income = group.income, expense = group.expense)
                        }
                        items(group.items, key = { it.id }) { item ->
                            Box(Modifier.padding(horizontal = 12.dp)) {
                                TransactionRow(
                                    item = item,
                                    category = item.categoryId?.let { state.categoryById[it] },
                                    shared = shared,
                                    template = billTemplate,
                                    // createdBy 为空说明是本机刚记、还没同步的账，那必然是「我」
                                    creatorName = if (!shared) {
                                        null
                                    } else {
                                        item.createdBy?.let { state.memberLabels[it] } ?: "我"
                                    },
                                    onDelete = { deleting = item },
                                    onEdit = { onEdit(item) }
                                )
                            }
                        }
                    }
                }
            }

            SnackbarHost(
                hostState = snackbarHostState,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }

    if (showCustomRange) {
        CustomRangeDialog(
            onConfirm = { start, end ->
                viewModel.setCustomRange(start, end)
                showCustomRange = false
            },
            onDismiss = { showCustomRange = false }
        )
    }

    // 删除是不可逆操作，且列表条目挨着「编辑」热区，必须让用户先确认
    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除这笔账单") },
            text = {
                val label = target.remark?.takeIf { it.isNotBlank() }
                    ?: target.categoryId?.let { state.categoryById[it] }?.name
                    ?: transactionTypeLabel(target.type)
                Text(
                    "$label ${formatSignedAmount(target.type, target.amount)}\n" +
                        "删除后无法恢复；已登录的账本，账户余额会随下一次同步回滚。"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.delete(target.id)
                        deleting = null
                    },
                    // 危险操作用错误色，与「取消」拉开视觉区分，避免手滑点错
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

/* ==================== 筛选栏 ==================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillFilterBar(
    state: BillViewModel.UiState,
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
    onRange: (BillDateRange) -> Unit,
    onOpenCustomRange: () -> Unit,
    onType: (Int) -> Unit,
    onAccount: (Long?) -> Unit,
    onToggleCategory: (Long) -> Unit,
    onClearCategories: () -> Unit,
    onKeyword: (String) -> Unit,
    onSort: (BillSort) -> Unit,
    onClear: () -> Unit
) {
    val filter = state.filter

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 4.dp, top = 6.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 时间范围
                FilterMenuChip(
                    label = filter.rangeLabel(),
                    active = filter.range != BillDateRange.THIS_MONTH
                ) { dismiss ->
                    BillDateRange.entries.forEach { range ->
                        DropdownMenuItem(
                            text = { Text(range.label) },
                            trailingIcon = { if (filter.range == range) SelectedCheck() },
                            onClick = {
                                if (range.isCustom) {
                                    dismiss()
                                    onOpenCustomRange()
                                } else {
                                    onRange(range)
                                    dismiss()
                                }
                            }
                        )
                    }
                }

                // 类型
                FilterMenuChip(
                    label = if (filter.type == 0) "全部类型" else transactionTypeLabel(filter.type),
                    active = filter.type != 0
                ) { dismiss ->
                    listOf(0, 1, 2, 3).forEach { type ->
                        DropdownMenuItem(
                            text = { Text(if (type == 0) "全部类型" else transactionTypeLabel(type)) },
                            trailingIcon = { if (filter.type == type) SelectedCheck() },
                            onClick = {
                                onType(type)
                                dismiss()
                            }
                        )
                    }
                }

                // 账户
                FilterMenuChip(
                    label = state.accounts.firstOrNull { it.id == filter.accountId }?.name ?: "全部账户",
                    active = filter.accountId != null
                ) { dismiss ->
                    DropdownMenuItem(
                        text = { Text("全部账户") },
                        trailingIcon = { if (filter.accountId == null) SelectedCheck() },
                        onClick = {
                            onAccount(null)
                            dismiss()
                        }
                    )
                    state.accounts.forEach { account ->
                        DropdownMenuItem(
                            text = { Text(account.name) },
                            trailingIcon = { if (filter.accountId == account.id) SelectedCheck() },
                            onClick = {
                                onAccount(account.id)
                                dismiss()
                            }
                        )
                    }
                }

                // 分类（可多选，菜单不自动关）
                if (state.categories.isNotEmpty()) {
                    FilterMenuChip(
                        label = if (filter.categoryIds.isEmpty()) "全部分类" else "分类 ${filter.categoryIds.size}",
                        active = filter.categoryIds.isNotEmpty(),
                        dismissOnClick = false
                    ) { _ ->
                        DropdownMenuItem(
                            text = { Text("全部分类") },
                            trailingIcon = { if (filter.categoryIds.isEmpty()) SelectedCheck() },
                            onClick = onClearCategories
                        )
                        state.categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                leadingIcon = {
                                    Icon(
                                        categoryIconOf(category.icon),
                                        contentDescription = null,
                                        modifier = Modifier.size(18.dp),
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                },
                                trailingIcon = { if (category.id in filter.categoryIds) SelectedCheck() },
                                onClick = { onToggleCategory(category.id) }
                            )
                        }
                    }
                }

                // 排序
                FilterMenuChip(
                    label = filter.sort.label,
                    active = filter.sort != BillSort.TIME_DESC
                ) { dismiss ->
                    BillSort.entries.forEach { sort ->
                        DropdownMenuItem(
                            text = { Text(sort.label) },
                            trailingIcon = { if (filter.sort == sort) SelectedCheck() },
                            onClick = {
                                onSort(sort)
                                dismiss()
                            }
                        )
                    }
                }
            }

            IconButton(onClick = onToggleSearch) {
                Icon(
                    imageVector = if (searchExpanded) Icons.Filled.Close else Icons.Filled.Search,
                    contentDescription = if (searchExpanded) "收起搜索" else "搜索备注或标签"
                )
            }
        }

        if (searchExpanded) {
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = filter.keyword,
                onValueChange = onKeyword,
                placeholder = { Text("搜索备注 / 标签") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(end = 8.dp)
            )
        }

        Spacer(Modifier.height(4.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = buildString {
                    append("共 ${state.summary.count} 笔")
                    append(" · 支出 ¥${formatAmount(state.summary.expense)}")
                    append(" · 收入 ¥${formatAmount(state.summary.income)}")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (filter.active) {
                Text(
                    text = "清空筛选",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable(onClick = onClear)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
    }
}

@Composable
private fun SelectedCheck() {
    Icon(
        Icons.Filled.Check,
        contentDescription = null,
        modifier = Modifier.size(18.dp),
        tint = MaterialTheme.colorScheme.primary
    )
}

/**
 * 筛选用的下拉 chip。
 *
 * @param dismissOnClick 分类是多选，菜单要一直开着；其余单选点完就关
 */
@Composable
private fun FilterMenuChip(
    label: String,
    active: Boolean,
    dismissOnClick: Boolean = true,
    menu: @Composable ColumnScope.(dismiss: () -> Unit) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        FilterChip(
            selected = active,
            onClick = { expanded = true },
            label = { Text(label) },
            trailingIcon = {
                Icon(Icons.Filled.ArrowDropDown, contentDescription = null, modifier = Modifier.size(18.dp))
            }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { if (dismissOnClick) expanded = false }
        }
    }
}

/** 自定义时间范围：Material3 的日期区间选择器 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CustomRangeDialog(
    onConfirm: (Long, Long) -> Unit,
    onDismiss: () -> Unit
) {
    val pickerState = rememberDateRangePickerState()
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val start = pickerState.selectedStartDateMillis
                val end = pickerState.selectedEndDateMillis ?: start
                if (start != null && end != null) {
                    onConfirm(utcMillisToLocalStartOfDay(start), utcMillisToLocalStartOfDay(end))
                }
            }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    ) {
        DateRangePicker(state = pickerState)
    }
}

/**
 * `DateRangePicker` 给的是「所选日期 UTC 0 点」的 millis，
 * 直接当本地时间用会整体偏一天（东八区会漏掉开始日 0~8 点）。这里换算成本地当天 0 点。
 */
private fun utcMillisToLocalStartOfDay(millis: Long): Long =
    Instant.ofEpochMilli(millis)
        .atZone(ZoneOffset.UTC)
        .toLocalDate()
        .atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()

/* ==================== 顶部卡片 ==================== */

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
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit
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
                AmountColumn("本月收入", monthIncome, AppColors.income, Modifier.weight(1f))
                AmountColumn("本月支出", monthExpense, AppColors.expense, Modifier.weight(1f))
                AmountColumn(
                    "本月结余",
                    monthIncome - monthExpense,
                    MaterialTheme.colorScheme.onSurface,
                    Modifier.weight(1f)
                )
            }

            // 收支占比条：三个数字之外，一眼看出「花得多还是赚得多」
            val total = monthIncome + monthExpense
            if (total > 0) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (monthIncome > 0) {
                        Box(
                            Modifier
                                .weight(monthIncome.toFloat())
                                .fillMaxHeight()
                                .background(AppColors.income.copy(alpha = 0.85f))
                        )
                    }
                    if (monthExpense > 0) {
                        Box(
                            Modifier
                                .weight(monthExpense.toFloat())
                                .fillMaxHeight()
                                .background(AppColors.expense.copy(alpha = 0.85f))
                        )
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                ManageEntry(
                    icon = Icons.Filled.AccountBalanceWallet,
                    label = "账户管理",
                    onClick = onOpenAccounts
                )
                ManageEntry(
                    icon = Icons.Filled.Category,
                    label = "分类管理",
                    onClick = onOpenCategories
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

/** PetCard 底部的管理入口（账户管理 / 分类管理） */
@Composable
private fun ManageEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(18.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Spacer(Modifier.size(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}

private fun moodText(mood: PetMood): String = when (mood) {
    PetMood.HAPPY -> "本月有结余，它很开心～"
    PetMood.NORMAL -> "今天也来记一笔吧"
    PetMood.WORRIED -> "本月支出有点多，它有点担心"
    PetMood.EXCITED -> "太棒了！"
}

@Composable
private fun AmountColumn(label: String, value: Long, color: Color, modifier: Modifier) {
    Column(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(2.dp))
        Text(
            text = formatAmount(value),
            style = MaterialTheme.typography.titleMedium,
            color = color,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ==================== 图表卡 ==================== */

/** 随筛选联动的迷你图表：近 14 天分布（柱）+ 分类占比 Top5（条） */
@Composable
private fun BillChartCard(chart: BillChart) {
    val accent = if (chart.type == 2) AppColors.income else AppColors.expense
    val title = if (chart.type == 2) "收入分布" else "支出分布"

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                Text(
                    text = "合计 ¥${formatAmount(chart.total)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (chart.days.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                val maxValue = chart.days.maxOf { it.amount }.coerceAtLeast(1L)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chart.days.forEach { day ->
                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height((64 * (day.amount.toFloat() / maxValue)).dp.coerceAtLeast(2.dp))
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(accent.copy(alpha = 0.85f))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (chart.topCategories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(10.dp))
                chart.topCategories.forEach { slice ->
                    Column(Modifier.padding(bottom = 8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(24.dp)
                                    .clip(CircleShape)
                                    .background(accent.copy(alpha = 0.12f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = categoryIconOf(slice.icon),
                                    contentDescription = null,
                                    tint = accent,
                                    modifier = Modifier.size(14.dp)
                                )
                            }
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = slice.name,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "¥${formatAmount(slice.amount)}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "${(slice.ratio * 100).toInt()}%",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = accent
                            )
                        }
                        Spacer(Modifier.height(4.dp))
                        LinearProgressIndicator(
                            progress = { slice.ratio.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(5.dp)
                                .clip(RoundedCornerShape(3.dp)),
                            color = accent,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/* ==================== 列表 ==================== */

/** 按日分组的组头（吸顶）：日期 + 当天收支小计 */
@Composable
private fun DayHeader(date: LocalDate, income: Long, expense: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dayLabel(date),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        if (expense > 0) {
            Text(
                text = "支出 ${formatAmount(expense)}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.expense
            )
        }
        if (expense > 0 && income > 0) {
            Spacer(Modifier.width(10.dp))
        }
        if (income > 0) {
            Text(
                text = "收入 ${formatAmount(income)}",
                style = MaterialTheme.typography.labelSmall,
                color = AppColors.income
            )
        }
    }
}

private fun dayLabel(date: LocalDate): String {
    val today = LocalDate.now()
    val prefix = when (date) {
        today -> "今天 · "
        today.minusDays(1) -> "昨天 · "
        else -> ""
    }
    val week = date.dayOfWeek.getDisplayName(java.time.format.TextStyle.SHORT, Locale.CHINA)
    return "$prefix${date.monthValue} 月 ${date.dayOfMonth} 日 · $week"
}

@Composable
private fun EmptyHint(hasAnyBill: Boolean, onClear: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (hasAnyBill) {
            Text("没有符合条件的账单", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "试试放宽时间范围或清空筛选条件",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            TextButton(onClick = onClear) { Text("清空筛选") }
        } else {
            Text("还没有账单", style = MaterialTheme.typography.titleSmall)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "点右下角「记一笔」，小锦锦就能吃到金币啦",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun TransactionRow(
    item: LocalTransaction,
    category: LocalCategory?,
    shared: Boolean,
    template: Int = 1,
    creatorName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    when (template) {
        2 -> InfoCardRow(item, category, shared, creatorName, onDelete, onEdit)
        3 -> CompactRow(item, category, shared, creatorName, onDelete, onEdit)
        else -> ListRow(item, category, shared, creatorName, onDelete, onEdit)
    }
}

/** 账单条目用的类型色（支出红 / 收入绿 / 转账灰） */
@Composable
private fun typeColor(type: Int): Color = when (type) {
    1 -> AppColors.expense
    2 -> AppColors.income
    else -> MaterialTheme.colorScheme.onSurface
}

/** 分类图标徽标：有分类时用分类图标，没有就退回类型图标 */
@Composable
private fun CategoryBadge(type: Int, icon: String?, size: Dp) {
    val color = typeColor(type)
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = categoryIconOf(icon),
            contentDescription = null,
            tint = color,
            modifier = Modifier.size(size * 0.5f)
        )
    }
}

/** 类型小标签（信息卡片模板用） */
@Composable
private fun TypeTag(type: Int) {
    val color = typeColor(type)
    Text(
        text = transactionTypeLabel(type),
        style = MaterialTheme.typography.labelSmall,
        color = color,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 2.dp)
    )
}

/** 条目副标题：时间 / 记账人 / 待同步 */
private fun subtitle(item: LocalTransaction, shared: Boolean, creatorName: String?): String =
    buildString {
        append(formatDateTime(item.tradeTime))
        if (shared && !creatorName.isNullOrBlank()) append(" · $creatorName 记的")
        if (item.pendingSync) append(" · 待同步")
    }

/** 模板 2 · 信息卡片：两行式卡片，类型标签 + 分类图标 + 备注 + 时间 */
@Composable
private fun InfoCardRow(
    item: LocalTransaction,
    category: LocalCategory?,
    shared: Boolean,
    creatorName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(type = item.type, icon = category?.icon, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                TypeTag(item.type)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = item.remark?.takeIf { it.isNotBlank() } ?: category?.name
                    ?: transactionTypeLabel(item.type),
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatSignedAmount(item.type, item.amount),
                    color = typeColor(item.type),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = subtitle(item, shared, creatorName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(28.dp)) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        modifier = Modifier.size(18.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

/** 模板 3 · 紧凑单行：高密度，一屏看更多 */
@Composable
private fun CompactRow(
    item: LocalTransaction,
    category: LocalCategory?,
    shared: Boolean,
    creatorName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() }
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(type = item.type, icon = category?.icon, size = 26.dp)
        Spacer(Modifier.size(8.dp))
        Text(
            text = formatDateTime(item.tradeTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = buildString {
                append(
                    item.remark?.takeIf { it.isNotBlank() } ?: category?.name
                    ?: transactionTypeLabel(item.type)
                )
                if (shared && !creatorName.isNullOrBlank()) append(" · $creatorName")
                if (item.pendingSync) append(" · 待同步")
            },
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatSignedAmount(item.type, item.amount),
            color = typeColor(item.type),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold
        )
        IconButton(onClick = onDelete, modifier = Modifier.size(30.dp)) {
            Icon(
                Icons.Filled.DeleteOutline,
                contentDescription = "删除",
                modifier = Modifier.size(16.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** 模板 1 · 清爽列表（默认）：一行一条，重点看金额 */
@Composable
private fun ListRow(
    item: LocalTransaction,
    category: LocalCategory?,
    shared: Boolean,
    creatorName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit
) {
    Card(
        // 整卡可点 = 改这笔账（删除按钮会自己消费点击，不会误触发）
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onEdit() },
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
            CategoryBadge(type = item.type, icon = category?.icon, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.remark?.takeIf { it.isNotBlank() } ?: category?.name
                    ?: transactionTypeLabel(item.type),
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle(item, shared, creatorName),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = formatSignedAmount(item.type, item.amount),
                color = typeColor(item.type),
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

private val previewCategories = listOf(
    LocalCategory(id = 1L, bookId = 1L, name = "餐饮", type = 1, icon = "restaurant"),
    LocalCategory(id = 2L, bookId = 1L, name = "交通", type = 1, icon = "bus"),
    LocalCategory(id = 3L, bookId = 1L, name = "购物", type = 1, icon = "shopping"),
    LocalCategory(id = 10L, bookId = 1L, name = "工资", type = 2, icon = "salary")
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
        // 无备注时回落到分类名 / 类型文案
        LocalTransaction(
            id = 3L, bookId = 1L, type = 1, amount = 3500, accountId = 1L, categoryId = 1L,
            tradeTime = now, remark = null, pendingSync = true
        ),
        LocalTransaction(
            id = 4L, bookId = 1L, type = 1, amount = 2600, accountId = 1L, categoryId = 2L,
            tradeTime = now - 90_000_000, remark = "地铁", pendingSync = false
        )
    )
}

private fun previewUiState(
    transactions: List<LocalTransaction> = previewTransactions(),
    filter: BillFilter = BillFilter(range = BillDateRange.ALL)
): BillViewModel.UiState {
    val zone = ZoneId.systemDefault()
    val categoryMap = previewCategories.associateBy { it.id }
    val groups = transactions
        .groupBy { Instant.ofEpochMilli(it.tradeTime).atZone(zone).toLocalDate() }
        .map { (date, list) ->
            BillDayGroup(
                date = date,
                income = list.filter { it.type == 2 }.sumOf { it.amount },
                expense = list.filter { it.type == 1 }.sumOf { it.amount },
                items = list
            )
        }
        .sortedByDescending { it.date }

    val expense = transactions.filter { it.type == 1 }
    val expenseTotal = expense.sumOf { it.amount }
    val days = expense
        .groupBy { Instant.ofEpochMilli(it.tradeTime).atZone(zone).toLocalDate() }
        .map { (date, list) -> BillChartDay(date, list.sumOf { it.amount }) }
        .sortedBy { it.date }
        .takeLast(14)
    val slices = expense
        .groupBy { it.categoryId }
        .map { (id, list) ->
            val amount = list.sumOf { it.amount }
            BillCategorySlice(
                categoryId = id,
                name = id?.let { categoryMap[it]?.name } ?: "未分类",
                icon = id?.let { categoryMap[it]?.icon },
                amount = amount,
                ratio = if (expenseTotal == 0L) 0f else amount.toFloat() / expenseTotal
            )
        }
        .sortedByDescending { it.amount }

    return BillViewModel.UiState(
        bookId = 1L,
        transactions = transactions,
        filteredTransactions = transactions,
        dayGroups = groups,
        summary = BillSummary(
            count = transactions.size,
            income = transactions.filter { it.type == 2 }.sumOf { it.amount },
            expense = expenseTotal
        ),
        chart = BillChart(type = 1, days = days, topCategories = slices, total = expenseTotal),
        filter = filter,
        accounts = listOf(),
        categories = previewCategories,
        categoryById = categoryMap,
        monthIncome = 1850000,
        monthExpense = 1234500,
        pet = previewPetState(),
        petMood = PetMood.HAPPY
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BillPreviewContent(
    state: BillViewModel.UiState = previewUiState(),
    billTemplate: Int = 1
) {
    Column(modifier = Modifier.fillMaxSize()) {
        BillFilterBar(
            state = state,
            searchExpanded = false,
            onToggleSearch = {},
            onRange = {},
            onOpenCustomRange = {},
            onType = {},
            onAccount = {},
            onToggleCategory = {},
            onClearCategories = {},
            onKeyword = {},
            onSort = {},
            onClear = {}
        )
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            item {
                Box(Modifier.padding(horizontal = 12.dp)) {
                    PetCard(
                        pet = state.pet,
                        mood = state.petMood,
                        monthIncome = state.monthIncome,
                        monthExpense = state.monthExpense,
                        pendingCount = 2,
                        syncing = false,
                        onSync = {},
                        onOpenAccounts = {},
                        onOpenCategories = {}
                    )
                }
            }
            item {
                Box(Modifier.padding(horizontal = 12.dp)) {
                    BillChartCard(state.chart)
                }
            }
            state.dayGroups.forEach { group ->
                stickyHeader(key = "day-${group.date}") {
                    DayHeader(date = group.date, income = group.income, expense = group.expense)
                }
                items(group.items, key = { it.id }) { item ->
                    Box(Modifier.padding(horizontal = 12.dp)) {
                        TransactionRow(
                            item = item,
                            category = item.categoryId?.let { state.categoryById[it] },
                            shared = false,
                            template = billTemplate,
                            creatorName = null,
                            onDelete = {},
                            onEdit = {}
                        )
                    }
                }
            }
        }
    }
}

@Preview(name = "账单页 · 信息卡片", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenCardPreview() {
    JizhangTheme { BillPreviewContent(billTemplate = 2) }
}

@Preview(name = "账单页 · 紧凑单行", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenCompactPreview() {
    JizhangTheme { BillPreviewContent(billTemplate = 3) }
}

@Preview(name = "账单页", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenPreview() {
    JizhangTheme { BillPreviewContent() }
}

/** 筛掉了全部账单时的空态 */
@Preview(name = "账单页 · 筛选无结果", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenFilteredEmptyPreview() {
    JizhangTheme {
        BillPreviewContent(
            state = previewUiState().copy(
                filteredTransactions = emptyList(),
                dayGroups = emptyList(),
                summary = BillSummary(),
                chart = BillChart(),
                filter = BillFilter(
                    range = BillDateRange.LAST_7_DAYS,
                    type = 2,
                    keyword = "房租"
                )
            )
        )
    }
}

/** 本月超支：宠物变担心表情，结余为负 */
@Preview(name = "账单页 · 超支", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenWorriedPreview() {
    JizhangTheme {
        BillPreviewContent(
            state = previewUiState().copy(
                petMood = PetMood.WORRIED,
                monthIncome = 320000,
                monthExpense = 1580000
            )
        )
    }
}

/** 一条账单都没有 */
@Preview(name = "账单页 · 空态", showBackground = true, widthDp = 380, heightDp = 620)
@Composable
private fun BillScreenEmptyPreview() {
    JizhangTheme {
        BillPreviewContent(state = previewUiState(transactions = emptyList()))
    }
}

/** 升到 Lv.10 的兴奋态 + 深色主题 */
@Preview(name = "账单页 · 深色", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenDarkPreview() {
    JizhangTheme(darkTheme = true) {
        BillPreviewContent(
            state = previewUiState().copy(
                pet = previewPetState().copy(coins = 480),
                petMood = PetMood.EXCITED
            )
        )
    }
}
