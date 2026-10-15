package com.jizhang.app

import android.app.Application
import com.jizhang.app.data.notification.BudgetAlertScheduler
import dagger.hilt.android.HiltAndroidApp

@HiltAndroidApp
class JizhangApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        // 超支提醒默认开启，是否真正推送由设置开关与预算状态决定
        BudgetAlertScheduler.schedule(this)
    }
}
