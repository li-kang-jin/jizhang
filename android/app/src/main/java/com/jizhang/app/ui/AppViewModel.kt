package com.jizhang.app.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.notification.BudgetAlertScheduler
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.LocalDataInitializer
import com.jizhang.app.ui.theme.AppTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 全局应用状态：主题、登录态、当前账本。
 * <p>
 * 未登录时使用本地账本（[JizhangDatabase.LOCAL_BOOK_ID]），打开即可记账。
 */
@HiltViewModel
class AppViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    private val settingsStore: SettingsStore,
    private val tokenStore: TokenStore,
    private val localDataInitializer: LocalDataInitializer
) : ViewModel() {

    data class UiState(
        val ready: Boolean = false,
        val theme: AppTheme = AppTheme.MINT,
        val loggedIn: Boolean = false,
        val bookId: Long = JizhangDatabase.LOCAL_BOOK_ID,
        /** 预算超支提醒开关 */
        val budgetAlertEnabled: Boolean = true,
        /** 登录后是否需要提示用户已迁移数据 */
        val loginTip: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val theme = settingsStore.theme()
            val loggedIn = !tokenStore.accessToken().isNullOrBlank()
            val bookId = resolveBookId(loggedIn)

            // 未登录：保证本地账本有默认账户与分类，避免「没有账户可选」
            if (!loggedIn) {
                localDataInitializer.ensureLocalData(JizhangDatabase.LOCAL_BOOK_ID)
            }

            _state.update {
                it.copy(ready = true, theme = theme, loggedIn = loggedIn, bookId = bookId)
            }

            // 跟随设置变化
            settingsStore.themeFlow.collect { current ->
                _state.update { it.copy(theme = current) }
            }
        }

        viewModelScope.launch {
            settingsStore.budgetAlertFlow.collect { enabled ->
                _state.update { it.copy(budgetAlertEnabled = enabled) }
            }
        }

        viewModelScope.launch {
            tokenStore.isLoggedIn.collect { loggedIn ->
                if (loggedIn) {
                    _state.update { it.copy(loggedIn = true, bookId = resolveBookId(true)) }
                } else {
                    _state.update {
                        it.copy(loggedIn = false, bookId = JizhangDatabase.LOCAL_BOOK_ID)
                    }
                }
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

    /** 登录成功后调用：切换到服务端账本 */
    fun onLoggedIn(tip: String?) {
        viewModelScope.launch {
            _state.update {
                it.copy(
                    loggedIn = true,
                    bookId = resolveBookId(true),
                    loginTip = tip
                )
            }
        }
    }

    fun consumeTip() {
        _state.update { it.copy(loginTip = null) }
    }

    private suspend fun resolveBookId(loggedIn: Boolean): Long =
        if (loggedIn) {
            tokenStore.bookIdFlow.first().takeIf { it > 0 } ?: JizhangDatabase.LOCAL_BOOK_ID
        } else {
            JizhangDatabase.LOCAL_BOOK_ID
        }
}
