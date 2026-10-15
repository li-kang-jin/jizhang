package com.jizhang.app.ui.account

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.entity.AccountWithBalance
import com.jizhang.app.data.repository.AccountRepository
import com.jizhang.app.ui.common.accountTypeLabel
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.formatAmount
import com.jizhang.app.ui.common.safeDialogContent
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val accountRepository: AccountRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val accounts: List<AccountWithBalance> = emptyList(),
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long) {
        if (bound == bookId) {
            return
        }
        bound = bookId
        _state.update { it.copy(bookId = bookId) }
        viewModelScope.launch {
            accountRepository.observeAccounts(bookId).collect { list ->
                _state.update { it.copy(accounts = list) }
            }
        }
    }

    fun save(id: Long?, name: String, type: Int, initialBalance: Long, onDone: () -> Unit) {
        val bookId = _state.value.bookId
        viewModelScope.launch {
            val result = if (id == null) {
                accountRepository.create(bookId, name, type, initialBalance)
            } else {
                accountRepository.update(id, bookId, name, type, initialBalance)
            }
            result.onSuccess {
                _state.update { it.copy(message = if (id == null) "账户已创建" else "账户已更新") }
                onDone()
            }.onFailure { e ->
                _state.update { it.copy(message = e.message ?: "操作失败") }
            }
        }
    }

    fun delete(id: Long) {
        viewModelScope.launch {
            accountRepository.delete(id)
                .onSuccess { _state.update { it.copy(message = "账户已删除") } }
                .onFailure { e -> _state.update { it.copy(message = e.message ?: "删除失败") } }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

/**
 * 账户管理。
 * <p>
 * 之前 App 里完全没有账户入口，只能依赖服务端同步过来的账户，
 * 一旦同步不到就陷入「无法记账」的死胡同。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountListDialog(
    bookId: Long,
    onDismiss: () -> Unit,
    viewModel: AccountViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<AccountWithBalance?>(null) }
    var showEditor by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<AccountWithBalance?>(null) }

    LaunchedEffect(bookId) { viewModel.bind(bookId) }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        // safeDialogContent：让整个页面在底部让出系统手势区，
        // 否则列表最后一项（新建账户按钮）会被手势条压住；顶部由 TopAppBar 自己处理
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
                TopAppBar(
                    title = { Text("账户管理", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )

                // weight(1f)：顶栏之外的剩余空间，用 fillMaxSize 会超出屏幕高度被裁掉
                LazyColumn(
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.accounts.isEmpty()) {
                        item {
                            Text(
                                text = "还没有账户，点下方按钮新建一个",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 24.dp)
                            )
                        }
                    }

                    items(state.accounts, key = { it.id }) { account ->
                        AccountRow(
                            account = account,
                            onEdit = {
                                editing = account
                                showEditor = true
                            },
                            onDelete = { deleting = account }
                        )
                    }

                    item {
                        Spacer(Modifier.height(8.dp))
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
                            Text("新建账户")
                        }
                    }
                }
            }
    }

    if (showEditor) {
        AccountEditor(
            account = editing,
            onDismiss = { showEditor = false },
            onSave = { name, type, initial ->
                viewModel.save(editing?.id, name, type, initial) { showEditor = false }
            }
        )
    }

    deleting?.let { target ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("删除账户") },
            text = { Text("确定删除「${target.name}」吗？该账户下的交易不会一并删除。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(target.id)
                    deleting = null
                }) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消") }
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

@Composable
private fun AccountRow(
    account: AccountWithBalance,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(account.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(
                    text = accountTypeLabel(account.type) + " · 初始 ¥" + formatAmount(account.initialBalance),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "¥${formatAmount(account.balance)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = onEdit) {
                Icon(Icons.Filled.Edit, contentDescription = "编辑", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
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

@Composable
private fun AccountEditor(
    account: AccountWithBalance?,
    onDismiss: () -> Unit,
    onSave: (String, Int, Long) -> Unit
) {
    var name by remember { mutableStateOf(account?.name.orEmpty()) }
    var type by remember { mutableStateOf(account?.type ?: 1) }
    var initialText by remember {
        mutableStateOf(account?.let { formatAmount(it.initialBalance) } ?: "0.00")
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (account == null) "新建账户" else "编辑账户") },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("账户名称") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = initialText,
                    onValueChange = { initialText = it.filter { c -> c.isDigit() || c == '.' } },
                    label = { Text("初始余额（元）") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Text("账户类型", style = MaterialTheme.typography.labelMedium)
                Spacer(Modifier.height(6.dp))
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(listOf(1, 2, 3, 4), listOf(5, 6, 7)).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            row.forEach { value ->
                                FilterChip(
                                    selected = type == value,
                                    onClick = { type = value },
                                    label = { Text(accountTypeLabel(value)) }
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val initial = ((initialText.toDoubleOrNull() ?: 0.0) * 100).toLong()
                    if (name.isBlank()) {
                        return@TextButton
                    }
                    onSave(name.trim(), type, initial)
                }
            ) { Text("保存") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    )
}
