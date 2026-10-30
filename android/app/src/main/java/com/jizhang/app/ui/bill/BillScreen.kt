package com.jizhang.app.ui.bill

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.CategoryBadge
import com.jizhang.app.ui.common.DateRangeDialog
import com.jizhang.app.ui.common.TagChips
import com.jizhang.app.ui.common.billTitle
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.formatDateTime
import com.jizhang.app.data.repository.WealthPlanner
import com.jizhang.app.ui.common.formatSignedAmount
import com.jizhang.app.ui.common.money
import com.jizhang.app.ui.common.inlineTags
import com.jizhang.app.ui.common.parseTagList
import com.jizhang.app.ui.common.tagsUsedAsTitle
import com.jizhang.app.ui.common.transactionTypeLabel
import com.jizhang.app.ui.common.typeColor
import com.jizhang.app.ui.pet.PetAdvice
import com.jizhang.app.ui.pet.PetAvatar
import com.jizhang.app.ui.pet.PetInsight
import com.jizhang.app.ui.pet.PetMood
import com.jizhang.app.ui.pet.PetState
import com.jizhang.app.ui.pet.PetTip
import com.jizhang.app.ui.pet.PetTipKind
import com.jizhang.app.ui.pet.PetType
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.JizhangTheme
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import kotlin.math.roundToInt

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
    /**
     * 外部数据版本号：导入 / 清空账单后由 MainScreen +1。
     * <p>
     * 列表本身是 Room Flow，会自己更新；但顶部卡片的「本月收支」是查一次的聚合，
     * 而本页 ViewModel 挂在 Activity 上、切 Tab 回来时 `bind()` 会早退，
     * 所以这里显式重算一次，不依赖失效通知的时序。
     */
    dataVersion: Int = 0,
    onOpenAccounts: () -> Unit,
    onOpenCategories: () -> Unit,
    /** 点条目进「编辑账单」；记账页由 MainScreen 以编辑模式打开 */
    onEdit: (LocalTransaction) -> Unit,
    /** 机会成本卡里的「看财务自由进度」：全屏页挂在 MainScreen 根部 */
    onOpenWealth: () -> Unit = {},
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
    // 非空表示正在看这一笔的机会成本（长按触发）
    var costItem by remember { mutableStateOf<LocalTransaction?>(null) }

    // 账本变化（登录 / 退出）时重新绑定数据源
    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    // 导入 / 清空数据后重算顶部卡片（本月收入 / 支出 / 结余）。
    // 放在 bind 之后：首次组合时 bookId 已就位，refresh() 不会查到空账本。
    LaunchedEffect(dataVersion) {
        if (dataVersion > 0) {
            viewModel.refresh()
        }
    }

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
                            insight = state.petInsight,
                            tipIndex = state.tipIndex,
                            monthIncome = state.monthIncome,
                            monthExpense = state.monthExpense,
                            pendingCount = state.pendingCount,
                            syncing = state.syncing,
                            onNextTip = viewModel::nextTip,
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
                                    // 「这笔钱从哪个账户出的」：转账显示「转出 → 转入」
                                    accountName = accountText(item, state.accountLabels),
                                    onDelete = { deleting = item },
                                    onEdit = { onEdit(item) },
                                    // 转账没有"消费的时间成本"，长按留给真正花掉的钱
                                    onLongPress = {
                                        if (item.type != 3) {
                                            viewModel.refreshFiAssumptions()
                                            costItem = item
                                        }
                                    }
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
        DateRangeDialog(
            // 二次打开时把当前自定义区间预选上；customEnd 存的是右开边界，减 1 毫秒还原成用户眼里的止日
            initialStartMillis = state.filter.takeIf { it.range.isCustom }?.customStart,
            initialEndMillis = state.filter.takeIf { it.range.isCustom }?.customEnd?.minus(1),
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
                val label = billTitle(
                    target,
                    target.categoryId?.let { state.categoryById[it] }?.name
                )
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

    // 长按某笔 → 把它折成「未来值」：不是记账功能，是把时间维度摊开给你看
    costItem?.let { target ->
        OpportunityCostDialog(
            label = billTitle(target, target.categoryId?.let { state.categoryById[it] }?.name),
            amountFen = target.amount,
            type = target.type,
            realReturn = state.fiRealReturn,
            years = state.fiTargetYears,
            onOpenWealth = {
                costItem = null
                onOpenWealth()
            },
            onDismiss = { costItem = null }
        )
    }
}

