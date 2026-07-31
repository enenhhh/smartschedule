package com.enenhhh.smartschedule.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.notifications.NotificationHelper

class TomorrowScheduleReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            val nm = NotificationManagerCompat.from(context)
            val notif = NotificationHelper.buildTomorrowPush(context).build()
            nm.notify(NotificationHelper.NOTIF_ID_TOMORROW_PUSH, notif)

            NotificationHelper.scheduleTomorrowReminder(context)
            NotificationHelper.updatePersistentNotification(context)
        } catch (e: SecurityException) {
            android.util.Log.e("TomorrowScheduleReceiver", "发送课表提醒失败", e)
        } catch (e: Exception) {
            android.util.Log.e("TomorrowScheduleReceiver", "发送课表提醒失败", e)
        }
    }
}
