package com.jizhang.app.ui.record

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.ui.category.CategoryEditorDialog
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val PLACEHOLDER = "点击填写"

/**
 * 「记一笔」全屏页。
 *
 * 五套模板共用同一个 ViewModel 与底层组件（金额引擎、分类、账户、数字键盘），
 * 但**信息架构与操作节奏完全不同**，各有适用场景：
 *
 * - 模板 1「经典键盘」：面板堆叠 + 常驻数字键盘，分类/账户/备注一屏滚动，信息最全；
 * - 模板 2「极简速记」：金额收进顶栏、分类变横向条、键盘铺满下半屏，3 秒记一笔；
 * - 模板 3「备注工作台」：卡片表单，备注/标签直接展开，数字键盘按需弹出；
 * - 模板 4「网格速记」：分类九宫格占主屏，账户/备注/标签收进键盘上方底栏；
 * - 模板 5「再来一笔」：列出最近记过的账，点一下照抄内容，改个金额就能存。
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
    /** 记账页模板：1 经典键盘 / 2 极简速记 / 3 备注工作台 / 4 网格速记 / 5 再来一笔 */
    recordTemplate: Int = 1,
    /** 非空表示「编辑这笔账单」（从账单页点进来），为空则是新增 */
    editTransaction: LocalTransaction? = null,
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

    LaunchedEffect(bookId, editTransaction) {
        val target = editTransaction
        if (target != null) viewModel.startEdit(bookId, target) else viewModel.load(bookId)
    }

    // 记账页的 ViewModel 是 Activity 作用域常驻实例（工程没有 Navigation），
    // 覆盖层只是反复 show/hide。离开页面时务必复位表单，否则下次打开会看到上次的金额 / 备注。
    // 极简模板保留类型与分类，方便连着记同类账。
    DisposableEffect(Unit) {
        onDispose { viewModel.resetForm(preserveType = recordTemplate == 2) }
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
        when (recordTemplate) {
            2 -> QuickRecordLayout(
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

            3 -> DetailRecordLayout(
                state = state,
                viewModel = viewModel,
                errorMessage = errorMessage,
                onBack = onDismiss,
                onSave = ::save,
                onOpenDate = { showDatePicker = true },
                onAddCategory = { showCategoryEditor = true },
                onKey = ::handleKey
            )

            4 -> GridRecordLayout(
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

            5 -> RecallRecordLayout(
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

            else -> ClassicRecordLayout(
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
 * 模板 1 · 经典键盘：面板堆叠 + 常驻数字键盘，信息最全
 * ================================================================== */

@Composable
private fun ClassicRecordLayout(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RecordTopBar(
                canSave = state.canSave,
                saving = state.saving,
                editing = state.editing != null,
                onBack = onBack,
                onSave = onSave
            )
        },
        bottomBar = { CalcKeyboard(onKey = onKey) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // 常驻：金额面板（核心信息，绝不能滚没）
            AmountPanel(state)
            ErrorHint(errorMessage)

            Spacer(Modifier.height(10.dp))
            TypeSwitch(
                type = state.type,
                onChange = viewModel::switchType,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                if (state.type == 3) {
                    TransferSection(state, viewModel)
                } else {
                    // 「常用」：把最近用过的分类提到最前，记账越用越顺手
                    val recent = state.recentCategoryIds.mapNotNull { id ->
                        state.categories.firstOrNull { it.id == id }
                    }
                    if (recent.isNotEmpty()) {
                        SectionTitle("常用")
                        Spacer(Modifier.height(8.dp))
                        CategoryGrid(
                            categories = recent.map { Triple(it.id, it.name, it.icon) },
                            selectedId = state.selectedCategoryId,
                            accent = accent,
                            onSelect = viewModel::selectCategory
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    SectionTitle("分类")
                    Spacer(Modifier.height(8.dp))
                    CategoryGrid(
                        categories = state.categories.map { Triple(it.id, it.name, it.icon) },
                        selectedId = state.selectedCategoryId,
                        accent = accent,
                        onSelect = viewModel::selectCategory,
                        onAdd = onAddCategory
                    )
                    Spacer(Modifier.height(12.dp))

                    SectionTitle("账户")
                    Spacer(Modifier.height(8.dp))
                    AccountChips(
                        accounts = state.accounts,
                        selectedId = state.selectedAccountId,
                        onSelect = viewModel::selectAccount
                    )
                }

                Spacer(Modifier.height(12.dp))
                DateRow(date = state.tradeDate, onClick = onOpenDate)
                Spacer(Modifier.height(8.dp))
                ExtraInfoRow(
                    remark = state.remark,
                    tags = state.tags,
                    onRemark = onOpenRemark,
                    onTags = onOpenTags
                )

                // 快捷标签：历史高频 + 内置兜底，直接摆在页面上，点一下就加上，不用再开弹窗打字
                if (state.tagSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("常用标签")
                    Spacer(Modifier.height(8.dp))
                    TagSuggestionChips(
                        suggestions = state.tagSuggestions,
                        picked = parseTags(state.tags),
                        onToggle = viewModel::toggleTag
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/* ==================================================================
 * 模板 2 · 极简速记：金额进顶栏、分类横向条、键盘铺满，3 秒一笔
 * ================================================================== */

@Composable
private fun QuickRecordLayout(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RecordTopBar(
                canSave = state.canSave,
                saving = state.saving,
                editing = state.editing != null,
                onBack = onBack,
                onSave = onSave,
                title = { QuickAmountTitle(state) }
            )
        },
        bottomBar = { CalcKeyboard(onKey = onKey) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 14.dp)
        ) {
            TypeSwitch(type = state.type, onChange = viewModel::switchType)
            ErrorHint(errorMessage, horizontalPadding = 0.dp)
            Spacer(Modifier.height(8.dp))

            // 一行搞定：日期 / 账户（转账时账户交给下方的转出-转入选择）
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                AssistChip(
                    onClick = onOpenDate,
                    label = { Text(dateLabel(state.tradeDate)) },
                    leadingIcon = {
                        Icon(Icons.Filled.DateRange, contentDescription = null, modifier = Modifier.size(16.dp))
                    }
                )
                if (state.type != 3) {
                    state.accounts.forEach { account ->
                        FilterChip(
                            selected = state.selectedAccountId == account.id,
                            onClick = { viewModel.selectAccount(account.id) },
                            label = { Text(account.name) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(6.dp))

            if (state.type == 3) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    TransferSection(state, viewModel)
                }
            } else {
                // 分类条：横向滚动，图标 + 名称，点选即定
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    state.categories.forEach { category ->
                        CategoryItem(
                            name = category.name,
                            iconKey = category.icon,
                            selected = state.selectedCategoryId == category.id,
                            accent = accent,
                            modifier = Modifier.width(62.dp),
                            onClick = { viewModel.selectCategory(category.id) }
                        )
                    }
                    AddCategoryItem(
                        accent = accent,
                        modifier = Modifier.width(62.dp),
                        onClick = onAddCategory
                    )
                }
                Spacer(Modifier.weight(1f))
            }

            // 快捷标签：一行横向滚动，点一下就记上，不占额外行高
            if (state.tagSuggestions.isNotEmpty()) {
                TagSuggestionChips(
                    suggestions = state.tagSuggestions,
                    picked = parseTags(state.tags),
                    onToggle = viewModel::toggleTag,
                    singleLine = true
                )
                Spacer(Modifier.height(2.dp))
            }

            // 备注 / 标签：折叠成一行入口，不抢主流程
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                ExtraEntry(
                    icon = Icons.Filled.Edit,
                    label = state.remark.takeIf { it.isNotBlank() } ?: "备注",
                    filled = state.remark.isNotBlank(),
                    onClick = onOpenRemark
                )
                ExtraEntry(
                    icon = Icons.Filled.LocalOffer,
                    label = state.tags.takeIf { it.isNotBlank() } ?: "标签",
                    filled = state.tags.isNotBlank(),
                    onClick = onOpenTags
                )
            }
            Spacer(Modifier.height(4.dp))
        }
    }
}

/** 极简模板的「顶栏即金额」 */
@Composable
private fun QuickAmountTitle(state: RecordViewModel.UiState) {
    Row(verticalAlignment = Alignment.Bottom) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 3.dp)
        )
        Spacer(Modifier.width(2.dp))
        Text(
            text = amountText(state),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = amountColor(state),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        if (state.expressionText.isNotBlank()) {
            Spacer(Modifier.width(8.dp))
            Text(
                text = state.expressionText,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

@Composable
private fun ExtraEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    filled: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            contentDescription = null,
            modifier = Modifier.size(15.dp),
            tint = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(5.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

/* ==================================================================
 * 模板 3 · 备注工作台：卡片表单，备注/标签直接展开，键盘按需弹出
 * ================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DetailRecordLayout(
    state: RecordViewModel.UiState,
    viewModel: RecordViewModel,
    errorMessage: String?,
    onBack: () -> Unit,
    onSave: () -> Unit,
    onOpenDate: () -> Unit,
    onAddCategory: () -> Unit,
    onKey: (String) -> Unit
) {
    var keyboardVisible by remember { mutableStateOf(false) }
    val accent = accentOf(state.type)

    Scaffold(
        // 备注/标签是页面内的输入框，键盘弹出时必须把内容顶上去
        modifier = Modifier
            .fillMaxSize()
            .imePadding(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RecordTopBar(
                canSave = state.canSave,
                saving = state.saving,
                editing = state.editing != null,
                onBack = onBack,
                onSave = onSave
            )
        },
        bottomBar = { if (keyboardVisible) CalcKeyboard(onKey = onKey) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TypeSwitch(
                type = state.type,
                onChange = viewModel::switchType,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            ErrorHint(errorMessage, horizontalPadding = 14.dp)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(Modifier.height(10.dp))

                // ① 金额 + 备注合在一张卡：备注是这一套模板的一级字段
                DetailCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { keyboardVisible = true }
                            .padding(vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.Bottom) {
                                Text(
                                    text = "¥",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.width(3.dp))
                                Text(
                                    text = amountText(state),
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = amountColor(state),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Text(
                                text = state.expressionText.ifBlank { "点这里输入金额" },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        IconButton(onClick = { keyboardVisible = !keyboardVisible }) {
                            Icon(
                                imageVector = if (keyboardVisible) {
                                    Icons.Filled.KeyboardArrowDown
                                } else {
                                    Icons.Filled.KeyboardArrowUp
                                },
                                contentDescription = if (keyboardVisible) "收起键盘" else "展开键盘",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.height(10.dp))

                    OutlinedTextField(
                        value = state.remark,
                        onValueChange = viewModel::setRemark,
                        label = { Text("备注") },
                        placeholder = { Text("这笔钱花在哪了？") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(10.dp))

                // ② 分类：表单行样式，比九宫格更适合逐个核对
                if (state.type != 3) {
                    DetailCard {
                        SectionTitle("分类")
                        Spacer(Modifier.height(4.dp))
                        CategoryRows(
                            categories = state.categories.map { Triple(it.id, it.name, it.icon) },
                            selectedId = state.selectedCategoryId,
                            accent = accent,
                            onSelect = viewModel::selectCategory,
                            onAdd = onAddCategory
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                // ③ 其他信息：账户 / 日期 / 标签
                DetailCard {
                    SectionTitle(if (state.type == 3) "转账账户" else "账户")
                    Spacer(Modifier.height(8.dp))
                    if (state.type == 3) {
                        TransferSection(state, viewModel)
                    } else {
                        AccountChips(
                            accounts = state.accounts,
                            selectedId = state.selectedAccountId,
                            onSelect = viewModel::selectAccount
                        )
                    }

                    Spacer(Modifier.height(10.dp))
                    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
                    Spacer(Modifier.height(6.dp))
                    DateRow(date = state.tradeDate, onClick = onOpenDate)
                    Spacer(Modifier.height(8.dp))

                    OutlinedTextField(
                        value = state.tags,
                        onValueChange = viewModel::setTags,
                        label = { Text("标签") },
                        placeholder = { Text("多个标签用逗号分隔") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(6.dp))
                    // 常用标签：点一下就补进上面的输入框，记账时少打字
                    TagSuggestionChips(
                        suggestions = state.tagSuggestions,
                        picked = parseTags(state.tags),
                        onToggle = viewModel::toggleTag
                    )
                }

                Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 表单式卡片容器：大圆角、靠留白分层 */
@Composable
private fun DetailCard(content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp),
        content = content
    )
}

/** 分类表单行：图标 + 名称 + 选中态，纵向排列 */
@Composable
private fun CategoryRows(
    categories: List<Triple<Long, String, String?>>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
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

    categories.forEach { (id, name, iconKey) ->
        val selected = selectedId == id
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(if (selected) accent.copy(alpha = 0.12f) else Color.Transparent)
                .clickable { onSelect(id) }
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = categoryIconOf(iconKey),
                    contentDescription = null,
                    tint = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (selected) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }

    if (onAdd != null) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onAdd)
                .padding(horizontal = 10.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(30.dp)
                    .clip(CircleShape)
                    .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Filled.Add,
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(17.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Text(
                text = "新建分类",
                style = MaterialTheme.typography.bodyMedium,
                color = accent
            )
        }
    }
}

/* ==================================================================
 * 模板 4 · 网格速记：分类九宫格占主屏，其他字段收进底栏，选分类最快
 * ================================================================== */

@Composable
private fun GridRecordLayout(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            // 金额进顶栏：主屏整块留给分类，眼睛和手指都不用上下跑
            RecordTopBar(
                canSave = state.canSave,
                saving = state.saving,
                editing = state.editing != null,
                onBack = onBack,
                onSave = onSave,
                title = { QuickAmountTitle(state) }
            )
        },
        bottomBar = { CalcKeyboard(onKey = onKey) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            TypeSwitch(
                type = state.type,
                onChange = viewModel::switchType,
                modifier = Modifier.padding(horizontal = 14.dp)
            )
            ErrorHint(errorMessage, horizontalPadding = 14.dp)

            // 主区：分类网格（这套模板的主角）。转账没有分类，换成转出/转入。
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 14.dp)
            ) {
                Spacer(Modifier.height(10.dp))
                if (state.type == 3) {
                    TransferSection(state, viewModel)
                } else {
                    // 「最近用过」置顶：同一批分类高频复用，不至于每次都在网格里找
                    val recent = state.recentCategoryIds.mapNotNull { id ->
                        state.categories.firstOrNull { it.id == id }
                    }
                    if (recent.isNotEmpty()) {
                        SectionTitle("最近用过")
                        Spacer(Modifier.height(8.dp))
                        CategoryGrid(
                            categories = recent.map { Triple(it.id, it.name, it.icon) },
                            selectedId = state.selectedCategoryId,
                            accent = accent,
                            onSelect = viewModel::selectCategory
                        )
                        Spacer(Modifier.height(12.dp))
                    }

                    SectionTitle("分类")
                    Spacer(Modifier.height(8.dp))
                    CategoryGrid(
                        categories = state.categories.map { Triple(it.id, it.name, it.icon) },
                        selectedId = state.selectedCategoryId,
                        accent = accent,
                        onSelect = viewModel::selectCategory,
                        onAdd = onAddCategory
                    )
                }
                Spacer(Modifier.height(12.dp))
            }

            // 底栏（键盘之上）：账户 / 快捷标签 / 日期·备注·标签入口，全部内联可见
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp)
            ) {
                if (state.type != 3) {
                    AccountChips(
                        accounts = state.accounts,
                        selectedId = state.selectedAccountId,
                        onSelect = viewModel::selectAccount
                    )
                    Spacer(Modifier.height(6.dp))
                }
                TagSuggestionChips(
                    suggestions = state.tagSuggestions,
                    picked = parseTags(state.tags),
                    onToggle = viewModel::toggleTag,
                    singleLine = true
                )
                Spacer(Modifier.height(2.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    ExtraEntry(
                        icon = Icons.Filled.DateRange,
                        label = dateLabel(state.tradeDate),
                        filled = false,
                        onClick = onOpenDate
                    )
                    ExtraEntry(
                        icon = Icons.Filled.Edit,
                        label = state.remark.takeIf { it.isNotBlank() } ?: "备注",
                        filled = state.remark.isNotBlank(),
                        onClick = onOpenRemark
                    )
                    ExtraEntry(
                        icon = Icons.Filled.LocalOffer,
                        label = state.tags.takeIf { it.isNotBlank() } ?: "标签",
                        filled = state.tags.isNotBlank(),
                        onClick = onOpenTags
                    )
                }
                Spacer(Modifier.height(4.dp))
            }
        }
    }
}

/* ==================================================================
 * 模板 5 · 再来一笔：先看最近记过的账，点一下照抄内容，改个金额就能存
 * ================================================================== */

@Composable
private fun RecallRecordLayout(
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

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            RecordTopBar(
                canSave = state.canSave,
                saving = state.saving,
                editing = state.editing != null,
                onBack = onBack,
                onSave = onSave
            )
        },
        bottomBar = { CalcKeyboard(onKey = onKey) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            AmountPanel(state)
            ErrorHint(errorMessage)

            Spacer(Modifier.height(10.dp))
            TypeSwitch(
                type = state.type,
                onChange = viewModel::switchType,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(Modifier.height(10.dp))

            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp)
            ) {
                // ① 「最近记过」：早餐、打车这类反复发生的账，照抄比重新填快得多
                if (state.recentTransactions.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        SectionTitle("最近记过")
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "点一下照抄",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    state.recentTransactions.forEach { transaction ->
                        val category = state.categoryOf(transaction)
                        RecentTransactionCard(
                            transaction = transaction,
                            categoryName = category?.name,
                            iconKey = category?.icon,
                            onClick = { viewModel.reuseRecent(transaction) }
                        )
                        Spacer(Modifier.height(8.dp))
                    }
                    Spacer(Modifier.height(4.dp))
                }

                // ② 照抄之后照样能改：分类 / 账户 / 日期 / 备注 / 标签
                if (state.type == 3) {
                    TransferSection(state, viewModel)
                } else {
                    SectionTitle("分类")
                    Spacer(Modifier.height(8.dp))
                    CategoryGrid(
                        categories = state.categories.map { Triple(it.id, it.name, it.icon) },
                        selectedId = state.selectedCategoryId,
                        accent = accent,
                        onSelect = viewModel::selectCategory,
                        onAdd = onAddCategory
                    )
                    Spacer(Modifier.height(12.dp))
                }

                DateRow(date = state.tradeDate, onClick = onOpenDate)
                Spacer(Modifier.height(8.dp))
                ExtraInfoRow(
                    remark = state.remark,
                    tags = state.tags,
                    onRemark = onOpenRemark,
                    onTags = onOpenTags
                )

                if (state.tagSuggestions.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    SectionTitle("常用标签")
                    Spacer(Modifier.height(8.dp))
                    TagSuggestionChips(
                        suggestions = state.tagSuggestions,
                        picked = parseTags(state.tags),
                        onToggle = viewModel::toggleTag
                    )
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

/** 最近账目里的一条：左边分类图标 + 名称/备注/日期，右边金额，点整行照抄 */
@Composable
private fun RecentTransactionCard(
    transaction: LocalTransaction,
    categoryName: String?,
    iconKey: String?,
    onClick: () -> Unit
) {
    val accent = accentOf(transaction.type)
    val date = Instant.ofEpochMilli(transaction.tradeTime)
        .atZone(ZoneId.systemDefault())
        .toLocalDate()
    val remark = transaction.remark.orEmpty()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surface)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = categoryIconOf(iconKey),
                contentDescription = null,
                tint = accent,
                modifier = Modifier.size(18.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = categoryName ?: typeLabelOf(transaction.type),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (remark.isBlank()) dateLabel(date) else "$remark · ${dateLabel(date)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            text = signedAmountText(transaction),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = accent
        )
    }
}

/** 最近账目在「当前分类表」里的分类：分类是按收支类型缓存的，类型对不上就查不到 */
private fun RecordViewModel.UiState.categoryOf(transaction: LocalTransaction): LocalCategory? =
    if (transaction.type == type) categories.firstOrNull { it.id == transaction.categoryId } else null

/** 「转账 / 收入 / 支出」：分类查不到时用它兜底显示 */
private fun typeLabelOf(type: Int): String = when (type) {
    2 -> "收入"
    3 -> "转账"
    else -> "支出"
}

/** 带正负号的金额文本：「+12.00 / -12.00 / 12.00」 */
private fun signedAmountText(transaction: LocalTransaction): String {
    val sign = when (transaction.type) {
        1 -> "-"
        2 -> "+"
        else -> ""
    }
    return sign + BigDecimal.valueOf(transaction.amount, 2)
        .setScale(2, RoundingMode.HALF_UP)
        .toPlainString()
}

/* ==================================================================
 * 共用组件
 * ================================================================== */

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecordTopBar(
    canSave: Boolean,
    saving: Boolean,
    onBack: () -> Unit,
    onSave: () -> Unit,
    /** 编辑已有账单：标题与按钮文案都要和「记一笔」区分开 */
    editing: Boolean = false,
    title: @Composable () -> Unit = {
        Text(if (editing) "编辑账单" else "记一笔", fontWeight = FontWeight.Bold)
    }
) {
    TopAppBar(
        title = title,
        // 用默认 windowInsets：窗口在 Android 15+ 是 edge-to-edge，
        // 顶部状态栏/挖孔必须由这个顶栏自己让位
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
            }
        },
        actions = {
            TextButton(enabled = canSave && !saving, onClick = onSave) {
                Text(
                    when {
                        saving -> "保存中…"
                        editing -> "保存修改"
                        else -> "保存"
                    },
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    )
}

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

private fun dateLabel(date: LocalDate): String =
    "${date.monthValue} 月 ${date.dayOfMonth} 日"

/* ---------------- 金额 ---------------- */

@Composable
private fun AmountPanel(state: RecordViewModel.UiState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = amountText(state),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor(state)
        )
        if (state.expressionText.isNotBlank()) {
            Spacer(Modifier.width(10.dp))
            Text(
                text = state.expressionText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp)
            )
        }
    }
}

/* ---------------- 类型 / 分类 / 账户 ---------------- */

@Composable
private fun TypeSwitch(type: Int, onChange: (Int) -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(4.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        SwitchTab("支出", type == 1, AppColors.expense, Modifier.weight(1f)) { onChange(1) }
        SwitchTab("收入", type == 2, AppColors.income, Modifier.weight(1f)) { onChange(2) }
        SwitchTab("转账", type == 3, AppColors.accent, Modifier.weight(1f)) { onChange(3) }
    }
}

@Composable
private fun SwitchTab(
    label: String,
    selected: Boolean,
    selectedColor: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(if (selected) selectedColor else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge
        )
    }
}

