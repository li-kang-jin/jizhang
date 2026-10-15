package com.jizhang.app.data.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.time.LocalTime
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

/**
 * 预算提醒的调度。
 * <p>
 * 每 24 小时跑一次，首次尽量落在晚上 20:00，之后按周期自动顺延。
 */
object BudgetAlertScheduler {

    private const val WORK_NAME = "budget_alert_daily"
    private const val ALERT_HOUR = 20

    /** 根据开关状态同步调度：开启则注册，关闭则取消 */
    fun sync(context: Context, enabled: Boolean) {
        if (enabled) schedule(context) else cancel(context)
    }

    fun schedule(context: Context) {
        val request = PeriodicWorkRequestBuilder<BudgetAlertWorker>(1, TimeUnit.DAYS)
            .setInitialDelay(initialDelayMinutes(), TimeUnit.MINUTES)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            // 已存在就保留原计划，避免每次启动都重置首次执行时间
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    /** 距离下一个 [ALERT_HOUR] 点的分钟数 */
    private fun initialDelayMinutes(): Long {
        val now = LocalTime.now()
        val target = LocalTime.of(ALERT_HOUR, 0)
        val minutes = ChronoUnit.MINUTES.between(now, target)
        return if (minutes > 0) minutes else minutes + 24 * 60
    }
}
