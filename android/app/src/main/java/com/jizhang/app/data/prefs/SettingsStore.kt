package com.jizhang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jizhang.app.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

private val KEY_THEME = stringPreferencesKey("app_theme")
private val KEY_BUDGET_ALERT = booleanPreferencesKey("budget_alert_enabled")
private val KEY_HAPTIC = booleanPreferencesKey("haptic_enabled")
private val KEY_RECORD_TEMPLATE = intPreferencesKey("record_template")
private val KEY_BILL_TEMPLATE = intPreferencesKey("bill_template")

/**
 * 应用外观设置。
 */
@Singleton
class SettingsStore @Inject constructor(
    @ApplicationContext private val context: Context
) {

    val themeFlow: Flow<AppTheme> =
        context.settingsDataStore.data.map { AppTheme.fromKey(it[KEY_THEME]) }

    suspend fun theme(): AppTheme = themeFlow.first()

    suspend fun setTheme(theme: AppTheme) {
        context.settingsDataStore.edit { it[KEY_THEME] = theme.key }
    }

    /* ---------------- 预算提醒 ---------------- */

    /** 超支提醒开关，默认开启 */
    val budgetAlertFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_BUDGET_ALERT] ?: true }

    suspend fun budgetAlertEnabled(): Boolean = budgetAlertFlow.first()

    suspend fun setBudgetAlert(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_BUDGET_ALERT] = enabled }
    }

    /* ---------------- 记账与账单 ---------------- */

    /** 记账键盘震动反馈，默认开启 */
    val hapticFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_HAPTIC] ?: true }

    suspend fun setHaptic(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_HAPTIC] = enabled }
    }

    /** 记账页模板：1=经典 2=极简速记 3=详情备注 */
    val recordTemplateFlow: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_RECORD_TEMPLATE] ?: 1 }

    suspend fun setRecordTemplate(value: Int) {
        context.settingsDataStore.edit { it[KEY_RECORD_TEMPLATE] = value }
    }

    /** 账单页模板：1=清爽列表 2=信息卡片 3=紧凑单行 */
    val billTemplateFlow: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_BILL_TEMPLATE] ?: 1 }

    suspend fun setBillTemplate(value: Int) {
        context.settingsDataStore.edit { it[KEY_BILL_TEMPLATE] = value }
    }
}
