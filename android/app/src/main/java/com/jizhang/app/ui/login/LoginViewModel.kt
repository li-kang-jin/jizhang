package com.jizhang.app.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.AuthRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val authRepository: AuthRepository,
    private val tokenStore: TokenStore
) : ViewModel() {

    data class UiState(
        /** 正在检查本地登录态 */
        val checking: Boolean = true,
        val loggedIn: Boolean = false,
        val loading: Boolean = false,
        val error: String? = null
    )

    private val _state = MutableStateFlow(UiState())
    val state: StateFlow<UiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val token = tokenStore.accessToken()
            _state.update { it.copy(checking = false, loggedIn = !token.isNullOrBlank()) }
        }
    }

    fun login(username: String, password: String) {
        if (username.isBlank() || password.isBlank()) {
            _state.update { it.copy(error = "请输入用户名和密码") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.login(username.trim(), password)
                .onSuccess { _state.update { it.copy(loading = false, loggedIn = true) } }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "登录失败") }
                }
        }
    }

    fun register(username: String, password: String, nickname: String) {
        if (username.isBlank() || password.length < 6) {
            _state.update { it.copy(error = "用户名不能为空，密码至少 6 位") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            authRepository.register(username.trim(), password, nickname)
                .onSuccess { login(username, password) }
                .onFailure { e ->
                    _state.update { it.copy(loading = false, error = e.message ?: "注册失败") }
                }
        }
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            _state.update { it.copy(loggedIn = false) }
        }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}
