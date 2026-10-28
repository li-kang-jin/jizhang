package com.jizhang.app.ui.record

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.repository.CategoryGuess
import com.jizhang.app.data.repository.GuessSource
import com.jizhang.app.ui.category.CategoryEditorDialog
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.categoryColorOf
import com.jizhang.app.ui.common.categoryContentOn
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.categoryTintOn
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val PLACEHOLDER = "点击填写"

/** 标签快选行最多铺几个：再多就靠横滚和弹窗，避免铺成一片「标签墙」 */
private const val QUICK_TAG_LIMIT = 12

/** 账户区默认铺几个常用账户：三个足够覆盖日常，再多就挤掉日期和备注 */
private const val QUICK_ACCOUNT_LIMIT = 3

/**
 * 「记一笔」全屏页。
 *
 * 只有一套布局，从上到下的顺序就是记账的动作顺序：
 * 类型 → 分类（主区，可滚）→ 标签（常驻快选，跟当前分类走）→ 账户 / 日期 / 备注 → 金额 → 数字键盘。
 *
 * 早期做过五套可切换模板（经典键盘 / 极简速记 / 备注工作台 / 网格速记 / 再来一笔），
 * 实际用起来各套都只覆盖一部分场景，切换成本反而拖慢记账，现已合并成这一套。
 *
 * 另：ViewModel 是 Activity 作用域常驻实例，离开页面时必须复位表单（见页面末尾的
 * [DisposableEffect] 与 `RecordViewModel.resetForm`），否则「记完账旧数据不清零」。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    bookId: Long,
    /** 数字键盘按键震动反馈（设置页可关） */
    hapticEnabled: Boolean = true,
    /** 非空表示「编辑这笔账单」（从账单页点进来），为空则是新增 */
    editTransaction: LocalTransaction? = null,
    /** 非空表示「补记某天」（从日历页点某一天进来），表单里的记账日期预填成那天 */
    initialDate: LocalDate? = null,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val view = LocalView.current
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRemarkDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var showCategoryEditor by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, editTransaction, initialDate) {
        val target = editTransaction
        if (target != null) viewModel.startEdit(bookId, target) else viewModel.load(bookId, initialDate)
    }

    // 记账页的 ViewModel 是 Activity 作用域常驻实例（工程没有 Navigation），
    // 覆盖层只是反复 show/hide。离开页面时务必复位表单，否则下次打开会看到上次的金额 / 备注。
    DisposableEffect(Unit) {
        onDispose { viewModel.resetForm() }
    }

    fun save() {
        viewModel.save { error ->
            if (error == null) {
                errorMessage = null
                onSaved()
            } else {
                errorMessage = error
            }
        }
    }

    fun handleKey(label: String) {
        // View.performHapticFeedback 走系统触感通道，不需要 VIBRATE 权限
        if (hapticEnabled) {
            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        }
        when (label) {
            "完成" -> save()
            "=" -> viewModel.pressEquals()
            "⌫" -> viewModel.pressBackspace()
            "C" -> viewModel.pressClear()
            "×", "÷", "−", "+" -> viewModel.pressOp(label.first())
            else -> viewModel.pressDigit(label.first())
        }
    }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        RecordLayout(
            state = state,
            viewModel = viewModel,
            errorMessage = errorMessage,
            onBack = onDismiss,
            onSave = ::save,
            onOpenRemark = { showRemarkDialog = true },
            onOpenTags = { showTagDialog = true },
            onOpenDate = { showDatePicker = true },
            onAddCategory = { showCategoryEditor = true },
            onKey = ::handleKey
        )
    }

    if (showCategoryEditor) {
        // 类型锁定为当前记账类型：新建的分类必须留在当前这一侧，否则看起来像没建成功
        CategoryEditorDialog(
            category = null,
            defaultType = state.type,
            lockType = true,
            onDismiss = { showCategoryEditor = false },
            onSave = { name, _, icon, done ->
                viewModel.addCategory(name, icon) { error ->
                    if (error == null) {
                        errorMessage = null
                        showCategoryEditor = false
                    }
                    done(error)
                }
            }
        )
    }

    if (showRemarkDialog) {
        InputDialog(
            title = "备注",
            initial = state.remark,
            onConfirm = { viewModel.setRemark(it); showRemarkDialog = false },
            onDismiss = { showRemarkDialog = false }
        )
    }

    if (showTagDialog) {
        TagInputDialog(
            initial = state.tags,
            suggestions = state.tagSuggestions,
            onConfirm = { viewModel.setTags(it); showTagDialog = false },
            onDismiss = { showTagDialog = false }
        )
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.tradeDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.setTradeDate(date)
                    }
                    showDatePicker = false
                }) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            }
        ) {
            DatePicker(state = pickerState)
        }
    }
}

