package com.jizhang.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.ui.AppViewModel
import com.jizhang.app.ui.navigation.JizhangApp
import com.jizhang.app.ui.theme.JizhangTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val appViewModel: AppViewModel = hiltViewModel()
            val state by appViewModel.state.collectAsStateWithLifecycle()

            JizhangTheme(theme = state.theme) {
                JizhangApp(appViewModel = appViewModel)
            }
        }
    }
}
