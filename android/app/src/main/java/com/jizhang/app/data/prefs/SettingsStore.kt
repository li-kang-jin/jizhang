package com.jizhang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
}