/* ==================================================================
 * 记账页唯一布局。
 *
 * 分层原则（屏幕从上到下就是手指的动作顺序）：
 *   一级 · 每次必用：分类（主区，一屏装下）、金额（键盘正上方，拇指落点）、保存（右上）
 *   一级 · 状态可见：类型（顶栏分段）、账户 / 日期（键盘上方元信息条）
 *   二级 · 按需弹出：账户选择、日期、备注、标签、新建分类
 * 所有「有默认值、偶尔才改」的东西都不再各占一行，省下来的高度全部给分类网格，
 * 27 个分类也不用滚动。
 * ================================================================== */

@Composable
private fun RecordLayout(
    state: RecordViewModel.UiState,
    viewModel: RecordViewModel,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenRemark: () -> Unit,
    onOpenTags: () -> Unit,
    onOpenDate: () -> Unit,
    onAddCategory: () -> Unit,
    onKey: (String) -> Unit
) {
    val accent = accentOf(state.type)
    var showAccountPicker by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // ① 顶栏：返回 / 类型（当前状态一眼可见，也可直接切） / 保存
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(start = 2.dp, end = 8.dp, top = 2.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack, modifier = Modifier.size(38.dp)) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "返回",
                    modifier = Modifier.size(20.dp)
                )
            }
            CompactTypeSwitch(
                type = state.type,
                onChange = viewModel::switchType,
                modifier = Modifier
                    .width(168.dp)
                    .padding(start = 2.dp)
            )
            Spacer(Modifier.weight(1f))
            SaveAction(
                enabled = state.canSave && !state.saving,
                saving = state.saving,
                editing = state.editing != null,
                onClick = onSave
            )
        }

        // 校验失败时只占一行提示，不挤占别的区域
        ErrorHint(errorMessage, horizontalPadding = 16.dp)

        // ② 主区：分类网格（或转账的双账户选择），撑满剩余高度
        if (state.type == 3) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                Spacer(Modifier.height(6.dp))
                TransferSection(state, viewModel)
                Spacer(Modifier.height(10.dp))
            }
        } else {
            CategoryGrid(
                categories = state.categories,
                selectedId = state.selectedCategoryId,
                accent = accent,
                onSelect = viewModel::selectCategory,
                onAdd = onAddCategory,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 10.dp)
            )
        }

        // ②' 备注猜出来的分类：一行高度，只在猜到的不是当前选中时出现（放在网格下方，避免出现时把格子顶走）
        CategorySuggestionBar(
            suggestion = state.categorySuggestion,
            categoryName = state.categorySuggestion?.let { guess ->
                state.categories.firstOrNull { it.id == guess.categoryId }?.name
            },
            accent = accent,
            onAccept = viewModel::acceptCategorySuggestion
        )

        // ③ 标签：记账基本每笔都要打标签，常用标签直接铺在表单里，点一下就选上，不再进二级弹窗
        TagQuickBar(
            tags = state.tags,
            suggestions = state.tagSuggestions,
            accent = accent,
            onToggle = viewModel::toggleTag,
            onMore = onOpenTags
        )

        // ④ 元信息条：三个常用账户 / 日期 / 备注，一眼看到当前值，点一下就切
        MetaBar(
            state = state,
            accent = accent,
            onPickAccount = { showAccountPicker = true },
            onSelectAccount = viewModel::selectAccount,
            onOpenDate = onOpenDate,
            onOpenRemark = onOpenRemark
        )

        // ⑤ 金额：紧贴键盘，眼睛不用离开手指
        AmountBar(state)

        // ⑥ 键盘
        CalcKeyboard(onKey = onKey)
    }

    if (showAccountPicker) {
        AccountPickerDialog(
            accounts = state.accounts,
            selectedId = state.selectedAccountId,
            lastAccountId = state.lastAccountId,
            accent = accent,
            onSelect = {
                viewModel.selectAccount(it)
                showAccountPicker = false
            },
            onDismiss = { showAccountPicker = false }
        )
    }
}

