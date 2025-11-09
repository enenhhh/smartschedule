package com.enenhhh.smartschedule.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.services.KeepAliveService
import com.enenhhh.smartschedule.util.Prefs

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (Prefs.isTomorrowReminderEnabled(context)) {
                NotificationHelper.scheduleTomorrowReminder(context)
            }
            if (Prefs.isWeatherReminderEnabled(context)) {
                NotificationHelper.scheduleWeatherReminder(context)
            }
            NotificationHelper.scheduleDailyPersistentUpdate(context)

            if (Prefs.isPersistentEnabled(context) || Prefs.isKeepAlive(context)) {
                KeepAliveService.start(context)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("BootReceiver", "开机启动失败，缺少精确闹钟权限", e)
        } catch (e: Exception) {
            android.util.Log.e("BootReceiver", "开机启动失败", e)
        }
    }
}
