package com.jizhang.app.ui.budget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.jizhang.app.data.local.entity.LocalRecurringExpense
import com.jizhang.app.data.remote.dto.CategoryStatDto
import com.jizhang.app.data.repository.FixedReservation
import com.jizhang.app.data.repository.FixedReservationItem
import com.jizhang.app.data.repository.RecurringExpenses
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.AppChip
import com.jizhang.app.ui.common.categoryColorOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.theme.AppColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/**
 * 固定支出管理。
 * <p>
 * 只登记规则，**不自动生成账单**：本期还没到期的部分会从预算的「每天可花」里预留掉。
 * 规则跟随账号同步（换设备、家庭账本里的成员都能看到），但「本期预留多少」始终按本机时区
 * 与本机流水实时计算，不走服务端 —— 否则会变成一份会过期的快照。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringExpenseDialog(
    bookId: Long,
    recurring: List<LocalRecurringExpense>,
    reservation: FixedReservation,
    expenseCategories: List<CategoryStatDto>,
    onSave: (LocalRecurringExpense) -> Unit,
    onDelete: (Long) -> Unit,
    onDismiss: () -> Unit
) {
    var editing by remember { mutableStateOf<LocalRecurringExpense?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<LocalRecurringExpense?>(null) }

    // 已在本期预留的项：列表上直接标出「什么时候扣多少」
    val reservedByItem: Map<Long, FixedReservationItem> =
        reservation.items.associateBy { it.expense.id }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = { Text("固定支出", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item {
                    Text(
                        text = "房租、房贷、保险、会员费这类每期都要花的钱登记在这里。" +
                            "本期还没到期的部分会先从预算里预留出来，避免月中交完房租，每天可花就被算成 0。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (recurring.isEmpty()) {
                    item {
                        Text(
                            text = "还没有固定支出",
                            style = MaterialTheme.typography.titleSmall,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                }

                items(recurring, key = { it.id }) { item ->
                    RecurringRow(
                        item = item,
                        reservation = reservedByItem[item.id],
                        categoryName = expenseCategories
                            .firstOrNull { it.categoryId == item.categoryId }?.categoryName,
                        onEdit = {
                            editing = item
                            showEditor = true
                        },
                        onDelete = { deleting = item }
                    )
                }

                item {
                    Spacer(Modifier.height(6.dp))
                    Button(
                        onClick = {
                            editing = null
                            showEditor = true
                        },
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("新增固定支出")
                    }
                }
            }
        }
    }

    if (showEditor) {
        RecurringExpenseEditorDialog(
            bookId = bookId,
            item = editing,
            expenseCategories = expenseCategories,
            onDismiss = { showEditor = false },
            onSave = {
                onSave(it)
                showEditor = false
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除固定支出") },
            text = { Text("确定删除「${target.name}」吗？已记过的账单不受影响。") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
            }
        )
    }
}

@Composable
private fun RecurringRow(
    item: LocalRecurringExpense,
    reservation: FixedReservationItem?,
    categoryName: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = item.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                if (!item.enabled) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "已停用",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }
                Spacer(Modifier.weight(1f))
                Text(
                    text = "${formatAmount(item.amount)} 元",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = "编辑",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.DeleteOutline,
                        contentDescription = "删除",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = buildString {
                    append(RecurringExpenses.scheduleLabel(item))
                    if (!categoryName.isNullOrBlank()) append(" · $categoryName")
                },
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            if (reservation != null) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "本期预留 ${formatAmount(reservation.amount)} 元" +
                        "（${reservation.nextDate.monthValue}/${reservation.nextDate.dayOfMonth} 起，" +
                        "共 ${reservation.count} 笔）",
                    style = MaterialTheme.typography.labelSmall,
                    color = AppColors.accent
                )
            }
        }
    }
}

/**
 * 固定支出编辑弹窗。
 * <p>
 * `startDate` 默认今天：预算预留只看「今天及以后」，所以对已有固定支出来说
 * 起始日填早填晚结果一样，只有「下个月才开始」这种情况才需要往后调。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringExpenseEditorDialog(
    bookId: Long,
    item: LocalRecurringExpense?,
    expenseCategories: List<CategoryStatDto>,
    onDismiss: () -> Unit,
    onSave: (LocalRecurringExpense) -> Unit
) {
    var name by remember { mutableStateOf(item?.name.orEmpty()) }
    var amountText by remember {
        mutableStateOf(item?.let { formatAmountPlain(it.amount) } ?: "")
    }
    var freq by remember { mutableStateOf(item?.freq ?: LocalRecurringExpense.FREQ_MONTHLY) }
    var dayOfMonth by remember { mutableStateOf((item?.dayOfMonth ?: 1).toString()) }
    var dayOfWeek by remember { mutableStateOf(item?.dayOfWeek ?: 1) }
    var monthOfYear by remember { mutableStateOf((item?.monthOfYear ?: 1).toString()) }
    var categoryId by remember { mutableStateOf(item?.categoryId ?: 0L) }
    var enabled by remember { mutableStateOf(item?.enabled ?: true) }
    var startDate by remember {
        mutableStateOf(
            item?.startDate?.takeIf { it > 0L }?.let { millisToDate(it) } ?: LocalDate.now()
        )
    }
    var showDatePicker by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    // 保存逻辑抽成局部函数：底部按钮行要用，校验失败的错误也显示在金额框下方
    fun submit() {
        val trimmedName = name.trim()
        if (trimmedName.isEmpty()) {
            error = "请输入名称"
            return
        }
        val yuan = amountText.trim().toBigDecimalOrNull()
        if (yuan == null || yuan.signum() <= 0) {
            error = "请输入大于 0 的金额"
            return
        }
        val cents = yuan.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
        onSave(
            LocalRecurringExpense(
                id = item?.id ?: 0L,
                bookId = item?.bookId?.takeIf { it > 0L } ?: bookId,
                name = trimmedName,
                categoryId = categoryId,
                freq = freq,
                dayOfWeek = dayOfWeek,
                dayOfMonth = dayOfMonth.toIntOrNull()?.coerceIn(1, 31) ?: 1,
                monthOfYear = monthOfYear.toIntOrNull()?.coerceIn(1, 12) ?: 1,
                amount = cents,
                startDate = startDate.atStartOfDay(ZoneId.systemDefault())
                    .toInstant().toEpochMilli(),
                endDate = item?.endDate,
                enabled = enabled,
                remark = item?.remark
            )
        )
    }

    /*
     * 用整屏覆盖层而不是 AlertDialog：这些字段（名称 / 金额 / 频率 / 具体日期 / 生效日 / 分类 / 启用中）
     * 在 Android 15+ 上会被弹窗窗口裁掉一截 —— 底部「启用中」和按钮直接看不见，
     * 中间也不一定能滚。整屏后：顶栏 + 可滚动表单 + 底部固定按钮，键盘弹出也不会盖住输入框。
     */
    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().imePadding()) {
            TopAppBar(
                title = {
                    Text(
                        text = if (item == null) "新增固定支出" else "编辑固定支出",
                        fontWeight = FontWeight.Bold
                    )
                },
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
                    .padding(top = 4.dp, bottom = 8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        error = null
                    },
                    label = { Text("名称") },
                    placeholder = { Text("例如：房租") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = amountText,
                    onValueChange = {
                        amountText = it
                        error = null
                    },
                    label = { Text("金额（元）") },
                    singleLine = true,
                    isError = error != null,
                    modifier = Modifier.fillMaxWidth()
                )
                error?.let {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = it,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelSmall
                    )
                }

                Spacer(Modifier.height(14.dp))
                Text("频率", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(4.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        LocalRecurringExpense.FREQ_WEEKLY,
                        LocalRecurringExpense.FREQ_MONTHLY,
                        LocalRecurringExpense.FREQ_YEARLY
                    ).forEach { value ->
                        AppChip(
                            label = RecurringExpenses.freqLabel(value),
                            selected = freq == value,
                            onClick = { freq = value }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))
                when (freq) {
                    LocalRecurringExpense.FREQ_WEEKLY -> {
                        Text("每周几", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(6.dp))
                        // 七个等宽格子：原来 7 个 FilterChip 横着排会溢出，还得左右划才能看到周日
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            (1..7).forEach { day ->
                                AppChip(
                                    label = when (day) {
                                        1 -> "一"
                                        2 -> "二"
                                        3 -> "三"
                                        4 -> "四"
                                        5 -> "五"
                                        6 -> "六"
                                        else -> "日"
                                    },
                                    selected = dayOfWeek == day,
                                    onClick = { dayOfWeek = day },
                                    compact = true,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    LocalRecurringExpense.FREQ_YEARLY -> Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = monthOfYear,
                            onValueChange = { monthOfYear = it.filter(Char::isDigit).take(2) },
                            label = { Text("月份") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = dayOfMonth,
                            onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) },
                            label = { Text("日") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    else -> OutlinedTextField(
                        value = dayOfMonth,
                        onValueChange = { dayOfMonth = it.filter(Char::isDigit).take(2) },
                        label = { Text("每月几号") },
                        supportingText = { Text("当月天数不足时按当月最后一天") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                Spacer(Modifier.height(12.dp))
                Text("生效起始日", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { showDatePicker = true }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${startDate.year} 年 ${startDate.monthValue} 月 ${startDate.dayOfMonth} 日",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        text = "修改",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (expenseCategories.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("归类到分类（可选）", style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        AppChip(
                            label = "不指定",
                            selected = categoryId == 0L,
                            onClick = { categoryId = 0L },
                            compact = true
                        )
                        expenseCategories.forEach { category ->
                            AppChip(
                                label = category.categoryName,
                                selected = categoryId == category.categoryId,
                                onClick = { categoryId = category.categoryId ?: 0L },
                                tint = categoryColorOf(null, null, category.categoryName),
                                compact = true
                            )
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("启用中", style = MaterialTheme.typography.bodyMedium)
                    Spacer(Modifier.weight(1f))
                    Switch(checked = enabled, onCheckedChange = { enabled = it })
                }
            }

            // 底部固定操作区：先铺背景再补安全区，否则手势条下面会露出一条空白
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .safeDialogContent()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("取消") }
                Button(
                    onClick = { submit() },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.weight(1f)
                ) { Text("保存") }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = startDate.atStartOfDay(ZoneId.systemDefault())
                .toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        // 选择器给的是「所选日期 UTC 0 点」，直接当本地时间用会整体偏一天
                        startDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneOffset.UTC)
                            .toLocalDate()
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

private fun millisToDate(millis: Long): LocalDate =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()

/** 分 -> 不带货币符号的元，用于回填输入框（`formatAmount` 会带千分位，不适合再编辑） */
private fun formatAmountPlain(cents: Long): String {
    val yuan = java.math.BigDecimal(cents).movePointLeft(2)
    return yuan.stripTrailingZeros().toPlainString()
}