/** 顶栏右侧的保存动作：不可保存时是明确的灰态，不是「点了没反应」 */
@Composable
private fun SaveAction(enabled: Boolean, saving: Boolean, editing: Boolean, onClick: () -> Unit) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
        modifier = Modifier.height(34.dp)
    ) {
        Text(
            text = when {
                saving -> "保存中…"
                editing -> "保存修改"
                else -> "保存"
            },
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** 金额条：本页最大的数字，紧贴键盘 */
@Composable
private fun AmountBar(state: RecordViewModel.UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 2.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 4.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = amountText(state),
            style = MaterialTheme.typography.headlineLarge,
            fontWeight = FontWeight.Bold,
            color = amountColor(state),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false)
        )
        if (state.expressionText.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = state.expressionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f, fill = false)
                    .padding(bottom = 5.dp)
            )
        }
    }
}

/**
 * 元信息条：常用账户 / 日期 / 备注。
 *
 * 账户只铺**三个常用的**（按使用次数，见 [quickAccounts]），点一下直接切，不用先开弹窗；
 * 账户多于三个时末尾补一个「更多」进完整选择器。日期与备注同样压缩成芯片，
 * 整条可横滚，**当前值始终可见**。标签不在这里：它每笔都要点，已经常驻成一行（见 [TagQuickBar]）。
 */
@Composable
private fun MetaBar(
    state: RecordViewModel.UiState,
    accent: Color,
    onPickAccount: () -> Unit,
    onSelectAccount: (Long) -> Unit,
    onOpenDate: () -> Unit,
    onOpenRemark: () -> Unit
) {
    val quickAccounts = quickAccounts(state)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (state.type != 3) {
            quickAccounts.forEach { account ->
                AppChip(
                    label = account.name,
                    selected = state.selectedAccountId == account.id,
                    onClick = { onSelectAccount(account.id) },
                    tint = accent,
                    compact = true
                )
            }
            if (state.accounts.size > quickAccounts.size) {
                AppChip(
                    label = "更多",
                    selected = false,
                    onClick = onPickAccount,
                    trailingIcon = Icons.Filled.ExpandMore,
                    compact = true
                )
            }
        }
        AppChip(
            label = dateShortLabel(state.tradeDate),
            selected = false,
            onClick = onOpenDate,
            leadingIcon = Icons.Filled.DateRange,
            compact = true
        )
        AppChip(
            label = state.remark.ifBlank { "备注" }.let { if (it.length > 8) it.take(8) + "…" else it },
            selected = state.remark.isNotBlank(),
            onClick = onOpenRemark,
            leadingIcon = Icons.Filled.Edit,
            compact = true
        )
    }
}

/**
 * 三个常用账户：只挑**真正记过账**的账户，按使用次数降序（次数相同按账户管理里的手工顺序），
 * 当前选中的账户如果不在前三就顶掉最后一个。完整规则见 [pickQuickAccounts]。
 */
private fun quickAccounts(state: RecordViewModel.UiState): List<LocalAccount> =
    pickQuickAccounts(
        accounts = state.accounts,
        usage = state.accountUsage,
        selectedAccountId = state.selectedAccountId,
        limit = QUICK_ACCOUNT_LIMIT
    )

/**
 * 表单里的标签快选行：常用标签直接铺开，点一下加上、再点一下去掉。
 *
 * 记账几乎每笔都要打标签，塞进二级弹窗等于每笔多两次点击；这里常驻一行（横向滚动，
 * 高度只有一行芯片），右侧「＋」留给候选里没有的标签（弹窗里可以手打、可以一次加多个）。
 *
 * 候选由 ViewModel 按**当前分类**算好（[RecordViewModel.refreshTagSuggestions]），
 * 换分类时整排换掉，记「交通」不会再刷出「晚餐」。
 */
@Composable
private fun TagQuickBar(
    tags: String,
    suggestions: List<String>,
    accent: Color,
    onToggle: (String) -> Unit,
    onMore: () -> Unit
) {
    val picked = parseTags(tags)
    // 已选但不在候选里的（别的分类打过 / 手打的）也要露出来，否则用户看不到自己到底选了啥
    val chips = (suggestions + picked.filter { it !in suggestions }).take(QUICK_TAG_LIMIT)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 15.dp, end = 10.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Filled.LocalOffer,
            contentDescription = null,
            tint = if (picked.isEmpty()) {
                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            } else {
                accent
            },
            modifier = Modifier.size(14.dp)
        )
        Spacer(Modifier.width(8.dp))
        Row(
            modifier = Modifier
                .weight(1f)
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (chips.isEmpty()) {
                Text(
                    text = "点右侧 ＋ 加标签",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            }
            chips.forEach { tag ->
                AppChip(
                    label = tag,
                    selected = tag in picked,
                    onClick = { onToggle(tag) },
                    tint = accent,
                    compact = true
                )
            }
        }
        Spacer(Modifier.width(6.dp))
        // 候选里没有的标签、或一次要加好几个，走原来的弹窗
        Box(
            modifier = Modifier
                .size(26.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.75f))
                .clickable(onClick = onMore),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "更多标签",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(15.dp)
            )
        }
    }
}

