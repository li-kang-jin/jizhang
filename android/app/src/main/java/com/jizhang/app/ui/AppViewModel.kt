package com.jizhang.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.notification.BudgetAlertScheduler
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.BookRepository
import com.jizhang.app.data.repository.LocalDataInitializer
import com.jizhang.app.data.repository.SyncManager
import com.jizhang.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全局应用状态：主题、登录态、账本列表与当前账本。
 * <p>
 * 「当前账本」是全 App 的单一数据源：账单 / 统计 / 预算都跟随它变化，
 * 因此多账本只需要在这里切换一个 id，无需各页面互相传参。
 * <p>
 * 未登录时只有本地账本（[JizhangDatabase.LOCAL_BOOK_ID]），打开即可记账。
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val localDataInitializer: LocalDataInitializer,
    private val bookRepository: BookRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    data class UiState(
        val ready: Boolean = false,
        val theme: AppTheme = AppTheme.MINT,
        val loggedIn: Boolean = false,
        val bookId: Long = JizhangDatabase.LOCAL_BOOK_ID,
        /** 我可访问的全部账本（本地账本 + 服务端账本） */
        val books: List<LocalBook> = emptyList(),
        /** 预算超支提醒开关 */
        val budgetAlertEnabled: Boolean = true,
        /** 登录后是否需要提示用户已迁移数据 */
        val loginTip: String? = null,
        /** 一次性提示（切账本、同步结果等） */
        val message: String? = null
    ) {
        val currentBook: LocalBook? get() = books.firstOrNull { it.id == bookId }

        val hasFamilyBook: Boolean get() = books.any { it.remote && it.shared }
    }

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsStore.themeFlow.collect { theme ->
                _state.update { it.copy(theme = theme) }
            }
        }

        viewModelScope.launch {
            settingsStore.budgetAlertFlow.collect { enabled ->
                _state.update { it.copy(budgetAlertEnabled = enabled) }
            }
        }

        // 账本列表：切换器与「我的」页共用
        viewModelScope.launch {
            bookRepository.observeBooks().collect { books ->
                _state.update { it.copy(books = books) }
            }
        }

        // 当前账本以 DataStore 为唯一数据源
        viewModelScope.launch {
            tokenStore.currentBookIdFlow.collect { bookId ->
                _state.update { it.copy(bookId = bookId) }
            }
        }

        viewModelScope.launch {
            val theme = settingsStore.theme()
            val loggedIn = !tokenStore.accessToken().isNullOrBlank()

            if (loggedIn) {
                val books = bookRepository.refreshBooks().getOrDefault(emptyList())
                // 老版本升级上来时当前账本还是 0，落到个人账本
                if (tokenStore.currentBookId() == JizhangDatabase.LOCAL_BOOK_ID) {
                    val fallback = books.firstOrNull { it.personal } ?: books.firstOrNull()
                    if (fallback != null) {
                        bookRepository.switchBook(fallback.id)
                    }
                }
            } else {
                bookRepository.ensureLocalBookRow()
                // 未登录：保证本地账本有默认账户与分类，避免「没有账户可选」
                localDataInitializer.ensureLocalData(JizhangDatabase.LOCAL_BOOK_ID)
            }

            _state.update { it.copy(ready = true, theme = theme, loggedIn = loggedIn) }

            // 进入应用后台静默同步全部账本
            if (loggedIn) {
                launch { syncAll(silent = true) }
            }
        }

        viewModelScope.launch {
            tokenStore.isLoggedIn.collect { loggedIn ->
                _state.update { it.copy(loggedIn = loggedIn) }
            }
        }
    }

    fun setTheme(theme: AppTheme) {
        viewModelScope.launch { settingsStore.setTheme(theme) }
    }

    /** 切换预算超支提醒，并同步后台任务的调度状态 */
    fun setBudgetAlert(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setBudgetAlert(enabled)
            BudgetAlertScheduler.sync(context, enabled)
        }
    }

    /** 登录成功后调用：重新拉账本并做一次全量同步 */
    fun onLoggedIn(tip: String?) {
        viewModelScope.launch {
            _state.update { it.copy(loggedIn = true, loginTip = tip) }
            val books = bookRepository.refreshBooks().getOrDefault(emptyList())
            if (tokenStore.currentBookId() == JizhangDatabase.LOCAL_BOOK_ID) {
                val fallback = books.firstOrNull { it.personal } ?: books.firstOrNull()
                if (fallback != null) {
                    bookRepository.switchBook(fallback.id)
                }
            }
            syncAll(silent = true)
        }
    }

    /** 退出登录后调用：回到本地账本 */
    fun onLoggedOut() {
        viewModelScope.launch {
            bookRepository.onLogout()
            localDataInitializer.ensureLocalData(JizhangDatabase.LOCAL_BOOK_ID)
            _state.update { it.copy(loggedIn = false, message = "已退出登录，本地账单仍保留") }
        }
    }

    /** 切换当前账本 */
    fun switchBook(bookId: Long) {
        viewModelScope.launch {
            if (bookId == _state.value.bookId) {
                return@launch
            }
            bookRepository.switchBook(bookId)
            val name = _state.value.books.firstOrNull { it.id == bookId }?.name ?: "账本"
            _state.update { it.copy(message = "已切换到「$name」") }
        }
    }

    /** 重新拉取服务端账本列表 */
    fun refreshBooks() {
        viewModelScope.launch {
            bookRepository.refreshBooks()
                .onFailure { e -> _state.update { it.copy(message = "账本刷新失败：${e.message}") } }
        }
    }

    /** 同步全部账本 */
    fun syncAll(silent: Boolean = false) {
        viewModelScope.launch {
            if (!_state.value.loggedIn) {
                if (!silent) {
                    _state.update { it.copy(message = "登录后即可同步到云端") }
                }
                return@launch
            }
            syncManager.syncAll()
                .onSuccess { summary ->
                    if (!silent) {
                        val failed = if (summary.failedBooks.isEmpty()) {
                            ""
                        } else {
                            "；失败：${summary.failedBooks.joinToString("、")}"
                        }
                        _state.update {
                            it.copy(
                                message = "同步完成：上传 ${summary.pushed} 条，" +
                                    "删除 ${summary.deleted} 条，拉取 ${summary.pulled} 条$failed"
                            )
                        }
                    }
                }
                .onFailure { e ->
                    if (!silent) {
                        _state.update { it.copy(message = "同步失败：${e.message}") }
                    }
                }
        }
    }

    fun showMessage(text: String) {
        _state.update { it.copy(message = text) }
    }

    fun consumeTip() {
        _state.update { it.copy(loginTip = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }
}
