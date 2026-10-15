package com.jizhang.app.ui.mine

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.BuildConfig
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.AuthRepository
import com.jizhang.app.data.repository.PetRepository
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.data.repository.TransactionRepository
import com.jizhang.app.ui.login.LoginDialog
import com.jizhang.app.ui.pet.PetAvatar
import com.jizhang.app.ui.pet.PetMood
import com.jizhang.app.ui.pet.PetState
import com.jizhang.app.ui.theme.AppColors
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class MineViewModel @Inject constructor(
    private val tokenStore: TokenStore,
    private val transactionRepository: TransactionRepository,
    private val petRepository: PetRepository,
    private val syncManager: SyncManager,
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val bookId: Long = 0L,
        val loggedIn: Boolean = false,
        val nickname: String = "",
        val pendingCount: Int = 0,
        val localCount: Int = 0,
        val pet: PetState = PetState(),
        val syncing: Boolean = false,
        val message: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    private var bound = -1L

    fun bind(bookId: Long, loggedIn: Boolean) {
        _state.update { it.copy(bookId = bookId, loggedIn = loggedIn) }
        if (bound == bookId) {
            return
        }
        bound = bookId
        viewModelScope.launch {
            launch {
                transactionRepository.observePendingCount(bookId).collect { count ->
                    _state.update { it.copy(pendingCount = count) }
                }
            }
            launch {
                petRepository.state.collect { pet -> _state.update { it.copy(pet = pet) } }
            }
            launch {
                tokenStore.nicknameFlow.collect { name -> _state.update { it.copy(nickname = name) } }
            }
            refresh()
        }
    }

    private suspend fun refresh() {
        val bookId = _state.value.bookId
        val count = transactionRepository.listLocal(bookId).size
        _state.update { it.copy(localCount = count) }
    }

    fun refreshLocal() {
        viewModelScope.launch { refresh() }
    }

    /** 同步全部账本（个人账本 + 所有家庭账本） */
    fun sync() {
        if (!_state.value.loggedIn) {
            _state.update { it.copy(message = "登录后即可同步到云端") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(syncing = true) }
            syncManager.syncAll()
                .onSuccess { summary ->
                    refresh()
                    val failed = if (summary.failedBooks.isEmpty()) {
                        ""
                    } else {
                        "；失败：${summary.failedBooks.joinToString("、")}"
                    }
                    _state.update {
                        it.copy(
                            syncing = false,
                            message = "同步完成：上传 ${summary.pushed} 条，" +
                                "删除 ${summary.deleted} 条，拉取 ${summary.pulled} 条$failed"
                        )
                    }
                }
                .onFailure { e ->
                    _state.update { it.copy(syncing = false, message = "同步失败：${e.message}") }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedIn = false) }
        }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}

@Composable
fun MineScreen(
    bookId: Long,
    loggedIn: Boolean,
    currentBookName: String?,
    currentBookShared: Boolean,
    onOpenSettings: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenBooks: () -> Unit,
    importDone: Int,
    onLoginChanged: (String?) -> Unit,
    onLoggedOut: () -> Unit,
    viewModel: MineViewModel = hiltViewModel()
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showLogin by remember { mutableStateOf(false) }
    var confirmLogout by remember { mutableStateOf(false) }

    LaunchedEffect(bookId, loggedIn) { viewModel.bind(bookId, loggedIn) }

    // 导入页由 MainScreen 以全屏覆盖层展示（弹窗窗口在本工程不可用），
    // 导入完成后用它通知这里刷新本地统计
    LaunchedEffect(importDone) { if (importDone > 0) viewModel.refreshLocal() }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            SectionBox {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PetAvatar(
                        type = state.pet.type,
                        mood = PetMood.HAPPY,
                        modifier = Modifier.size(72.dp),
                        bodyColor = AppColors.accent
                    )
                    Spacer(Modifier.size(14.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = state.pet.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(Modifier.height(2.dp))
                        Text(
                            text = "Lv.${state.pet.level} · 金币 ${state.pet.coins} · 连续 ${state.pet.streakDays} 天",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable(onClick = onOpenSettings)
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Palette,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(Modifier.size(4.dp))
                        Text("个性化", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }

        item {
            SectionBox {
                Text("账本", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                InfoRow("当前账本", currentBookName ?: "本地账本")
                Spacer(Modifier.height(8.dp))
                InfoRow(
                    "账本类型",
                    if (currentBookShared) "家庭账本 · 家人互相可见" else "个人账本 · 仅自己可见"
                )
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "家庭账本全体成员都能看到彼此的收支；私人开销请记在个人账本里。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenBooks,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.AutoMirrored.Filled.MenuBook, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("账本与成员管理")
                }
            }
        }

        item {
            SectionBox {
                Text("云同步", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))

                InfoRow("当前状态", if (state.loggedIn) "已登录 · ${state.nickname}" else "本地模式（未登录）")
                Spacer(Modifier.height(8.dp))
                InfoRow("账单总数", "${state.localCount} 笔")
                Spacer(Modifier.height(8.dp))
                InfoRow("待同步", "${state.pendingCount} 笔")

                Spacer(Modifier.height(14.dp))
                if (state.loggedIn) {
                    Button(
                        onClick = viewModel::sync,
                        enabled = !state.syncing,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text(if (state.syncing) "同步中…" else "立即同步")
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(
                        onClick = { confirmLogout = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("退出登录")
                    }
                } else {
                    Text(
                        text = "不登录也能正常记账；登录后可以把账单同步到云端，多台设备共享。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    Button(
                        onClick = { showLogin = true },
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.size(8.dp))
                        Text("登录并开启云同步")
                    }
                }
            }
        }

        item {
            SectionBox {
                Text("数据管理", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                Text(
                    text = "从其他记账 App 导出的 CSV 账单可以直接导入本账本，账户与分类会自动匹配或创建。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = onOpenImport,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.UploadFile, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.size(8.dp))
                    Text("导入账单")
                }
            }
        }

        item {
            SectionBox {
                Text("关于", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(10.dp))
                InfoRow("后端地址", BuildConfig.API_BASE_URL)
                Spacer(Modifier.height(8.dp))
                InfoRow("数据存储", "本地优先，登录后同步至云端")
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.Visibility,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.size(6.dp))
                    Text(
                        text = "所有金额均以「分」为单位存储，不会有浮点误差",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }

    if (showLogin) {
        LoginDialog(
            onDismiss = { showLogin = false },
            onLoggedIn = { summary ->
                showLogin = false
                val tip = if (summary.hasData) {
                    "已把本地 ${summary.transactions} 笔账单迁移到云端（新建账户 ${summary.createdAccounts} 个）"
                } else {
                    null
                }
                onLoginChanged(tip)
                viewModel.refreshLocal()
            }
        )
    }

    if (confirmLogout) {
        AlertDialog(
            onDismissRequest = { confirmLogout = false },
            title = { Text("退出登录") },
            text = { Text("退出后回到本地模式，已有的云端账单会保留在本地，重新登录即可继续同步。") },
            confirmButton = {
                TextButton(onClick = {
                    confirmLogout = false
                    viewModel.logout()
                    onLoggedOut()
                }) { Text("退出") }
            },
            dismissButton = {
                TextButton(onClick = { confirmLogout = false }) { Text("取消") }
            }
        )
    }

    state.message?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::consumeMessage,
            confirmButton = { TextButton(onClick = viewModel::consumeMessage) { Text("知道了") } },
            title = { Text("提示") },
            text = { Text(message) }
        )
    }
}

@Composable
private fun SectionBox(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        content()
    }
}

@Composable
private fun InfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}
