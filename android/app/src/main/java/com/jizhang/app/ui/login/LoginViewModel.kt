package com.jizhang.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.repository.AuthRepository
import com.jizhang.app.data.repository.MigrationSummary
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * 登录 / 注册。登录是**可选**的：只有需要多端同步时才用到。
 */
@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository
) : ViewModel() {

    data class UiState(
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    fun login(username: String, password: String, onSuccess: (MigrationSummary) -> Unit) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.login(username.trim(), password)
                .onSuccess { summary ->
                    _state.update { it.copy(loading = false) }
                    onSuccess(summary)
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "登录失败") }
                }
        }
    }

    fun register(username: String, password: String, nickname: String, onSuccess: () -> Unit) {
        if (username.isBlank() || password.length < 6) {
            _state.update { it.copy(error = "用户名不能为空，密码至少 6 位") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.register(username.trim(), password, nickname)
                .onSuccess {
                    _state.update { it.copy(loading = false) }
                    onSuccess()
                }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "注册失败") }
                }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
