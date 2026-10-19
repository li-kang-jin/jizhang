package com.jizhang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.ui.theme.AppTheme
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import javax.inject.Singleton

private val Context.settingsDataStore by preferencesDataStore(name = "settings_prefs")

private val KEY_THEME = stringPreferencesKey("app_theme")
private val KEY_BUDGET_ALERT = booleanPreferencesKey("budget_alert_enabled")
private val KEY_HAPTIC = booleanPreferencesKey("haptic_enabled")
private val KEY_BILL_TEMPLATE = intPreferencesKey("bill_template")
private val KEY_HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
private val KEY_REPORT_GRANULARITY = stringPreferencesKey("report_granularity")
private val KEY_OFFLINE = booleanPreferencesKey("offline_mode")

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

    /**
     * 金额隐私模式：打开后账户管理、账户详情与统计页的资产金额显示成 `****`。
     * <p>默认关闭；开关只影响展示，不改任何数据。
     */
    val hideAmountsFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_HIDE_AMOUNTS] ?: false }

    suspend fun setHideAmounts(hidden: Boolean) {
        context.settingsDataStore.edit { it[KEY_HIDE_AMOUNTS] = hidden }
    }

    /** 账单页模板：1=清爽列表 2=信息卡片 3=紧凑单行 */
    val billTemplateFlow: Flow<Int> =
        context.settingsDataStore.data.map { it[KEY_BILL_TEMPLATE] ?: 1 }

    suspend fun setBillTemplate(value: Int) {
        context.settingsDataStore.edit { it[KEY_BILL_TEMPLATE] = value }
    }

    /* ---------------- 离线模式 ---------------- */

    /**
     * 离线模式：打开后 App 不发起任何网络请求。
     * <p>
     * 记账、统计、预算、导入导出本来就纯本地，所以开关打开后这些照常可用；
     * 真正的拦截点在 [com.jizhang.app.di.NetworkModule] 的 OkHttp 拦截器，这里只持久化开关状态。
     * 关闭时上层会补一次同步，把离线期间攒下的待同步账单推上去。
     */
    val offlineModeFlow: Flow<Boolean> =
        context.settingsDataStore.data.map { it[KEY_OFFLINE] ?: false }

    suspend fun offlineMode(): Boolean = offlineModeFlow.first()

    suspend fun setOfflineMode(enabled: Boolean) {
        context.settingsDataStore.edit { it[KEY_OFFLINE] = enabled }
    }

    /**
     * 供 OkHttp 拦截器同步读取。
     * 拦截器跑在 OkHttp 的工作线程上，不能挂起；[TokenStore.accessTokenBlocking] 已经是同样的写法。
     */
    fun offlineModeBlocking(): Boolean = runBlocking { offlineMode() }

    /**
     * 记账页上次用的账户 id，按账本分开存。
     * <p>
     * 账户列表按手工顺序排，第一项往往是「现金」这类没人用的账户；记账是高频动作，
     * 每笔都要点开选择器就太慢了。记住上次用的账户，进程重启 / 切回账本后仍然是它。
     * 账户被删除或归档时由记账页「列表里确实存在才采用」的校验兜住，这里不需要清理。
     */
    suspend fun lastAccountId(bookId: Long): Long? =
        context.settingsDataStore.data.first()[lastAccountKey(bookId)]

    suspend fun setLastAccount(bookId: Long, accountId: Long) {
        context.settingsDataStore.edit { it[lastAccountKey(bookId)] = accountId }
    }

    private fun lastAccountKey(bookId: Long) = longPreferencesKey("last_account_id_$bookId")

    /**
     * 统计页上次看的时间粒度（日 / 月 / 年）。
     * 记住它，用户上次按「年」看，下次进来就不用再切一遍。
     */
    suspend fun reportGranularity(): ReportGranularity {
        val key = context.settingsDataStore.data.first()[KEY_REPORT_GRANULARITY]
        return when (key) {
            null -> ReportGranularity.MONTH
            // 老版本存的是「日」：日已从筛选档位移除，按最接近的心智迁到「周」
            "DAY" -> ReportGranularity.WEEK
            else -> ReportGranularity.entries.firstOrNull { it.name == key } ?: ReportGranularity.MONTH
        }
    }

    suspend fun setReportGranularity(granularity: ReportGranularity) {
        context.settingsDataStore.edit { it[KEY_REPORT_GRANULARITY] = granularity.name }
    }
}
