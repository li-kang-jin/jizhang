package com.jizhang.app.ui.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.local.entity.LocalCategory
import com.jizhang.app.data.local.entity.LocalTransaction
import com.jizhang.app.data.repository.AccountRepository
import com.jizhang.app.data.repository.CategoryRepository
import com.jizhang.app.data.repository.TransactionRepository
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.accountColorOf
import com.jizhang.app.ui.common.accountIconOf
import com.jizhang.app.ui.common.categoryTintOn
import com.jizhang.app.ui.common.accountTypeLabel
import com.jizhang.app.ui.common.categoryIconOf
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.formatAmountInput
import com.jizhang.app.ui.common.formatDate
import com.jizhang.app.ui.common.formatSignedAmount
import com.jizhang.app.ui.common.monthStartMillis
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.common.transactionTypeLabel
import com.jizhang.app.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.YearMonth
import javax.inject.Inject
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 详情页最多列几条流水（账户流水可能很多，先给最近这些） */
private const val DETAIL_LIMIT = 60

@HiltViewModel
class AccountDetailViewModel @Inject constructor(
    private val accountRepository: AccountRepository,
    private val transactionRepository: TransactionRepository,
    private val categoryRepository: CategoryRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val accountId: Long = 0L,
        /** 实时账户（余额 / 备注 / 归档都会跟着列表变） */
        val account: AccountWithBalance? = null,
        val transactions: List<LocalTransaction> = emptyList(),
        val categoryById: Map<Long, LocalCategory> = emptyMap(),
        val monthIncome: Long = 0L,
        val monthExpense: Long = 0L,
        val calibrating: Boolean = false,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long, accountId: Long) {
        if (bound == accountId) {
            return
        }
        bound = accountId
        _state.update { it.copy(bookId = bookId, accountId = accountId) }

        viewModelScope.launch {
            accountRepository.observeAccounts(bookId).collect { list ->
                _state.update { it.copy(account = list.firstOrNull { a -> a.id == accountId }) }
            }
        }
        viewModelScope.launch {
            val categories = runCatching { categoryRepository.listCategories(bookId) }
                .getOrDefault(emptyList())
            _state.update { it.copy(categoryById = categories.associateBy { c -> c.id }) }
        }
        viewModelScope.launch { refresh(bookId, accountId) }
    }

    private suspend fun refresh(bookId: Long, accountId: Long) {
        val transactions = runCatching { transactionRepository.listByAccount(accountId, DETAIL_LIMIT) }
            .getOrDefault(emptyList())
        val month = YearMonth.now()
        val sum = runCatching {
            transactionRepository.sumByAccount(
                accountId,
                monthStartMillis(month),
                monthStartMillis(month.plusMonths(1))
            )
        }.getOrNull()
        _state.update {
            it.copy(
                transactions = transactions,
                monthIncome = sum?.income ?: 0L,
                monthExpense = sum?.expense ?: 0L
            )
        }
    }

    /**
     * 余额校准：把账户对到 [targetFen]（分），差额记成一笔「余额调整」账单。
     * <p>
     * 账实不符（少还了几块、现金对不上）时用它，而不是去改初始余额 —— 调整会留在流水里可追溯。
     */
    fun calibrate(targetFen: Long) {
        val current = _state.value
        val accountId = current.accountId
        if (accountId <= 0L) {
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(calibrating = true) }
            accountRepository.adjustBalance(current.bookId, accountId, targetFen)
                .onSuccess { diff ->
                    refresh(current.bookId, accountId)
                    _state.update {
                        it.copy(
                            calibrating = false,
                            message = if (diff == 0L) {
                                "余额本来就对得上，无需调整"
                            } else {
                                "已校准：差额 " + (if (diff > 0) "+" else "-") +
                                    formatAmount(abs(diff)) + " 记为一笔「余额调整」"
                            }
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(calibrating = false, message = e.message ?: "校准失败") }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

/**
 * 账户详情：余额 / 本月收支 / 该账户流水 / 余额校准。
 * <p>
 * 覆盖层由页面根部（MainScreen）统一挂载，和记账页、账户管理页同一套约定。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountDetailDialog(
    bookId: Long,
    accountId: Long,
    onDismiss: () -> Unit,
    hideAmounts: Boolean = false,
    viewModel: AccountDetailViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var calibrating by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, accountId) { viewModel.bind(bookId, accountId) }

    val account = state.account

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = account?.name ?: "账户详情",
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (account != null) {
                            Text(
                                text = accountTypeLabel(account.type) +
                                    if (account.archived) " · 已归档" else "",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )

            if (account == null) {
                Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("账户不存在或已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                return@Column
            }

            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                item { BalanceCard(account, hideAmounts) }

                item {
                    MonthCard(
                        income = state.monthIncome,
                        expense = state.monthExpense,
                        hideAmounts = hideAmounts
                    )
                }

                item {
                    Button(
                        onClick = { calibrating = true },
                        enabled = !state.calibrating,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(46.dp)
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (state.calibrating) "校准中…" else "余额校准")
                    }
                }

                item {
                    Text(
                        text = "流水（最近 ${state.transactions.size} 条）",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                }

                if (state.transactions.isEmpty()) {
                    item {
                        Text(
                            text = "这个账户还没有账单",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                items(state.transactions, key = { it.id }) { transaction ->
                    TransactionRow(
                        transaction = transaction,
                        category = state.categoryById[transaction.categoryId],
                        accountId = accountId,
                        hideAmounts = hideAmounts
                    )
                }
            }
        }
    }

    if (calibrating && account != null) {
        CalibrateDialog(
            account = account,
            onDismiss = { calibrating = false },
            onConfirm = { targetFen ->
                calibrating = false
                viewModel.calibrate(targetFen)
            }
        )
    }

    state.message?.let {
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
            title = { Text("提示") },
            text = { Text(it) }
        )
    }
}

/* ==================== 各部分 ==================== */

@Composable
private fun BalanceCard(account: AccountWithBalance, hideAmounts: Boolean) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // 与账户列表同一套语义色：现金绿 / 储蓄卡蓝 / 信用卡紫
            val accColor = accountColorOf(account.icon, account.type)
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(accColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = accountIconOf(account.icon),
                    contentDescription = null,
                    tint = categoryTintOn(accColor),
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(Modifier.width(10.dp))
            Column {
                Text("当前余额", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    text = amountText(account.balance, hideAmounts),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (account.balance < 0) AppColors.expense else MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "初始余额",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = amountText(account.initialBalance, hideAmounts),
                style = MaterialTheme.typography.labelSmall
            )
        }
        if (!account.note.isNullOrBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = account.note.orEmpty(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun MonthCard(income: Long, expense: Long, hideAmounts: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text("本月收入", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "+" + amountText(income, hideAmounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.income
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text("本月支出", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(2.dp))
            Text(
                text = "-" + amountText(expense, hideAmounts),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.expense
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: LocalTransaction,
    category: LocalCategory?,
    accountId: Long,
    hideAmounts: Boolean
) {
    // 转账在这条流水里可能是转出方，也可能是转入方，文案要说清楚是哪个方向
    val isTransfer = transaction.type == 3
    val outgoing = transaction.accountId == accountId
    val title = category?.name
        ?: if (isTransfer) (if (outgoing) "转账转出" else "转账转入") else transactionTypeLabel(transaction.type)
    val remark = transaction.remark.orEmpty()
    val subtitle = if (remark.isBlank()) {
        formatDate(transaction.tradeTime)
    } else {
        "$remark · ${formatDate(transaction.tradeTime)}"
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = categoryIconOf(category?.icon, category?.name),
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        Spacer(Modifier.width(8.dp))
        Text(
            // 转账方向对这个账户而言是「进」还是「出」，所以不用记账类型直接套符号
            text = when {
                hideAmounts -> "¥ ****"
                isTransfer -> (if (outgoing) "-" else "+") + amountText(transaction.amount, false)
                else -> formatSignedAmount(transaction.type, transaction.amount)
            },
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = when {
                isTransfer -> MaterialTheme.colorScheme.onSurface
                transaction.type == 2 -> AppColors.income
                else -> AppColors.expense
            }
        )
    }
}

/** 校准弹窗：填「实际余额」，差额由系统补一笔调整账单 */
@Composable
private fun CalibrateDialog(
    account: AccountWithBalance,
    onDismiss: () -> Unit,
    onConfirm: (Long) -> Unit
) {
    // 回填输入框要用无千分位口径，否则下面的 toDoubleOrNull() 会解析失败
    var text by remember { mutableStateOf(formatAmountInput(account.balance)) }
    val target = text.toDoubleOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("余额校准") },
        text = {
            Column {
                Text(
                    text = "当前系统余额 ¥${formatAmount(account.balance)}。" +
                        "填入实际余额，差额会自动记成一笔「余额调整」（可追溯、可筛选）。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = text,
                    onValueChange = { input ->
                        text = input.filter { c -> c.isDigit() || c == '.' || c == '-' }
                    },
                    label = { Text("实际余额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                if (target != null) {
                    val diff = (target * 100).roundToInt().toLong() - account.balance
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = if (diff == 0L) {
                            "与当前余额一致，不会产生调整"
                        } else {
                            "将记一笔 " + (if (diff > 0) "收入 +" else "支出 -") + formatAmount(abs(diff))
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = target != null,
                onClick = { onConfirm(((target ?: 0.0) * 100).roundToInt().toLong()) }
            ) { Text("校准") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}

/** 金额文本：隐私模式下显示 ****；不显示正负号时用绝对值 */
private fun amountText(fen: Long, hideAmounts: Boolean): String =
    if (hideAmounts) "¥ ****" else "¥" + formatAmount(abs(fen))