@Composable
private fun TransferSection(state: RecordViewModel.UiState, viewModel: RecordViewModel) {
    SectionTitle("转出账户")
    Spacer(Modifier.height(8.dp))
    AccountChips(
        accounts = state.accounts,
        selectedId = state.selectedAccountId,
        excludeId = state.selectedToAccountId,
        onSelect = viewModel::selectAccount
    )

    Spacer(Modifier.height(12.dp))
    SectionTitle("转入账户")
    Spacer(Modifier.height(8.dp))
    AccountChips(
        accounts = state.accounts,
        selectedId = state.selectedToAccountId,
        excludeId = state.selectedAccountId,
        onSelect = viewModel::selectToAccount
    )
}

/** 分类九宫格：每行 4 个，超出自动换行；[onAdd] 非空时在末尾补一个「＋新建」格子 */
@Composable
private fun CategoryGrid(
    categories: List<Triple<Long, String, String?>>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit,
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

    val cellCount = categories.size + if (onAdd == null) 0 else 1
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (0 until cellCount).chunked(4).forEach { rowIndices ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                rowIndices.forEach { index ->
                    if (index < categories.size) {
                        val (id, name, iconKey) = categories[index]
                        CategoryItem(
                            name = name,
                            iconKey = iconKey,
                            selected = selectedId == id,
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = { onSelect(id) }
                        )
                    } else if (onAdd != null) {
                        AddCategoryItem(
                            accent = accent,
                            modifier = Modifier.weight(1f),
                            onClick = onAdd
                        )
                    }
                }
                repeat(4 - rowIndices.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
    }
}

/** 分类选择区末尾的「＋新建」格子（与 [CategoryItem] 同尺寸，排在分类后面） */
@Composable
private fun AddCategoryItem(accent: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .border(1.dp, accent.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "新建分类",
                tint = accent,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(Modifier.height(4.dp))
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
    selected: Boolean,
    accent: Color,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(if (selected) accent else MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            } else {
                Icon(
                    imageVector = categoryIconOf(iconKey),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = name,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun AccountChips(
    accounts: List<LocalAccount>,
    selectedId: Long?,
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
            FilterChip(
                selected = selectedId == account.id,
                onClick = { onSelect(account.id) },
                label = { Text(account.name) }
            )
        }
    }
}

/* ---------------- 日期 / 备注 / 标签 ---------------- */

@Composable
private fun DateRow(date: LocalDate, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("日期", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        Text(
            text = "${date.year} 年 ${date.monthValue} 月 ${date.dayOfMonth} 日",
            style = MaterialTheme.typography.bodyMedium
        )
        Spacer(Modifier.width(2.dp))
        Icon(
            Icons.Filled.ChevronRight,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ExtraInfoRow(remark: String, tags: String, onRemark: () -> Unit, onTags: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        InfoField(
            label = "备注",
            value = remark.ifBlank { PLACEHOLDER },
            placeholder = remark.isBlank(),
            modifier = Modifier.weight(1f),
            onClick = onRemark
        )
        InfoField(
            label = "标签",
            value = tags.ifBlank { PLACEHOLDER },
            placeholder = tags.isBlank(),
            modifier = Modifier.weight(1f),
            onClick = onTags
        )
    }
}

@Composable
private fun InfoField(
    label: String,
    value: String,
    placeholder: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 6.dp)
    ) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = if (placeholder) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

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
            FilterChip(
                selected = tag in picked,
                onClick = { onToggle(tag) },
                label = { Text(tag) }
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
        }
    } else {
        FlowRow(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
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
