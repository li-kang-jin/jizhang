package com.jizhang.app.ui.settings

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.jizhang.app.data.repository.PetRepository
import com.jizhang.app.ui.AppViewModel
import com.jizhang.app.ui.common.AppFullScreenDialog
import com.jizhang.app.ui.common.safeDialogContent
import com.jizhang.app.ui.pet.PetAvatar
import com.jizhang.app.ui.pet.PetMood
import com.jizhang.app.ui.pet.PetState
import com.jizhang.app.ui.pet.PetType
import com.jizhang.app.ui.theme.AppColors
import com.jizhang.app.ui.theme.AppTheme
import com.jizhang.app.ui.theme.paletteOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PetSettingsViewModel @Inject constructor(
    private val petRepository: PetRepository
) : ViewModel() {

    private val _pet = MutableStateFlow(PetState())
    val pet: StateFlow<PetState> = _pet.asStateFlow()

    init {
        viewModelScope.launch {
            petRepository.state.collect { _pet.value = it }
        }
    }

    fun rename(name: String) {
        viewModelScope.launch { petRepository.rename(name) }
    }

    fun changeType(type: PetType) {
        viewModelScope.launch { petRepository.changeType(type) }
    }
}

/**
 * 设置：主题切换 + 宠物定制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsDialog(
    onDismiss: () -> Unit,
    appViewModel: AppViewModel = hiltViewModel(),
    petViewModel: PetSettingsViewModel = hiltViewModel()
) {
    val appState by appViewModel.state.collectAsStateWithLifecycle()
    val pet by petViewModel.pet.collectAsStateWithLifecycle()
    var nameInput by remember { mutableStateOf(pet.name) }

    val context = LocalContext.current
    // 用户同意通知权限后才真正打开开关，避免「开了却收不到提醒」
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted -> appViewModel.setBudgetAlert(granted) }

    LaunchedEffect(pet.name) { nameInput = pet.name }

    AppFullScreenDialog(onDismissRequest = onDismiss) {
        // safeDialogContent：底部让出系统手势区；顶部由 TopAppBar 自己处理
        Column(modifier = Modifier.fillMaxSize().safeDialogContent()) {
                TopAppBar(
                    title = { Text("个性化", fontWeight = FontWeight.Bold) },
                    navigationIcon = {
                        IconButton(onClick = onDismiss) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                        }
                    }
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        // 全屏弹窗窗口不会因 IME 上推，靠 imePadding 把输入框顶起来
                        .imePadding()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    SectionCard("主题配色") {
                        ThemePicker(
                            current = appState.theme,
                            onSelect = appViewModel::setTheme
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    SectionCard("我的宠物") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PetAvatar(
                                type = pet.type,
                                mood = PetMood.HAPPY,
                                modifier = Modifier.size(72.dp),
                                bodyColor = AppColors.accent
                            )
                            Spacer(Modifier.size(14.dp))
                            Column {
                                Text(
                                    "${pet.name} · Lv.${pet.level}",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "金币 ${pet.coins} · 连续记账 ${pet.streakDays} 天",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        Text("形象", style = MaterialTheme.typography.labelMedium)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            PetType.entries.forEach { type ->
                                PetTypeItem(
                                    type = type,
                                    selected = pet.type == type,
                                    accent = AppColors.accent,
                                    onClick = { petViewModel.changeType(type) }
                                )
                            }
                        }

                        Spacer(Modifier.height(16.dp))
                        OutlinedTextField(
                            value = nameInput,
                            onValueChange = { if (it.length <= 8) nameInput = it },
                            label = { Text("宠物名字（最多 8 个字）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { petViewModel.rename(nameInput) },
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存名字")
                        }
                    }

                    Spacer(Modifier.height(14.dp))

                    SectionCard("玩法") {
                        Text(
                            text = "· 每记一笔账，${pet.name} 获得 ${PetState.COINS_PER_RECORD} 金币\n" +
                                "· 每 ${PetState.COINS_PER_LEVEL} 金币升一级\n" +
                                "· 每天记账可累计连续天数\n" +
                                "· 本月超支时它会露出担心的表情",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    SectionCard("预算提醒") {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("超支提醒", style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "本期预算用到 80% 或已超支时，每天提醒一次",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(
                                checked = appState.budgetAlertEnabled,
                                onCheckedChange = { checked ->
                                    if (checked && needsNotificationPermission(context)) {
                                        permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                    } else {
                                        appViewModel.setBudgetAlert(checked)
                                    }
                                }
                            )
                        }
                    }
                }
        }
    }
}

/** Android 13 起通知需要运行时授权 */
private fun needsNotificationPermission(context: Context): Boolean =
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) !=
        PackageManager.PERMISSION_GRANTED

@Composable
private fun SectionCard(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    ) {
        Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        content()
    }
}

@Composable
private fun ThemePicker(current: AppTheme, onSelect: (AppTheme) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        AppTheme.entries.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { theme ->
                    val palette = paletteOf(theme)
                    val selected = current == theme
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(14.dp))
                            .background(palette.surfaceVariant)
                            .border(
                                width = if (selected) 2.dp else 1.dp,
                                color = if (selected) palette.primary else Color.Transparent,
                                shape = RoundedCornerShape(14.dp)
                            )
                            .clickable { onSelect(theme) }
                            .padding(horizontal = 12.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(26.dp)
                                .clip(CircleShape)
                                .background(palette.primary)
                        )
                        Spacer(Modifier.size(10.dp))
                        Text(
                            text = "${theme.emoji} ${theme.label}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
                            color = palette.onSurface
                        )
                        Spacer(Modifier.weight(1f))
                        if (selected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = palette.primary)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PetTypeItem(
    type: PetType,
    selected: Boolean,
    accent: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(14.dp))
            .background(
                if (selected) accent.copy(alpha = 0.18f) else MaterialTheme.colorScheme.surfaceVariant
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 8.dp)
    ) {
        PetAvatar(
            type = type,
            mood = PetMood.HAPPY,
            modifier = Modifier.size(48.dp),
            bodyColor = accent,
            animated = false
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = type.label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
