package com.enenhhh.smartschedule.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.services.KeepAliveService
import com.enenhhh.smartschedule.util.Prefs

class UpdatePersistentReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        try {
            if (Prefs.isPersistentEnabled(context) || Prefs.isKeepAlive(context)) {
                KeepAliveService.requestUpdate(context)
            }
            NotificationHelper.scheduleDailyPersistentUpdate(context)
        } catch (e: SecurityException) {
            android.util.Log.e("UpdatePersistentReceiver", "更新常驻通知失败", e)
        } catch (e: Exception) {
            android.util.Log.e("UpdatePersistentReceiver", "更新常驻通知失败", e)
        }
    }
}
