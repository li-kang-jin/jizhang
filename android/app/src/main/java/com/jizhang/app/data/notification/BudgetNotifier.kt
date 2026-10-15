package com.jizhang.app.data.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat

/**
 * 预算提醒的通知发送。
 * <p>
 * 渠道单独分开，用户可以在系统设置里只关掉预算提醒而不影响其他通知。
 */
object BudgetNotifier {

    private const val CHANNEL_ID = "budget_alert"
    private const val CHANNEL_NAME = "预算提醒"
    private const val NOTIFICATION_ID = 1001

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) {
            return
        }
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_DEFAULT
        ).apply {
            description = "预算快用完或已超支时提醒"
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * 发送预算提醒。
     * <p>
     * 未授予通知权限（Android 13+）时静默失败，不能因此让后台任务崩掉。
     */
    fun notify(context: Context, title: String, content: String) {
        ensureChannel(context)

        val manager = NotificationManagerCompat.from(context)
        if (!manager.areNotificationsEnabled()) {
            return
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_warning)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        runCatching { manager.notify(NOTIFICATION_ID, notification) }
    }
}