/**
 * 机会成本卡。
 * <p>
 * 只做一件事：把「今天这笔钱」按用户自己的实际收益率假设折到目标年数，
 * 让一笔 38 元的午饭在时间维度上有一个具体数字。措辞上刻意不劝人省钱——
 * 记账工具一旦开始说教，用户就不记了。
 */
@Composable
private fun OpportunityCostDialog(
    label: String,
    amountFen: Long,
    type: Int,
    realReturn: Double,
    years: Int,
    onOpenWealth: () -> Unit,
    onDismiss: () -> Unit
) {
    val future = WealthPlanner.opportunityCost(amountFen, realReturn, years)
    val rateText = "${(realReturn * 1000).roundToInt() / 10.0}%"
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("这笔钱的机会成本") },
        text = {
            Column {
                Text(
                    text = "$label · ${formatSignedAmount(type, amountFen)}",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "按 $rateText 的实际收益率、$years 年折算，" +
                        // 账单页本来就逐笔显示金额，这里不需要再走隐私打码
                        "这笔钱如果留在组合里，届时约值 ${money(future, hideAmounts = false)}（今天的购买力）。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = "这不是让你别花——只是把它放回时间里看一眼。假设与「财务自由」页同源。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenWealth) { Text("看财务自由进度") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("知道了") }
        }
    )
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
                                        categoryIconOf(category.icon, category.name),
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
        AppChip(
            label = label,
            selected = active,
            onClick = { expanded = true },
            compact = true,
            trailingIcon = Icons.Filled.ArrowDropDown
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            menu { if (dismissOnClick) expanded = false }
        }
    }
}

/* ==================== 顶部卡片 ==================== */

/**
 * 顶部宠物卡片：宠物 + 本月收支 + 账户入口。
 *
 * 宠物在这里的职责是**替你把账读一遍**：名字下面一行是它算出来的近况，再往下是一条
 * 能对这笔钱起作用的具体建议（点一下换下一条）。
 * 原先那行「再记 N 笔升级」是纯粹的数值游戏——既不反映真实数据，对记账也没有帮助，已移除。
 */
