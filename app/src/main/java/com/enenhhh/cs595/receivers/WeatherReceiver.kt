package com.enenhhh.smartschedule.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.R
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.weather.WeatherFetcher
import kotlin.concurrent.thread

class WeatherReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        thread {
            try {
                val content = WeatherFetcher.fetchTomorrowSummary(context)
                val notif = NotificationCompat.Builder(context, NotificationHelper.CHANNEL_WEATHER)
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setContentTitle("明天天气")
                    .setContentText(content)
                    .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                    .setAutoCancel(true)
                    .build()
                NotificationManagerCompat.from(context).notify(NotificationHelper.NOTIF_ID_WEATHER, notif)

                NotificationHelper.scheduleWeatherReminder(context)
            } catch (e: SecurityException) {
                android.util.Log.e("WeatherReceiver", "发送天气提醒失败", e)
            } catch (e: Exception) {
                android.util.Log.e("WeatherReceiver", "发送天气提醒失败", e)
            }
        }
    }
}
