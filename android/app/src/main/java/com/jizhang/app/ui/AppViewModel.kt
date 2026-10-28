package com.jizhang.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.local.entity.LocalBook
import com.jizhang.app.data.notification.BudgetAlertScheduler
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.SuggestionTally
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
        /** 记账数字键盘的震动反馈开关 */
        val hapticEnabled: Boolean = true,
        /** 账单页模板：1 清爽列表 / 2 信息卡片 / 3 紧凑单行 */
        val billTemplate: Int = 1,
        /** 金额隐私模式：资产金额显示成 ****（只影响展示） */
        val hideAmounts: Boolean = false,
        /** 离线模式：打开后 App 不发起任何网络请求 */
        val offlineMode: Boolean = false,
        /** 备注猜分类的累计统计：设置页用它说明「猜得准不准」 */
        val suggestionTally: SuggestionTally = SuggestionTally(),
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

        viewModelScope.launch {
            settingsStore.hapticFlow.collect { enabled ->
                _state.update { it.copy(hapticEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            settingsStore.billTemplateFlow.collect { value ->
                _state.update { it.copy(billTemplate = value) }
            }
        }

        viewModelScope.launch {
            settingsStore.hideAmountsFlow.collect { hidden ->
                _state.update { it.copy(hideAmounts = hidden) }
            }
        }

        viewModelScope.launch {
            settingsStore.offlineModeFlow.collect { offline ->
                _state.update { it.copy(offlineMode = offline) }
            }
        }

        viewModelScope.launch {
            settingsStore.suggestionTallyFlow.collect { tally ->
                _state.update { it.copy(suggestionTally = tally) }
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
            // 离线模式：启动时一次网络请求都不发，账本列表直接用本地缓存（由 observeBooks 下发）
            val offline = settingsStore.offlineMode()

            if (loggedIn) {
                if (!offline) {
                    val books = bookRepository.refreshBooks().getOrDefault(emptyList())
                    // 老版本升级上来时当前账本还是 0，落到个人账本
                    if (tokenStore.currentBookId() == JizhangDatabase.LOCAL_BOOK_ID) {
                        val fallback = books.firstOrNull { it.personal } ?: books.firstOrNull()
                        if (fallback != null) {
                            bookRepository.switchBook(fallback.id)
                        }
                    }
                }
            } else {
                bookRepository.ensureLocalBookRow()
                // 未登录：保证本地账本有默认账户与分类，避免「没有账户可选」
                localDataInitializer.ensureLocalData(JizhangDatabase.LOCAL_BOOK_ID)
            }

            _state.update { it.copy(ready = true, theme = theme, loggedIn = loggedIn, offlineMode = offline) }

            // 进入应用后台静默同步全部账本（离线模式下跳过）
            if (loggedIn && !offline) {
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

    fun setHapticEnabled(enabled: Boolean) {
        viewModelScope.launch { settingsStore.setHaptic(enabled) }
    }

    /** 金额隐私模式开关 */
    fun setHideAmounts(hidden: Boolean) {
        viewModelScope.launch { settingsStore.setHideAmounts(hidden) }
    }

    /** 从账户管理页右上角的眼睛图标切换（等价于设置里的开关） */
    fun toggleHideAmounts() {
        setHideAmounts(!_state.value.hideAmounts)
    }

    fun setBillTemplate(value: Int) {
        viewModelScope.launch { settingsStore.setBillTemplate(value) }
    }

    /** 清零「备注猜分类」的统计：换了一套分类之后，旧样本不该继续算在准确率里 */
    fun resetSuggestionTally() {
        viewModelScope.launch { settingsStore.resetSuggestionTally() }
    }

    /**
     * 离线模式开关。
     * <p>
     * 打开后所有网络请求在 OkHttp 拦截器里直接失败，同步 / 登录入口随之停用，数据留在本地；
     * 关闭时补一次静默同步，把离线期间攒下的待同步账单推上去，不用用户再点一次「立即同步」。
     */
    fun setOfflineMode(enabled: Boolean) {
        viewModelScope.launch {
            settingsStore.setOfflineMode(enabled)
            _state.update { it.copy(offlineMode = enabled) }
            if (!enabled) {
                syncAll(silent = true)
            }
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
            if (_state.value.offlineMode) {
                if (!silent) {
                    _state.update { it.copy(message = "离线模式已开启，已跳过同步") }
                }
                return@launch
            }
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
                        // 冲突是「服务端版本更新，按服务端为准」：不提示的话用户只会看到
                        // 待同步条数不动、上传 0 条，误以为同步没生效
                        val conflict = if (summary.conflicts > 0) {
                            "，${summary.conflicts} 条冲突已采用服务端版本"
                        } else {
                            ""
                        }
                        _state.update {
                            it.copy(
                                message = "同步完成：上传 ${summary.pushed} 条，" +
                                    "删除 ${summary.deleted} 条，拉取 ${summary.pulled} 条" +
                                    "$conflict$failed"
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
