package com.jizhang.app.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.ui.login.LoginScreen
import com.jizhang.app.ui.login.LoginViewModel
import com.jizhang.app.ui.main.MainScreen

/**
 * 应用根组件：根据登录态在登录页与主页面之间切换。
 */
@Composable
fun JizhangApp(
    loginViewModel: LoginViewModel = hiltViewModel()
) {
    val state by loginViewModel.state.collectAsStateWithLifecycle()

    when {
        state.checking -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }

        state.loggedIn -> MainScreen(onLogout = loginViewModel::logout)

        else -> LoginScreen(
            state = state,
            onLogin = loginViewModel::login,
            onRegister = loginViewModel::register,
            onConsumeError = loginViewModel::consumeError
        )
    }
}