/** 账户选择：二级弹层，不再常驻占一行 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccountPickerDialog(
    accounts: List<LocalAccount>,
    selectedId: Long?,
    lastAccountId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    // 当前选中排第一、上次用过的排第二，其余保持账户管理里的手工顺序：
    // 常点的账户永远在伸手就够到的地方，不用先滚动找
    val ordered = accounts.sortedWith(
        compareByDescending<LocalAccount> { it.id == selectedId }
            .thenByDescending { it.id == lastAccountId }
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择账户", fontWeight = FontWeight.SemiBold) },
        text = {
            if (accounts.isEmpty()) {
                Text(
                    text = "还没有账户，可在账单页打开「账户管理」新建",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ordered.forEach { account ->
                            AppChip(
                                label = account.name,
                                selected = selectedId == account.id,
                                onClick = { onSelect(account.id) },
                                tint = accent
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("关闭") }
        }
    )
}

/** 紧凑类型分段：顶栏里只占 168dp，当前类型一眼可见 */
@Composable
private fun CompactTypeSwitch(type: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        TypeTab("支出", type == 1, AppColors.expense, Modifier.weight(1f)) { onChange(1) }
        TypeTab("收入", type == 2, AppColors.income, Modifier.weight(1f)) { onChange(2) }
        TypeTab("转账", type == 3, AppColors.accent, Modifier.weight(1f)) { onChange(3) }
    }
}