@Composable
private fun PetCard(
    pet: PetState,
    insight: PetInsight,
    tipIndex: Int,
    monthIncome: Long,
    monthExpense: Long,
    pendingCount: Int,
    syncing: Boolean,
    onNextTip: () -> Unit,
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
                    mood = insight.mood,
                    modifier = Modifier.size(64.dp)
                )
                Spacer(Modifier.size(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = pet.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = moodText(insight.mood),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (insight.statusLine.isNotBlank()) {
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = insight.statusLine,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                        )
                    }
                }
                if (syncing) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    IconButton(onClick = onSync) {
                        Icon(Icons.Filled.Refresh, contentDescription = "同步")
                    }
                }
            }

            // 宠物的结论区：本地算出来的建议，一次一条，点一下换下一句
            val tip = insight.tips.getOrNull(tipIndex) ?: insight.tips.firstOrNull()
            if (tip != null) {
                Spacer(Modifier.height(10.dp))
                TipBubble(
                    tip = tip,
                    index = tipIndex,
                    count = insight.tips.size,
                    onNext = onNextTip
                )
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

/**
 * 宠物的建议气泡。
 *
 * 一次只放一条：三条挤在一起等于一条都没读。左侧色条按建议性质着色，
 * 多条时右下角给「序号 + 刷新」提示，暗示还能点。
 */
@Composable
private fun TipBubble(
    tip: PetTip,
    index: Int,
    count: Int,
    onNext: () -> Unit
) {
    val accent = when (tip.kind) {
        PetTipKind.ALERT -> MaterialTheme.colorScheme.error
        PetTipKind.SAVING -> MaterialTheme.colorScheme.primary
        PetTipKind.CHEER -> AppColors.income
        PetTipKind.HABIT -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = count > 1, onClick = onNext)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(accent)
            )
            Spacer(Modifier.size(10.dp))
            Text(
                text = tip.text,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f)
            )
            if (count > 1) {
                Spacer(Modifier.size(8.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "换一句",
                        modifier = Modifier.size(14.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${index + 1}/$count",
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
    PetMood.NORMAL -> "看了一遍你的账，没什么异常"
    PetMood.WORRIED -> "账有点不对劲，它皱起了眉头"
    PetMood.EXCITED -> "表现不错，它兴奋得直转圈～"
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
                Text(
                    text = "近 14 天",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                val maxValue = chart.days.maxOf { it.amount }.coerceAtLeast(1L)
                val today = LocalDate.now()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(86.dp),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    chart.days.forEach { day ->
                        // 今天单独给实色：14 根柱子光看日期数字很难定位「最右边这根是不是今天」
                        val isToday = day.date == today
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
                                    .background(if (isToday) accent else accent.copy(alpha = 0.35f))
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = day.date.dayOfMonth.toString(),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                                color = if (isToday) accent else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            if (chart.topCategories.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "分类占比",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
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
                                    imageVector = categoryIconOf(slice.icon, slice.name),
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
                                text = "${(slice.ratio * 100).roundToInt()}%",
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
    /** 这一笔的账户展示文本（转账为「转出 → 转入」）；查不到名字时为 null */
    accountName: String? = null,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    /** 长按：看这笔钱的机会成本；默认什么都不做（预览里不会误触） */
    onLongPress: () -> Unit = {}
) {
    when (template) {
        2 -> InfoCardRow(item, category, shared, creatorName, accountName, onDelete, onEdit, onLongPress)
        3 -> CompactRow(item, category, shared, creatorName, accountName, onDelete, onEdit, onLongPress)
        else -> ListRow(item, category, shared, creatorName, accountName, onDelete, onEdit, onLongPress)
    }
}

/**
 * 这一笔的账户展示文本。
 * <p>
 * 转账要写成「转出账户 → 转入账户」：只看一个名字会误以为钱只从那里出去了。
 * 账户名查不到（账户已被删除）时返回 null，调用方照旧不显示这一段。
 */
private fun accountText(item: LocalTransaction, labels: Map<Long, String>): String? {
    val from = labels[item.accountId]
    // type = 3 是转账（全工程统一口径，见 RecordViewModel / AccountDetailDialog）
    if (item.type != 3) {
        return from
    }
    val to = item.toAccountId?.let { labels[it] }
    return when {
        from != null && to != null -> "$from → $to"
        else -> from
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

/**
 * 条目副标题：分类 / 时间 / 账户 / 记账人 / 待同步。
 * <p>
 * 分类放最前面：有备注时标题是备注，不把分类补在这里，等于这条账看不到类别。
 * 主标题本身就是分类名时跳过，避免「餐饮 · … · 餐饮」。
 */
private fun subtitle(
    item: LocalTransaction,
    title: String,
    categoryName: String?,
    accountName: String?,
    shared: Boolean,
    creatorName: String?
): String =
    buildString {
        categoryName?.takeIf { it.isNotBlank() && it != title }?.let { append("$it · ") }
        append(formatDateTime(item.tradeTime))
        accountName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
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
    accountName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
            val title = billTitle(item, category?.name)
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryBadge(type = item.type, category = category, size = 34.dp)
                Spacer(Modifier.width(10.dp))
                TypeTag(item.type)
                Spacer(Modifier.size(8.dp))
                Text(
                    text = title,
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
                    text = subtitle(
                        item = item,
                        title = title,
                        categoryName = category?.name,
                        accountName = accountName,
                        shared = shared,
                        creatorName = creatorName
                    ),
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
            val cardTags = parseTagList(item.tags)
            // 标签已经顶上标题时不再重复一排：同一串字在一张卡里出现两次很吵
            if (cardTags.isNotEmpty() && !tagsUsedAsTitle(item)) {
                Spacer(Modifier.height(6.dp))
                TagChips(cardTags)
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
    accountName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress)
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        CategoryBadge(type = item.type, category = category, size = 26.dp)
        Spacer(Modifier.size(8.dp))
        Text(
            text = formatDateTime(item.tradeTime),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.size(10.dp))
        Text(
            text = buildString {
                val title = billTitle(item, category?.name)
                append(title)
                // 紧凑模板只有一行：分类（和标签）都压进这行文本里，标签已经当标题时不再重复
                category?.name?.takeIf { it.isNotBlank() && it != title }?.let {
                    append(" · $it")
                }
                if (!tagsUsedAsTitle(item)) {
                    inlineTags(item.tags)?.let { append(" · $it") }
                }
                accountName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
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
    accountName: String?,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onLongPress: () -> Unit
) {
    Card(
        // 整卡可点 = 改这笔账，长按 = 看机会成本（删除按钮会自己消费点击，不会误触发）
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onEdit, onLongClick = onLongPress),
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
            CategoryBadge(type = item.type, category = category, size = 38.dp)
            Spacer(Modifier.width(12.dp))
            val title = billTitle(item, category?.name)
            val tags = parseTagList(item.tags)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = subtitle(
                        item = item,
                        title = title,
                        categoryName = category?.name,
                        accountName = accountName,
                        shared = shared,
                        creatorName = creatorName
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (tags.isNotEmpty() && !tagsUsedAsTitle(item)) {
                    Spacer(Modifier.height(4.dp))
                    TagChips(tags)
                }
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
        // 无备注时用标签当标题（标签也没有才回落到分类名 / 类型文案）
        LocalTransaction(
            id = 3L, bookId = 1L, type = 1, amount = 3500, accountId = 1L, categoryId = 1L,
            tradeTime = now, remark = null, tags = "早餐,楼下", pendingSync = true
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
        petInsight = PetAdvice.analyze(
            PetAdvice.factsOf(
                transactions = transactions,
                categoryNames = previewCategories.associate { it.id to it.name },
                today = LocalDate.now(),
                streakDays = 6
            )
        )
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
                        insight = state.petInsight,
                        tipIndex = state.tipIndex,
                        monthIncome = state.monthIncome,
                        monthExpense = state.monthExpense,
                        pendingCount = 2,
                        syncing = false,
                        onNextTip = {},
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

/** 本月超支：宠物变担心表情，建议走预警配色 */
@Preview(name = "账单页 · 超支", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenWorriedPreview() {
    JizhangTheme {
        BillPreviewContent(
            state = previewUiState().copy(
                monthIncome = 320000,
                monthExpense = 1580000,
                petInsight = PetInsight(
                    mood = PetMood.WORRIED,
                    tips = listOf(
                        PetTip(
                            PetTipKind.ALERT,
                            "本月支出 ¥15,800.00 已经超过收入 ¥3,200.00，倒挂 ¥12,600.00"
                        ),
                        PetTip(
                            PetTipKind.SAVING,
                            "「餐饮」占了本月支出的 52%（¥8,200.00），想省钱先从它下手"
                        )
                    ),
                    statusLine = "本月日均支出 ¥526.67 · 还剩 11 天 · 连续记账 6 天"
                )
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

/** 连续记账很久的兴奋态 + 深色主题 */
@Preview(name = "账单页 · 深色", showBackground = true, widthDp = 380, heightDp = 860)
@Composable
private fun BillScreenDarkPreview() {
    JizhangTheme(darkTheme = true) {
        BillPreviewContent(
            state = previewUiState().copy(
                petInsight = PetInsight(
                    mood = PetMood.EXCITED,
                    tips = listOf(
                        PetTip(PetTipKind.CHEER, "本月结余 ¥6,155.00，收入的 33% 存下来了"),
                        PetTip(PetTipKind.CHEER, "已经连续记账 12 天了，稳住")
                    ),
                    statusLine = "本月日均支出 ¥205.75 · 还剩 11 天 · 连续记账 12 天"
                )
            )
        )
    }
}
