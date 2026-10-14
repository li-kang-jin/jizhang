package com.jizhang.app.ui.mine

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.BuildConfig
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.data.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val transactionRepository: TransactionRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    data class UiState(
        val nickname: String = "",
        val bookId: Long = 0L,
        val pendingCount: Int = 0,
        val lastSyncAt: String = "",
        val syncing: Boolean = false,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val bookId = tokenStore.bookIdFlow.first()
            _state.update {
                it.copy(
                    nickname = tokenStore.nicknameFlow.first(),
                    bookId = bookId,
                    lastSyncAt = tokenStore.lastSyncAt().orEmpty()
                )
            }
            if (bookId > 0L) {
                viewModelScope.launch {
                    transactionRepository.observePendingCount(bookId).collect { count ->
                        _state.update { it.copy(pendingCount = count) }
                    }
                }
            }
        }
    }

    fun sync() {
        val bookId = _state.value.bookId
        if (bookId <= 0L) {
            _state.update { it.copy(message = "未获取到账本") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            syncManager.sync(bookId)
                .onSuccess { outcome ->
                    _state.update {
                        it.copy(
                            syncing = false,
                            lastSyncAt = tokenStore.lastSyncAt().orEmpty(),
                            message = "同步完成：上传 ${outcome.pushed} 条，删除 ${outcome.deleted} 条，拉取 ${outcome.pulled} 条"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(syncing = false, message = "同步失败：${e.message}") }
                }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

@Composable
fun MineScreen(
    onLogout: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmLogout by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Filled.Person,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.size(12.dp))
                Column {
                    Text(
                        text = state.nickname.ifBlank { "未登录" },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "账本 ID：${state.bookId}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
        }

        Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Column(Modifier.padding(16.dp)) {
                InfoRow("待同步记录", "${state.pendingCount} 条")
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                InfoRow("上次同步时间", state.lastSyncAt.ifBlank { "尚未同步" })
                HorizontalDivider(Modifier.padding(vertical = 10.dp))
                InfoRow("后端地址", BuildConfig.API_BASE_URL)

                Spacer(Modifier.height(16.dp))
                Button(
                    onClick = viewModel::sync,
                    enabled = !state.syncing,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text(if (state.syncing) "同步中…" else "立即同步")
                }
            }
        }

        OutlinedButton(
            onClick = { confirmLogout = true },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text("退出登录")
        }
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
            title = { Text("提示") },
            text = { Text(message) }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    onLogout()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            },
            title = { Text("退出登录") },
            text = { Text("本地账单数据会保留，重新登录后可继续同步。") }
        )
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
