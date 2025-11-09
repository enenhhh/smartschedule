package com.enenhhh.smartschedule.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.activity.result.ActivityResultLauncher
import androidx.core.app.NotificationChannelCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.FileProvider
import com.enenhhh.smartschedule.R
import com.enenhhh.smartschedule.data.ScheduleRepository
import com.enenhhh.smartschedule.receivers.TomorrowScheduleReceiver
import com.enenhhh.smartschedule.receivers.UpdatePersistentReceiver
import com.enenhhh.smartschedule.receivers.WeatherReceiver
import com.enenhhh.smartschedule.util.Prefs
import java.io.File
import java.util.Calendar
import kotlin.concurrent.thread

object NotificationHelper {

    const val CHANNEL_REMINDERS = "schedule_reminders"
    const val CHANNEL_PERSISTENT = "persistent"
    const val CHANNEL_WEATHER = "weather"
    const val CHANNEL_UPDATES = "updates"

    const val NOTIF_ID_TOMORROW_PUSH = 1001
    const val NOTIF_ID_PERSISTENT = 2001
    const val NOTIF_ID_WEATHER = 3001
    const val NOTIF_ID_UPDATE_READY = 4001

    fun createChannels(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)

        val reminders = NotificationChannelCompat.Builder(
            CHANNEL_REMINDERS,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName("课表提醒")
            .setDescription("明日课表推送")
            .build()

        val persistent = NotificationChannelCompat.Builder(
            CHANNEL_PERSISTENT,
            NotificationManagerCompat.IMPORTANCE_LOW
        ).setName("常驻通知")
            .setDescription("常驻明日课表或保活通知")
            .build()

        val weather = NotificationChannelCompat.Builder(
            CHANNEL_WEATHER,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName("天气提醒")
            .setDescription("明天天气推送")
            .build()

        val updates = NotificationChannelCompat.Builder(
            CHANNEL_UPDATES,
            NotificationManagerCompat.IMPORTANCE_DEFAULT
        ).setName("应用更新")
            .setDescription("下载完成与安装提示")
            .build()

        nm.createNotificationChannel(reminders)
        nm.createNotificationChannel(persistent)
        nm.createNotificationChannel(weather)
        nm.createNotificationChannel(updates)
    }

    fun maybeRequestPostNotifications(ctx: Context, launcher: ActivityResultLauncher<String>) {
        if (Build.VERSION.SDK_INT >= 33) {
            val nm = NotificationManagerCompat.from(ctx)
            if (!nm.areNotificationsEnabled()) {
                launcher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    fun scheduleTomorrowReminder(ctx: Context) {
        if (!Prefs.isTomorrowReminderEnabled(ctx)) return

        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, TomorrowScheduleReceiver::class.java).apply {
                action = "com.enenhhh.smartschedule.ACTION_TOMORROW_SCHEDULE"
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 100, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, Prefs.getReminderHour(ctx))
                set(Calendar.MINUTE, Prefs.getReminderMinute(ctx))
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            setExact(ctx, am, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "安排课表提醒失败，缺少精确闹钟权限", e)
            throw e
        }
    }

    fun cancelTomorrowReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, TomorrowScheduleReceiver::class.java).apply {
            action = "com.enenhhh.smartschedule.ACTION_TOMORROW_SCHEDULE"
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 100, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    fun buildTomorrowPush(ctx: Context): NotificationCompat.Builder {
        val repo = ScheduleRepository(ctx)
        val content = repo.buildTomorrowSummary()
        return NotificationCompat.Builder(ctx, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("明日课表")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
    }

    fun triggerScheduleNow(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        val notif = buildTomorrowPush(ctx).build()
        nm.notify(NOTIF_ID_TOMORROW_PUSH, notif)
        triggerPersistentUpdateNow(ctx)
    }

    fun scheduleWeatherReminder(ctx: Context) {
        if (!Prefs.isWeatherReminderEnabled(ctx)) return

        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, WeatherReceiver::class.java).apply {
                action = "com.enenhhh.smartschedule.ACTION_TOMORROW_WEATHER"
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 300, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, Prefs.getWeatherHour(ctx))
                set(Calendar.MINUTE, Prefs.getWeatherMinute(ctx))
                if (timeInMillis <= System.currentTimeMillis()) {
                    add(Calendar.DAY_OF_YEAR, 1)
                }
            }
            setExact(ctx, am, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "安排天气提醒失败，缺少精确闹钟权限", e)
            throw e
        }
    }

    fun cancelWeatherReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, WeatherReceiver::class.java).apply {
            action = "com.enenhhh.smartschedule.ACTION_TOMORROW_WEATHER"
        }
        val pi = PendingIntent.getBroadcast(
            ctx, 300, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        am.cancel(pi)
    }

    fun triggerWeatherNow(ctx: Context) {
        thread {
            val content = com.enenhhh.smartschedule.weather.WeatherFetcher.fetchTomorrowSummary(ctx)
            val builder = NotificationCompat.Builder(ctx, CHANNEL_WEATHER)
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setContentTitle("明天天气")
                .setContentText(content)
                .setStyle(NotificationCompat.BigTextStyle().bigText(content))
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            NotificationManagerCompat.from(ctx).notify(NOTIF_ID_WEATHER, builder.build())
        }
    }

    fun scheduleDailyPersistentUpdate(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, UpdatePersistentReceiver::class.java).apply {
                action = "com.enenhhh.smartschedule.ACTION_UPDATE_PERSISTENT"
            }
            val pi = PendingIntent.getBroadcast(
                ctx, 200, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 1)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            setExact(ctx, am, cal.timeInMillis, pi)
        } catch (e: SecurityException) {
            android.util.Log.w("NotificationHelper", "安排每日更新失败，缺少精确闹钟权限", e)
            throw e
        }
    }

    fun buildPersistentNotification(ctx: Context): NotificationCompat.Builder {
        val repo = ScheduleRepository(ctx)
        val persistentEnabled = Prefs.isPersistentEnabled(ctx)
        val content = if (persistentEnabled) {
            repo.buildTomorrowSummary()
        } else {
            "应用保持后台运行中"
        }
        return NotificationCompat.Builder(ctx, CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(if (persistentEnabled) "常驻明日课表" else "后台保活")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    fun triggerPersistentUpdateNow(ctx: Context) {
        val nm = NotificationManagerCompat.from(ctx)
        val notif = buildPersistentNotification(ctx).build()
        nm.notify(NOTIF_ID_PERSISTENT, notif)
    }

    // 下载完成后，发送“点击安装”通知
    fun notifyApkDownloaded(ctx: Context, file: File, version: String) {
        createChannels(ctx) // 确保通道存在
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(
            ctx, 401, installIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = "更新下载完成"
        val content = "${file.name} 下载完成，点击安装（版本 ${version}）"
        val builder = NotificationCompat.Builder(ctx, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(pi)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID_UPDATE_READY, builder.build())
    }

    private fun setExact(ctx: Context, am: AlarmManager, triggerAt: Long, pi: PendingIntent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                    android.util.Log.w("NotificationHelper", "精确闹钟权限未授予，使用非精确闹钟")
                }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            android.util.Log.e("NotificationHelper", "设置精确闹钟失败", e)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                } else {
                    am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                }
            } catch (e2: Exception) {
                android.util.Log.e("NotificationHelper", "设置非精确闹钟也失败", e2)
                throw e
            }
            throw e
        }
    }
}