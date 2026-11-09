package com.enenhhh.smartschedule.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.enenhhh.smartschedule.notifications.NotificationHelper

class WeatherReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (!com.enenhhh.smartschedule.legal.LegalConsent.isAccepted(context)) return
        // 广播的 onReceive 结束后进程可能被回收，用 goAsync 撑住这次网络请求
        val pending = goAsync()
        Thread {
            try {
                // 通知内容的构造和发送统一放在 NotificationHelper 里，避免两处不一致
                NotificationHelper.triggerWeatherNowBlocking(context)
                NotificationHelper.scheduleWeatherReminder(context)
            } catch (e: Exception) {
                android.util.Log.e("WeatherReceiver", "发送天气提醒失败", e)
            } finally {
                pending.finish()
            }
        }.start()
    }
}
