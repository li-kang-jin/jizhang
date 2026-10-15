package com.jizhang.app.ui.record

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.data.local.entity.LocalAccount
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import java.math.RoundingMode
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

private const val PLACEHOLDER = "点击填写"

/**
 * 「记一笔」全屏页。
 * <p>
 * 设计目标：一次打开完成全部信息 ——
 * 自定义数字键盘（含 + − × ÷ 四则运算，不用系统键盘）、金额大字实时预览、
 * 类型 / 分类 / 账户（转账为转出+转入）、日期、备注、标签。
 * 键盘固定在底部，上半部分可滚动，单手即可操作。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecordScreen(
    bookId: Long,
    onDismiss: () -> Unit,
    onSaved: () -> Unit,
    viewModel: RecordViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showRemarkDialog by remember { mutableStateOf(false) }
    var showTagDialog by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(bookId) { viewModel.load(bookId) }

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
        // 用 Scaffold 精确分配「顶栏 / 底部键盘 / 中间滚动」三个区域：
        // 在 Dialog 里用 Column + weight 时高度约束不确定，底部组件会被挤出屏幕
        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            topBar = {
                TopAppBar(
                    title = { Text("记一笔", fontWeight = FontWeight.Bold) },
                    // 用默认 windowInsets：窗口在 Android 15+ 是 edge-to-edge，
                    // 顶部状态栏/挖孔必须由这个顶栏自己让位
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    },
                    actions = {
                        TextButton(
                            enabled = state.canSave && !state.saving,
                            onClick = { save() }
                        ) {
                            Text(
                                if (state.saving) "保存中…" else "保存",
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                )
            },
            bottomBar = { CalcKeyboard(onKey = ::handleKey) }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
            ) {
                // 常驻：金额面板（核心信息，绝不能滚没）
                AmountPanel(state)

                errorMessage?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }

                Spacer(Modifier.height(10.dp))

                // 常驻：类型切换
                TypeSwitch(
                    type = state.type,
                    onChange = viewModel::switchType,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(Modifier.height(10.dp))

                // 中间滚动区域：其余全部信息（Scaffold 已给出确定高度，weight 可靠）
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    if (state.type == 3) {
                        TransferSection(state, viewModel)
                    } else {
                        SectionTitle("分类")
                        Spacer(Modifier.height(8.dp))
                        CategoryGrid(
                            categories = state.categories.map { Triple(it.id, it.name, it.icon) },
                            selectedId = state.selectedCategoryId,
                            accent = if (state.type == 1) AppColors.expense else AppColors.income,
                            onSelect = viewModel::selectCategory
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
                    DateRow(date = state.tradeDate) { showDatePicker = true }
                    Spacer(Modifier.height(8.dp))
                    ExtraInfoRow(
                        remark = state.remark,
                        tags = state.tags,
                        onRemark = { showRemarkDialog = true },
                        onTags = { showTagDialog = true }
                    )
                    Spacer(Modifier.height(12.dp))
                }
            }
        }
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
        InputDialog(
            title = "标签（多个用逗号分隔）",
            initial = state.tags,
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

/* ---------------- 金额 ---------------- */

@Composable
private fun AmountPanel(state: RecordViewModel.UiState) {
    val result = state.resultYuan
    val amountColor = when {
        result == null -> MaterialTheme.colorScheme.onSurfaceVariant
        state.type == 1 -> AppColors.expense
        state.type == 2 -> AppColors.income
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        verticalAlignment = Alignment.Bottom
    ) {
        Text(
            text = "¥",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = result?.setScale(2, RoundingMode.HALF_UP)?.toPlainString() ?: "0.00",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = amountColor
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

/** 分类九宫格：每行 4 个，超出自动换行 */
@Composable
private fun CategoryGrid(
    categories: List<Triple<Long, String, String?>>,
    selectedId: Long?,
    accent: Color,
    onSelect: (Long) -> Unit
) {
    if (categories.isEmpty()) {
        Text(
            text = "暂无分类数据",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        categories.chunked(4).forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                row.forEach { (id, name, iconKey) ->
                    CategoryItem(
                        name = name,
                        iconKey = iconKey,
                        selected = selectedId == id,
                        accent = accent,
                        modifier = Modifier.weight(1f),
                        onClick = { onSelect(id) }
                    )
                }
                repeat(4 - row.size) {
                    Spacer(Modifier.weight(1f))
                }
            }
        }
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
