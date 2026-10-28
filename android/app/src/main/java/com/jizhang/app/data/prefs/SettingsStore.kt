package com.jizhang.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.jizhang.app.data.repository.ReportGranularity
import com.jizhang.app.data.repository.WealthPlanner
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
private val KEY_BUDGET_SAVINGS_RATE = intPreferencesKey("budget_savings_rate")
private val KEY_HAPTIC = booleanPreferencesKey("haptic_enabled")
private val KEY_BILL_TEMPLATE = intPreferencesKey("bill_template")
private val KEY_HIDE_AMOUNTS = booleanPreferencesKey("hide_amounts")
private val KEY_REPORT_GRANULARITY = stringPreferencesKey("report_granularity")
private val KEY_OFFLINE = booleanPreferencesKey("offline_mode")
private val KEY_FI_WITHDRAWAL_RATE = floatPreferencesKey("fi_withdrawal_rate")
private val KEY_FI_REAL_RETURN = floatPreferencesKey("fi_real_return")
private val KEY_FI_TARGET_YEARS = intPreferencesKey("fi_target_years")
private val KEY_SUGGEST_SHOWN = intPreferencesKey("suggest_shown")
private val KEY_SUGGEST_ACCEPTED = intPreferencesKey("suggest_accepted")
private val KEY_SUGGEST_OVERRIDDEN = intPreferencesKey("suggest_overridden")

/** 备注 / 标签猜分类的统计事件；只累加计数，不记录备注与标签内容 */
enum class SuggestionEvent { SHOWN, ACCEPTED, OVERRIDDEN }

/**
 * 备注 / 标签猜分类的三个计数。
 * <p>
 * [acceptance] 是「点了提示的那个分类」占「做出过选择」的比例：
 * 提示了但用户什么都没点（照默认继续记）不计入分母，否则会把这套规则算得比实际更准。
 */
data class SuggestionTally(
    val shown: Int = 0,
    val accepted: Int = 0,
    val overridden: Int = 0
) {
    val answered: Int get() = accepted + overridden

    /** 还没人做过选择时为 null（不是 0，0 会被读成「一次都没猜对」） */
    val acceptance: Double? get() = if (answered == 0) null else accepted.toDouble() / answered
}

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

    /**
     * 预算建议用的目标储蓄率（百分比），默认 20%。
     * <p>
     * 它是「支出上限 = 收入 × (1 − 储蓄率)」里那个储蓄率：调高 = 预算更紧。
     * 只影响建议，不改变已经设好的预算额度。
     */
    suspend fun budgetSavingsRate(): Int =
        context.settingsDataStore.data.first()[KEY_BUDGET_SAVINGS_RATE] ?: 20

    suspend fun setBudgetSavingsRate(percent: Int) {
        context.settingsDataStore.edit { it[KEY_BUDGET_SAVINGS_RATE] = percent.coerceIn(0, 90) }
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

    /* ---------------- 财富规划假设 ---------------- */

    /**
     * 财富规划的三个假设：安全提取率、实际收益率、目标年数。
     * <p>
     * 它们只影响测算，不改任何账单，所以没有"保存"按钮——拖完就落盘；
     * 否则每次进来都被重置成默认值，用户会以为算法在乱跳。
     */
    suspend fun fiWithdrawalRate(): Double =
        context.settingsDataStore.data.first()[KEY_FI_WITHDRAWAL_RATE]?.toDouble()
            ?: WealthPlanner.DEFAULT_WITHDRAWAL_RATE

    suspend fun setFiWithdrawalRate(value: Double) {
        context.settingsDataStore.edit { it[KEY_FI_WITHDRAWAL_RATE] = value.toFloat() }
    }

    suspend fun fiRealReturn(): Double =
        context.settingsDataStore.data.first()[KEY_FI_REAL_RETURN]?.toDouble()
            ?: WealthPlanner.DEFAULT_REAL_RETURN

    suspend fun setFiRealReturn(value: Double) {
        context.settingsDataStore.edit { it[KEY_FI_REAL_RETURN] = value.toFloat() }
    }

    /** 目标年数：从今天算起多少年内想达标，用来算"达标概率" */
    suspend fun fiTargetYears(): Int =
        context.settingsDataStore.data.first()[KEY_FI_TARGET_YEARS] ?: DEFAULT_FI_TARGET_YEARS

    suspend fun setFiTargetYears(value: Int) {
        context.settingsDataStore.edit { it[KEY_FI_TARGET_YEARS] = value }
    }

    /* ---------------- 备注猜分类的统计 ---------------- */

    /**
     * 备注猜分类的试用统计：提示过多少次、其中多少被直接采纳、多少被改选。
     * <p>
     * 只累加计数，不记录任何备注原文 —— 它回答的是「这套规则准不准」，不是「用户买了什么」。
     * 全局累计而不按账本拆：单机自用，拆开只会让样本更碎、更看不出结论。
     * <p>
     * 需要它是因为「要不要换更重的模型」不能靠感觉决定：采纳率长期偏低才值得上模型，
     * 已经够高就说明规则版够用，再上模型纯属浪费电。
     */
    val suggestionTallyFlow: Flow<SuggestionTally> =
        context.settingsDataStore.data.map { prefs ->
            SuggestionTally(
                shown = prefs[KEY_SUGGEST_SHOWN] ?: 0,
                accepted = prefs[KEY_SUGGEST_ACCEPTED] ?: 0,
                overridden = prefs[KEY_SUGGEST_OVERRIDDEN] ?: 0
            )
        }

    suspend fun recordSuggestion(event: SuggestionEvent) {
        context.settingsDataStore.edit { prefs ->
            val key = when (event) {
                SuggestionEvent.SHOWN -> KEY_SUGGEST_SHOWN
                SuggestionEvent.ACCEPTED -> KEY_SUGGEST_ACCEPTED
                SuggestionEvent.OVERRIDDEN -> KEY_SUGGEST_OVERRIDDEN
            }
            prefs[key] = (prefs[key] ?: 0) + 1
        }
    }

    /** 清零：想看下一轮（比如换了分类体系之后）时不必卸载重装 */
    suspend fun resetSuggestionTally() {
        context.settingsDataStore.edit { prefs ->
            prefs.remove(KEY_SUGGEST_SHOWN)
            prefs.remove(KEY_SUGGEST_ACCEPTED)
            prefs.remove(KEY_SUGGEST_OVERRIDDEN)
        }
    }

    private companion object {
        const val DEFAULT_FI_TARGET_YEARS = 20
    }
}
