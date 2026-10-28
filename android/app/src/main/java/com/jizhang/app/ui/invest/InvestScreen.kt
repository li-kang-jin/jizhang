package com.jizhang.app.ui.invest

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalInvestPosition
import com.jizhang.app.data.local.entity.PositionWithValue
import com.jizhang.app.data.repository.InvestmentOverview
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.formatAmountInput
import com.jizhang.app.ui.common.money
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * 投资页（全屏覆盖层）。
 * <p>
 * 只做三件事：**投了多少、现在值多少、还有多少没归到品种上**。
 * 买入/卖出不在这里记 —— 那仍然是普通的转账（储蓄卡 → 投资账户），
 * 所以这张页面永远不会让支出统计、预算或宠物建议失真。
 * <p>
 * 页面遵循全屏覆盖层约定：调用点在 [com.jizhang.app.ui.main.MainScreen] 根部，
 * 顶栏用默认 windowInsets，内容区 `safeDialogContent()`。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InvestScreen(
    bookId: Long,
    hideAmounts: Boolean = false,
    /** 没有投资账户时的出口：跳到资产管理新建一个 */
    onOpenAccounts: () -> Unit = {},
    onDismiss: () -> Unit,
    viewModel: InvestViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    LaunchedEffect(bookId) { viewModel.bind(bookId) }
    val overview = state.overview

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("投资", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
                    .padding(bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                when {
                    !overview.hasInvestAccount -> NoAccountHint(onOpenAccounts)
                    overview.positions.isEmpty() -> {
                        SummaryCard(overview, hideAmounts)
                        EmptyHint(
                            title = "还没有品种",
                            body = "点下面的「添加品种」记下第一只基金或股票。" +
                                "买入的钱不用重新记 —— 从储蓄卡转账到投资账户就已经算进去了。"
                        )
                    }
                    else -> {
                        SummaryCard(overview, hideAmounts)
                        if (overview.unallocatedFen != 0L) {
                            UnallocatedCard(
                                unallocatedFen = overview.unallocatedFen,
                                hideAmounts = hideAmounts,
                                onClaim = {
                                    viewModel.openCreate(
                                        costPrefillFen = overview.unallocatedFen.coerceAtLeast(0L)
                                    )
                                }
                            )
                        }
                        AllocationCard(overview.positions, hideAmounts)
                        overview.positions.forEach { position ->
                            PositionCard(
                                position = position,
                                hideAmounts = hideAmounts,
                                onEdit = { viewModel.openEdit(position) },
                                onUpdateValue = { viewModel.openValuing(position) }
                            )
                        }
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "记账时从储蓄卡转账到投资账户、顺手选中品种，成本会自动累计；" +
                                "市值需要手动更新（每周或每月一次就够）。这里不接行情：" +
                                "记账是按月看的，天天盯盘只会让你更容易追涨杀跌。",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            if (overview.hasInvestAccount) {
                Button(
                    onClick = { viewModel.openCreate() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                        .padding(top = 4.dp, bottom = 12.dp)
                ) {
                    Text("添加品种")
                }
            }
        }
    }

    if (state.editorOpen) {
        PositionEditorDialog(
            editing = state.editing,
            costPrefillFen = state.costPrefillFen,
            accounts = overview.accounts,
            hideAmounts = hideAmounts,
            onDismiss = viewModel::closeEditor,
            onSave = viewModel::save,
            onDelete = state.editing?.let { position -> { viewModel.delete(position) } }
        )
    }

    state.valuing?.let { position ->
        ValuationDialog(
            position = position,
            hideAmounts = hideAmounts,
            onDismiss = viewModel::closeValuing,
            onConfirm = { text -> viewModel.updateValue(position, text) }
        )
    }
}

/* ==================== 卡片 ==================== */

/** 总览：总市值 + 浮动盈亏 + 总成本 */
@Composable
private fun SummaryCard(overview: InvestmentOverview, hideAmounts: Boolean) {
    val profit = overview.profitFen
    val profitColor = when {
        profit > 0L -> AppColors.income
        profit < 0L -> AppColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    CardBox {
        Text(
            text = "投资总市值",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = money(overview.totalValueFen, hideAmounts),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = overview.profitRate?.let { rate ->
                    signedMoney(profit, hideAmounts) + "（" + percentText(rate) + "）"
                } ?: "还没有成本记录",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = profitColor
            )
        }

        Spacer(Modifier.height(10.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
        Spacer(Modifier.height(8.dp))
        InfoRow("总成本", money(overview.totalCostFen, hideAmounts))
        InfoRow("投资账户余额", money(overview.accountBalanceFen, hideAmounts))
        InfoRow(
            label = "上次估值",
            value = overview.lastValuedAt?.let { daysAgoText(it) } ?: "还没估过"
        )
    }
}

/**
 * 未分配资金。
 * <p>
 * 这是整个模块最重要的一个数：它允许你「转了钱但暂时懒得分类」，
 * 不至于因为必须先建品种而放弃记账。金额来自账户余额与持仓成本之差，无需维护。
 */
@Composable
private fun UnallocatedCard(unallocatedFen: Long, hideAmounts: Boolean, onClaim: () -> Unit) {
    val negative = unallocatedFen < 0L
    CardBox {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (negative) "成本记多了" else "未分配资金",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = money(abs(unallocatedFen), hideAmounts),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
            if (!negative) {
                TextButton(onClick = onClaim) { Text("归到新品种") }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = if (negative) {
                "品种成本比投资账户里的钱还多。检查一下成本是不是填重复了。"
            } else {
                "这笔钱在投资账户里，但还没归到具体品种上。"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 品种占比环形图。
 * <p>
 * 只在有 2 个以上品种时出现：一个品种画个整圆不传递任何信息，纯占地方。
 * 占比按**市值**算（未估值的品种按成本），所以它回答的是「我的钱现在压在哪儿」，
 * 而不是「我过去买了多少」—— 后者看成本列。
 */
@Composable
private fun AllocationCard(positions: List<PositionWithValue>, hideAmounts: Boolean) {
    if (positions.size < 2) return
    val total = positions.sumOf { it.marketValueFen }
    if (total <= 0L) return
    val sorted = positions.sortedByDescending { it.marketValueFen }

    CardBox {
        Text("配置", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(2.dp))
        Text(
            text = "按最新市值算的占比",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Canvas(modifier = Modifier.size(104.dp)) {
                val thickness = 16.dp.toPx()
                val inset = thickness / 2f
                var start = -90f
                sorted.forEachIndexed { index, position ->
                    val sweep = position.marketValueFen.toFloat() / total * 360f
                    if (sweep <= 0f) return@forEachIndexed
                    drawArc(
                        color = ringColor(index),
                        startAngle = start,
                        // 留 1.5° 缝隙，相邻两块颜色才分得开
                        sweepAngle = (sweep - 1.5f).coerceAtLeast(0.5f),
                        useCenter = false,
                        topLeft = Offset(inset, inset),
                        size = Size(size.width - thickness, size.height - thickness),
                        style = Stroke(width = thickness)
                    )
                    start += sweep
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                sorted.forEachIndexed { index, position ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(ringColor(index))
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = position.name,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = shareText(position.marketValueFen.toDouble() / total),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

/** 单个品种：市值 / 成本 / 盈亏 / 估值时间 */
@Composable
private fun PositionCard(
    position: PositionWithValue,
    hideAmounts: Boolean,
    onEdit: () -> Unit,
    onUpdateValue: () -> Unit
) {
    val profit = position.profitFen
    val profitColor = when {
        profit > 0L -> AppColors.income
        profit < 0L -> AppColors.expense
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    CardBox(onClick = onEdit) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = kindIcon(position.kind),
                contentDescription = null,
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = position.name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = buildString {
                        append(LocalInvestPosition.kindName(position.kind))
                        position.code?.let { append(" · ").append(it) }
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = money(position.marketValueFen, hideAmounts),
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = position.profitRate?.let { rate ->
                        signedMoney(profit, hideAmounts) + " " + percentText(rate)
                    } ?: signedMoney(profit, hideAmounts),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    color = profitColor
                )
            }
        }

        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "成本 " + money(position.costFen, hideAmounts),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = position.valuedAt?.let { "估值 " + daysAgoText(it) } ?: "未估值",
                style = MaterialTheme.typography.labelSmall,
                color = if (position.valuedAt == null) {
                    AppColors.expense
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
            )
            Spacer(Modifier.width(4.dp))
            TextButton(onClick = onUpdateValue) { Text("更新市值") }
        }
    }
}

/* ==================== 空状态 ==================== */

@Composable
private fun NoAccountHint(onOpenAccounts: () -> Unit) {
    CardBox {
        Text("还没有投资账户", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "先建一个类型为「投资」的账户，再把买入的钱从储蓄卡转账过来 —— " +
                "本金会自动算进投资，不需要另记一笔支出，否则支出统计会凭空翻几倍。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(10.dp))
        Button(
            onClick = onOpenAccounts,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("去新建投资账户")
        }
    }
}

@Composable
private fun EmptyHint(title: String, body: String) {
    CardBox {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            text = body,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/* ==================== 弹窗 ==================== */

/**
 * 新建 / 编辑品种。
 * <p>
 * 成本与市值都是**选填**：先建一个空壳、以后慢慢补，比逼用户当场凑齐数字更能留住人。
 */
@Composable
private fun PositionEditorDialog(
    editing: PositionWithValue?,
    costPrefillFen: Long?,
    accounts: List<AccountWithBalance>,
    hideAmounts: Boolean,
    onDismiss: () -> Unit,
    onSave: (accountId: Long, name: String, code: String, kind: Int, costText: String, valueText: String, note: String) -> Unit,
    onDelete: (() -> Unit)?
) {
    var name by remember { mutableStateOf(editing?.name.orEmpty()) }
    var code by remember { mutableStateOf(editing?.code.orEmpty()) }
    var kind by remember { mutableStateOf(editing?.kind ?: LocalInvestPosition.KIND_FUND) }
    var accountId by remember {
        mutableStateOf(editing?.accountId ?: accounts.firstOrNull()?.id ?: 0L)
    }
    var costText by remember {
        // 回填输入框必须用无千分位口径，否则 toDoubleOrNull() 解析不出来。
        // 编辑时回填的是「期初成本」：转账自动累计的那部分不该被手改成静态值
        mutableStateOf(
            when {
                editing != null -> formatAmountInput(editing.manualCostFen)
                costPrefillFen != null && costPrefillFen > 0L -> formatAmountInput(costPrefillFen)
                else -> ""
            }
        )
    }
    var valueText by remember { mutableStateOf("") }
    var note by remember { mutableStateOf(editing?.note.orEmpty()) }
    var confirmingDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (editing == null) "添加品种" else "编辑品种",
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .heightIn(max = 420.dp)
                    .verticalScroll(rememberScrollState())
                    .imePadding()
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("名称") },
                    placeholder = { Text("沪深300ETF") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("代码（选填）") },
                    placeholder = { Text("510300") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(10.dp))
                Text(
                    text = "类型",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        LocalInvestPosition.KIND_STOCK,
                        LocalInvestPosition.KIND_FUND,
                        LocalInvestPosition.KIND_BOND,
                        LocalInvestPosition.KIND_WEALTH
                    ).forEach { option ->
                        FilterChip(
                            selected = kind == option,
                            onClick = { kind = option },
                            label = { Text(LocalInvestPosition.kindName(option)) }
                        )
                    }
                }

                if (accounts.size > 1) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "所属投资账户",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        accounts.forEach { account ->
                            FilterChip(
                                selected = accountId == account.id,
                                onClick = { accountId = account.id },
                                label = { Text(account.name) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = costText,
                    onValueChange = { costText = it },
                    label = { Text(if (editing == null) "投入成本（元）" else "期初成本（元）") },
                    placeholder = { Text("0") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                // 转账挂过品种时，成本里有一部分是自动累计的：说清楚，免得用户以为填错了
                if (editing != null && editing.flowCostFen != 0L) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "另有 ${money(editing.flowCostFen, hideAmounts)} 由转账自动累计，" +
                            "合计 ${money(editing.costFen, hideAmounts)}。这里只填没有对应转账的部分。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (editing == null) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = valueText,
                        onValueChange = { valueText = it },
                        label = { Text("当前市值（元，选填）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("备注（选填）") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                if (onDelete != null) {
                    Spacer(Modifier.height(4.dp))
                    TextButton(
                        onClick = {
                            if (confirmingDelete) onDelete() else confirmingDelete = true
                        }
                    ) {
                        Text(
                            text = if (confirmingDelete) "确认删除（连估值记录一起删）" else "删除品种",
                            color = AppColors.expense
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onSave(accountId, name, code, kind, costText, valueText, note)
                },
                enabled = name.isNotBlank() && accountId > 0L
            ) {
                Text("保存")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 只填一个数的市值更新：输入时实时算盈亏，比填完再回去看更直观 */
@Composable
private fun ValuationDialog(
    position: PositionWithValue,
    hideAmounts: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    // 预填当前市值：多数时候只是微调，全清空重打反而慢
    var text by remember { mutableStateOf(formatAmountInput(position.marketValueFen)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("更新市值", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                Text(
                    text = position.name + " · 成本 " + money(position.costFen, hideAmounts),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("当前市值（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                val fen = text.toDoubleOrNull()?.let { (it * 100).roundToInt().toLong() }
                if (fen != null) {
                    val profit = fen - position.costFen
                    val rate = if (position.costFen > 0L) {
                        profit.toDouble() / position.costFen
                    } else {
                        null
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "浮动盈亏 " + signedMoney(profit, hideAmounts) +
                            (rate?.let { "（" + percentText(it) + "）" } ?: ""),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = when {
                            profit > 0L -> AppColors.income
                            profit < 0L -> AppColors.expense
                            else -> MaterialTheme.colorScheme.onSurfaceVariant
                        }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/* ==================== 小工具 ==================== */

@Composable
private fun CardBox(onClick: (() -> Unit)? = null, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

/** 带符号金额：+¥1,200.00 / -¥300.00；隐私模式下不泄露正负之外的任何信息 */
private fun signedMoney(fen: Long, hideAmounts: Boolean): String =
    if (hideAmounts) "****" else (if (fen >= 0) "+" else "-") + "¥" + formatAmount(abs(fen))

/** 收益率：整数不带小数，其余保留一位（+24.6%） */
private fun percentText(rate: Double): String {
    val percent = rate * 100.0
    val rounded = percent.roundToInt()
    val body = if (abs(percent - rounded) < 0.05) {
        "$rounded"
    } else {
        "${(percent * 10).roundToInt() / 10.0}"
    }
    return if (percent >= 0) "+$body%" else "$body%"
}

/** 「上次估值」的距离文案：超过一个月按整月说，避免出现「第 187 天」这种读不懂的数字 */
private fun daysAgoText(at: Long): String {
    val days = ((System.currentTimeMillis() - at) / 86_400_000L).toInt()
    return when {
        days <= 0 -> "今天"
        days == 1 -> "昨天"
        days < 30 -> "$days 天前"
        else -> "${days / 30} 个月前"
    }
}

private fun kindIcon(kind: Int): ImageVector = when (kind) {
    LocalInvestPosition.KIND_STOCK -> Icons.Filled.TrendingUp
    LocalInvestPosition.KIND_FUND -> Icons.Filled.PieChart
    LocalInvestPosition.KIND_BOND -> Icons.Filled.AccountBalance
    LocalInvestPosition.KIND_WEALTH -> Icons.Filled.Savings
    else -> Icons.Filled.MoreHoriz
}

/**
 * 环形图的固定色板。
 * <p>
 * 不用主题色：accent 在每块扇形上都用一遍，图表会退化成一片同色。
 * 这 6 个色相在深浅两套主题下都能区分，第 7 个品种开始循环复用。
 */
private val RING_COLORS = listOf(
    Color(0xFF4E9F5B),
    Color(0xFF3D7BBF),
    Color(0xFFC98A2E),
    Color(0xFF8A5BB8),
    Color(0xFF2E9AA6),
    Color(0xFFC4557A)
)

private fun ringColor(index: Int): Color = RING_COLORS[index % RING_COLORS.size]

/** 占比文案：不足 1% 时说「<1%」，别显示成 0% 让人以为这笔钱不存在 */
private fun shareText(ratio: Double): String {
    val percent = ratio * 100.0
    return when {
        percent <= 0.0 -> "0%"
        percent < 1.0 -> "<1%"
        percent >= 99.95 -> "100%"
        else -> "${percent.roundToInt()}%"
    }
}
