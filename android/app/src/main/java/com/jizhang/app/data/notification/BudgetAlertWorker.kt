package com.jizhang.app.data.notification

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.jizhang.app.data.local.JizhangDatabase
import com.jizhang.app.data.prefs.SettingsStore
import com.jizhang.app.data.prefs.TokenStore
import com.jizhang.app.data.repository.BudgetCycle
import com.jizhang.app.data.repository.BudgetPeriods
import com.jizhang.app.data.repository.BudgetRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.first
import java.math.BigDecimal
import java.math.RoundingMode
import kotlin.math.roundToInt

/**
 * 预算超支检查（每天一次）。
 * <p>
 * 只在「本期已设预算」且「接近上限 / 已超支」时发通知，避免打扰。
 * 依赖用 [EntryPoint] 获取，这样不必改造 Application 的 WorkManager 配置。
 */
class BudgetAlertWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val entry = EntryPointAccessors.fromApplication(
            applicationContext,
            BudgetAlertEntryPoint::class.java
        )

        // 开关以设置里的最新值为准（用户可能刚关掉）
        if (!entry.settingsStore().budgetAlertEnabled()) {
            return Result.success()
        }

        val bookId = resolveBookId(entry.tokenStore())
        val period = BudgetPeriods.of(BudgetCycle.MONTH)
        val overview = entry.budgetRepository().overview(bookId, period)

        if (!overview.hasBudget) {
            return Result.success()
        }

        val used = overview.totalUsed
        val total = overview.totalBudget
        val ratio = if (total > 0) used.toDouble() / total else 0.0

        when {
            overview.exceeded -> BudgetNotifier.notify(
                applicationContext,
                "本期预算已超支",
                "已用 ${yuan(used)} 元，超出 ${yuan(used - total)} 元（${BudgetPeriods.label(period)}）。"
            )

            ratio >= ALERT_RATIO -> BudgetNotifier.notify(
                applicationContext,
                "预算快用完了",
                "已用 ${yuan(used)} / ${yuan(total)} 元（${(ratio * 100).roundToInt()}%），" +
                    "剩余 ${yuan(overview.remaining)} 元、${overview.daysLeft} 天。"
            )
        }

        return Result.success()
    }

    private suspend fun resolveBookId(tokenStore: TokenStore): Long {
        val loggedIn = !tokenStore.accessToken().isNullOrBlank()
        if (!loggedIn) {
            return JizhangDatabase.LOCAL_BOOK_ID
        }
        return tokenStore.currentBookIdFlow.first().takeIf { it > 0 } ?: JizhangDatabase.LOCAL_BOOK_ID
    }

    /** 分 → 元，固定两位小数 */
    private fun yuan(fen: Long): String =
        BigDecimal(fen).movePointLeft(2).setScale(2, RoundingMode.HALF_UP).toPlainString()

    private companion object {
        /** 用到 80% 就开始提醒 */
        const val ALERT_RATIO = 0.8
    }
}

@EntryPoint
@InstallIn(SingletonComponent::class)
interface BudgetAlertEntryPoint {
    fun budgetRepository(): BudgetRepository
    fun settingsStore(): SettingsStore
    fun tokenStore(): TokenStore
}
