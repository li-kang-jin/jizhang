package com.jizhang.app.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jizhang.app.ui.AppViewModel
import com.jizhang.app.ui.main.MainScreen
import com.jizhang.app.ui.pet.PetAvatar
import com.jizhang.app.ui.pet.PetMood
import com.jizhang.app.ui.pet.PetType

/**
 * 应用根组件。
 * <p>
 * 不再拦截登录 —— 打开即进入主界面，用本地账本记账；
 * 需要多端同步时再到「我的」页登录。
 */
@Composable
fun JizhangApp(appViewModel: AppViewModel) {
    val state by appViewModel.state.collectAsStateWithLifecycle()

    if (!state.ready) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                PetAvatar(
                    type = PetType.CAT,
                    mood = PetMood.HAPPY,
                    modifier = Modifier.size(140.dp)
                )
                CircularProgressIndicator()
                Text("小锦锦正在准备账本…")
            }
        }
        return
    }

    MainScreen(appViewModel = appViewModel)
}