@Composable
private fun TypeTab(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

/* ==================================================================
 * 共用组件
 * ================================================================== */

@Composable
private fun ErrorHint(message: String?, horizontalPadding: androidx.compose.ui.unit.Dp = 16.dp) {
    if (message == null) {
        return
    }
    Spacer(Modifier.height(4.dp))
    Text(
        text = message,
        color = MaterialTheme.colorScheme.error,
        style = MaterialTheme.typography.bodySmall,
        modifier = Modifier.padding(horizontal = horizontalPadding)
    )
}

@Composable
private fun accentOf(type: Int): Color = when (type) {
    1 -> AppColors.expense
    2 -> AppColors.income
    else -> AppColors.accent
}

@Composable
private fun amountColor(state: RecordViewModel.UiState): Color = when {
    state.resultYuan == null -> MaterialTheme.colorScheme.onSurfaceVariant
    state.type == 1 -> AppColors.expense
    state.type == 2 -> AppColors.income
    else -> MaterialTheme.colorScheme.onSurface
}

private fun amountText(state: RecordViewModel.UiState): String =
    state.resultYuan?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "0.00"

/** 元信息条上的日期：今天不占字，非今天才显示月日 */
private fun dateShortLabel(date: LocalDate): String =
    if (date == LocalDate.now()) "今天" else "${date.monthValue}月${date.dayOfMonth}日"

/* ---------------- 金额 ---------------- */

/* ---------------- 类型 / 分类 / 账户 ---------------- */

@Composable
private fun TransferSection(state: RecordViewModel.UiState, viewModel: RecordViewModel) {
    val accent = accentOf(state.type)
    SectionTitle("转出账户")
    Spacer(Modifier.height(8.dp))
    AccountChips(
        accounts = state.accounts,
        selectedId = state.selectedAccountId,
        accent = accent,
        excludeId = state.selectedToAccountId,
        onSelect = viewModel::selectAccount
    )

    Spacer(Modifier.height(12.dp))
    SectionTitle("转入账户")
    Spacer(Modifier.height(8.dp))
    AccountChips(
        accounts = state.accounts,
        selectedId = state.selectedToAccountId,
        accent = accent,
        excludeId = state.selectedAccountId,
        onSelect = viewModel::selectToAccount
    )

    // 转入「投资」账户且名下已有品种时，多一行「这笔钱算谁的」。
    // 不选也能存：钱会留在投资页的「未分配」里，不让分类变成记账的门槛。
    if (state.positions.isNotEmpty()) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "归属品种",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "选填",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            state.positions.forEach { position ->
                AppChip(
                    label = position.name,
                    selected = state.selectedPositionId == position.id,
                    tint = accent,
                    // 再点一次取消：允许「本来选了、想想还是不归类」
                    onClick = {
                        viewModel.selectPosition(
                            if (state.selectedPositionId == position.id) null else position.id
                        )
                    }
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = "选中后这笔钱会计入该品种的成本（转入算买入、转出算卖出），之后在投资页看盈亏。",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 备注或标签猜出来的分类：一行芯片，点一下等于点了对应的分类格子。
 *
 * 三条克制：
 * 1. **不自动套用**：分类是用户对这笔记账的理解，猜错了再改回来比一开始不猜更烦；
 * 2. 只在与当前选中不同时出现（相同的话什么也没省，只是多一行噪音）；
 * 3. 用户自己点过分类后就不再出现（VM 侧的 `categoryTouched`），避免「刚选好又被劝改」。
 *
 * 右侧那句「标签「打车」」/「备注「牛肉面」」说明的是**猜的依据**：包含匹配时它与用户当前的输入
 * 并不相同（输入「午饭牛肉面」、凭历史「牛肉面」猜的餐饮），把差异摆出来，用户才知道这次猜得靠不靠谱。
 */
@Composable
private fun CategorySuggestionBar(
    suggestion: CategoryGuess?,
    categoryName: String?,
    accent: Color,
    onAccept: () -> Unit
) {
    if (suggestion == null || categoryName == null) {
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp, bottom = 2.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AppChip(
            label = "猜你要记「$categoryName」",
            selected = false,
            onClick = onAccept,
            tint = accent,
            compact = true
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = when (suggestion.source) {
                GuessSource.TAG -> "标签「${suggestion.matchedText}」"
                GuessSource.REMARK -> "备注「${suggestion.matchedText}」"
            },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * 分类九宫格：每行 4 个，[onAdd] 非空时在末尾补一个「＋新建」格子。
 *
 * 高度全部交给 [modifier]（主区 weight），格子按最小尺寸收紧：27 个分类 + 新建 = 7 行，
 * 一屏装得下，不需要滑。
 */
@Composable
private fun CategoryGrid(
    categories: List<LocalCategory>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
    modifier: Modifier = Modifier,
    onAdd: (() -> Unit)? = null
) {
    if (categories.isEmpty() && onAdd == null) {
        Text(
            text = "暂无分类数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    // spacedBy + CenterVertically：分类少时整块在剩余高度里居中（不会顶在上面留一大片空白），
    // 多到一屏装不下时照常滚动
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
        contentPadding = PaddingValues(horizontal = 2.dp, vertical = 2.dp),
        verticalArrangement = Arrangement.spacedBy(1.dp, Alignment.CenterVertically),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        items(categories, key = { it.id }) { category ->
            CategoryItem(
                name = category.name,
                iconKey = category.icon,
                color = categoryColorOf(category.color, category.icon, category.name),
                selected = selectedId == category.id,
                modifier = Modifier.fillMaxWidth(),
                onClick = { onSelect(category.id) }
            )
        }
        if (onAdd != null) {
            item(key = "add-category") {
                AddCategoryItem(
                    accent = accent,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onAdd
                )
            }
        }
    }
}

/** 分类选择区末尾的「＋新建」格子（与 [CategoryItem] 同尺寸，排在分类后面） */
@Composable
private fun AddCategoryItem(accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "新建分类",
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = "新建",
            style = MaterialTheme.typography.labelSmall,
            color = accent,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CategoryItem(
    name: String,
    iconKey: String?,
    color: Color,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                // 未选中：分类色 13% 的淡底 + 同色线性图标，色相负责「找得到」；
                // 选中：分类色实心 + 反色图标，靠「填充」而不是打勾表达选中 ——
                // 打勾会把所有分类都变成同一个符号，反而认不出点的是哪个
                .background(if (selected) color else color.copy(alpha = 0.13f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIconOf(iconKey, name),
                contentDescription = null,
                tint = if (selected) categoryContentOn(color) else categoryTintOn(color),
                modifier = Modifier.size(19.dp)
            )
        }
        Spacer(Modifier.height(3.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountChips(
    accounts: List<LocalAccount>,
    selectedId: Long?,
    accent: Color,
    excludeId: Long? = null,
    onSelect: (Long) -> Unit
) {
    val visible = accounts.filter { it.id != excludeId }
    if (visible.isEmpty()) {
        Text(
            text = "还没有账户，可在账单页打开「账户管理」新建",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        visible.forEach { account ->
            AppChip(
                label = account.name,
                selected = selectedId == account.id,
                onClick = { onSelect(account.id) },
                tint = accent
            )
        }
        // 横向滚到末尾时别让最后一个芯片贴着文字边缘，看起来像被切掉
        Spacer(Modifier.width(4.dp))
    }
}

/* ---------------- 日期 / 备注 / 标签 ---------------- */

/**
 * 标签编辑弹窗（经典键盘 / 极简速记模板用）。
 *
 * 除了输入框，还列出「常用标签」快捷芯片：历史高频的排在前面，新用户则由内置兜底候选补位。
 * 芯片点选直接改写输入框里的文本（[toggleTagText]），所以「点过的」「手打的」永远是一份东西，
 * 不会出现「芯片亮着但输入框里没有」（早先按独立「已选集合」存状态时就会这样）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagInputDialog(
    initial: String,
    suggestions: List<String>,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("标签（多个用逗号分隔）", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (suggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "常用标签",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(4.dp))
                    TagSuggestionChips(
                        suggestions = suggestions,
                        picked = parseTags(text),
                        onToggle = { text = toggleTagText(text, it) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/**
 * 快捷标签芯片：点一下加上、再点一下去掉。
 *
 * 选中态不自己维护，每次都从当前标签文本解析（[picked]）—— 用户手动改过文本之后，
 * 芯片的亮灭依然跟输入框一致。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TagSuggestionChips(
    suggestions: List<String>,
    picked: List<String>,
    onToggle: (String) -> Unit,
    modifier: Modifier = Modifier,
    /** 极简模板空间紧张：改成一整行横向滚动，不再多占一行高度 */
    singleLine: Boolean = false
) {
    if (suggestions.isEmpty()) {
        return
    }
    val chips: @Composable () -> Unit = {
        suggestions.forEach { tag ->
            AppChip(
                label = tag,
                selected = tag in picked,
                onClick = { onToggle(tag) },
                tint = MaterialTheme.colorScheme.primary,
                compact = true
            )
        }
    }
    if (singleLine) {
        Row(
            modifier = modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips()
            Spacer(Modifier.width(4.dp))
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            chips()
        }
    }
}

@Composable
private fun InputDialog(
    title: String,
    initial: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.SemiBold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(text) }) { Text("确定") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/* ---------------- 自定义键盘 ---------------- */

@Composable
private fun CalcKeyboard(onKey: (String) -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3", "×"),
        listOf("4", "5", "6", "÷"),
        listOf("7", "8", "9", "−"),
        listOf(".", "0", "⌫", "+")
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
            // 键盘背景先铺到屏幕最底部，再让出系统手势区，避免最后一行按键被压住
            .safeDialogContent()
            .padding(horizontal = 10.dp, vertical = 6.dp)
    ) {
        rows.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                row.forEach { label ->
                    val isOp = label in listOf("×", "÷", "−", "+")
                    KeyButton(
                        label = label,
                        isOp = isOp,
                        modifier = Modifier.weight(1f),
                        onClick = { onKey(label) }
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            KeyButton(
                label = "C",
                isOp = true,
                modifier = Modifier.weight(1f),
                onClick = { onKey("C") }
            )
            KeyButton(
                label = "完成",
                isOp = false,
                isPrimary = true,
                modifier = Modifier.weight(2f),
                onClick = { onKey("完成") }
            )
            KeyButton(
                label = "=",
                isOp = true,
                modifier = Modifier.weight(1f),
                onClick = { onKey("=") }
            )
        }
    }
}

@Composable
private fun KeyButton(
    label: String,
    isOp: Boolean,
    isPrimary: Boolean = false,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val background = when {
        isPrimary -> MaterialTheme.colorScheme.primary
        isOp -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }
    val content = when {
        isPrimary -> MaterialTheme.colorScheme.onPrimary
        isOp -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(background)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        when (label) {
            "⌫" -> Icon(
                Icons.AutoMirrored.Filled.Backspace,
                contentDescription = "删除",
                tint = content,
                modifier = Modifier.size(20.dp)
            )

            else -> Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (isPrimary || isOp) FontWeight.SemiBold else FontWeight.Medium,
                color = content
            )
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
