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
import com.enenhhh.smartschedule.receivers.TomorrowScheduleReceiver
import com.enenhhh.smartschedule.receivers.UpdatePersistentReceiver
import com.enenhhh.smartschedule.receivers.WeatherReceiver
import com.enenhhh.smartschedule.util.Prefs
import java.io.File
import java.util.Calendar
import kotlin.concurrent.thread
import android.provider.Settings
import android.net.Uri
import com.enenhhh.smartschedule.data.ScheduleManager

object NotificationHelper {

    fun requestIgnoreBatteryOptimizations(ctx: Context) {
        try {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${ctx.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: Exception) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
            } catch (_: Exception) {}
        }
    }

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
        val reminders = NotificationChannelCompat.Builder(CHANNEL_REMINDERS, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("课表提醒").build()
        val persistent = NotificationChannelCompat.Builder(CHANNEL_PERSISTENT, NotificationManagerCompat.IMPORTANCE_LOW).setName("常驻通知").build()
        val weather = NotificationChannelCompat.Builder(CHANNEL_WEATHER, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("天气提醒").build()
        val updates = NotificationChannelCompat.Builder(CHANNEL_UPDATES, NotificationManagerCompat.IMPORTANCE_DEFAULT).setName("应用更新").build()

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
            val intent = Intent(ctx, TomorrowScheduleReceiver::class.java).apply { action = "com.enenhhh.smartschedule.ACTION_TOMORROW_SCHEDULE" }
            val pi = PendingIntent.getBroadcast(ctx, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, Prefs.getReminderHour(ctx))
                set(Calendar.MINUTE, Prefs.getReminderMinute(ctx))
                if (timeInMillis <= System.currentTimeMillis()) { add(Calendar.DAY_OF_YEAR, 1) }
            }
            setExact(ctx, am, cal.timeInMillis, pi)
            android.util.Log.d("NotificationHelper", "Tomorrow reminder scheduled for ${cal.time}")
        } catch (e: SecurityException) { }
    }

    fun cancelTomorrowReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, TomorrowScheduleReceiver::class.java).apply { action = "com.enenhhh.smartschedule.ACTION_TOMORROW_SCHEDULE" }
        val pi = PendingIntent.getBroadcast(ctx, 100, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }

    /** 点击通知回到应用首页 */
    private fun openAppIntent(ctx: Context): PendingIntent {
        val intent = Intent(ctx, com.enenhhh.smartschedule.MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        return PendingIntent.getActivity(
            ctx, 900, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun buildTomorrowPush(ctx: Context): NotificationCompat.Builder {
        val manager = ScheduleManager(ctx)
        val content = "明日: ${getFormattedCourses(ctx, manager, true)}"
        return NotificationCompat.Builder(ctx, CHANNEL_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("明日课表")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(ctx))
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
            val intent = Intent(ctx, WeatherReceiver::class.java).apply { action = "com.enenhhh.smartschedule.ACTION_TOMORROW_WEATHER" }
            val pi = PendingIntent.getBroadcast(ctx, 300, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

            val cal = Calendar.getInstance().apply {
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                set(Calendar.HOUR_OF_DAY, Prefs.getWeatherHour(ctx))
                set(Calendar.MINUTE, Prefs.getWeatherMinute(ctx))
                if (timeInMillis <= System.currentTimeMillis()) { add(Calendar.DAY_OF_YEAR, 1) }
            }
            setExact(ctx, am, cal.timeInMillis, pi)
            android.util.Log.d("NotificationHelper", "Weather reminder scheduled for ${cal.time}")
        } catch (e: SecurityException) { }
    }

    fun cancelWeatherReminder(ctx: Context) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(ctx, WeatherReceiver::class.java).apply { action = "com.enenhhh.smartschedule.ACTION_TOMORROW_WEATHER" }
        val pi = PendingIntent.getBroadcast(ctx, 300, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        am.cancel(pi)
    }

    fun triggerWeatherNow(ctx: Context) {
        thread { triggerWeatherNowBlocking(ctx) }
    }

    /** 已经在子线程里时调用这个版本 */
    fun triggerWeatherNowBlocking(ctx: Context) {
        createChannels(ctx)
        val content = com.enenhhh.smartschedule.weather.WeatherFetcher.fetchTomorrowSummary(ctx)
        val builder = NotificationCompat.Builder(ctx, CHANNEL_WEATHER)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("明天天气")
            .setContentText(content)
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(ctx))
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
        NotificationManagerCompat.from(ctx).notify(NOTIF_ID_WEATHER, builder.build())
    }

    fun scheduleDailyPersistentUpdate(ctx: Context) {
        try {
            val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(ctx, UpdatePersistentReceiver::class.java).apply { action = "com.enenhhh.smartschedule.ACTION_UPDATE_PERSISTENT" }
            val pi = PendingIntent.getBroadcast(ctx, 200, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 1); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
            }
            setExact(ctx, am, cal.timeInMillis, pi)
        } catch (e: SecurityException) { }
    }

    fun refreshAll(ctx: Context) {
        if (Prefs.isPersistentEnabled(ctx) || Prefs.isKeepAlive(ctx)) {
            startForegroundWork(ctx)
        } else {
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_PERSISTENT)
            stopForegroundWork(ctx)
        }

        if (Prefs.isTomorrowReminderEnabled(ctx)) {
            scheduleTomorrowReminder(ctx)
        } else {
            cancelTomorrowReminder(ctx)
        }

        if (Prefs.isWeatherReminderEnabled(ctx)) {
            scheduleWeatherReminder(ctx)
        } else {
            cancelWeatherReminder(ctx)
        }
    }

    fun getFormattedCourses(ctx: Context, manager: com.enenhhh.smartschedule.data.ScheduleManager, isTomorrow: Boolean): String {
        val cal = Calendar.getInstance()
        if (isTomorrow) cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayIndex = when(cal.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
            Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
            else -> 7
        }
        val isOdd = com.enenhhh.smartschedule.util.DateUtils.getWeekNumberFrom(Prefs.getStartDate(ctx), cal.timeInMillis) % 2 == 1
        val plan = manager.getActiveSchedule(isOdd, dayIndex)
        
        return plan?.let { p ->
            val courses = p.courses.filter { it.isNotBlank() }.map { name -> manager.getShortName(name) }
            val base = if (p.isMorningReadEnabled && p.morningRead.isNotBlank()) {
                "${manager.getShortName(p.morningRead)} | ${courses.joinToString(" ")}"
            } else {
                courses.joinToString(" ")
            }
            if (base.isBlank()) "无课" else base
        } ?: "无课"
    }

    fun buildPersistentNotification(ctx: Context): NotificationCompat.Builder {
        val manager = ScheduleManager(ctx)
        val hw = com.enenhhh.smartschedule.data.HomeworkRepository(ctx).getPending().filter { it.getDaysRemaining() <= 1 }
        val hwContent = if (hw.isNotEmpty()) "\n紧急作业: ${hw.joinToString { it.title }}" else ""
        
        val content = buildString {
            append("今日: ${getFormattedCourses(ctx, manager, false)}")
            val tomorrow = getFormattedCourses(ctx, manager, true)
            if (tomorrow.isNotBlank()) append("\n明日: $tomorrow")
            if (hwContent.isNotBlank()) append(hwContent)
        }

        return NotificationCompat.Builder(ctx, CHANNEL_PERSISTENT)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("智能课表")
            .setContentText(content.lineSequence().firstOrNull().orEmpty())
            .setStyle(NotificationCompat.BigTextStyle().bigText(content))
            .setContentIntent(openAppIntent(ctx))
            .setOngoing(true)
            .setShowWhen(false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
    }

    fun triggerPersistentUpdateNow(ctx: Context) {
        if (!Prefs.isPersistentEnabled(ctx)) {
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_PERSISTENT)
            return
        }
        val nm = NotificationManagerCompat.from(ctx)
        val notif = buildPersistentNotification(ctx).build()
        nm.notify(NOTIF_ID_PERSISTENT, notif)
    }

    fun startForegroundWork(ctx: Context) {
        if (Prefs.isKeepAlive(ctx)) {
            com.enenhhh.smartschedule.services.KeepAliveService.start(ctx)
        } else {
            triggerPersistentUpdateNow(ctx)
            scheduleDailyPersistentUpdate(ctx)
        }
    }

    fun stopForegroundWork(ctx: Context) {
        com.enenhhh.smartschedule.services.KeepAliveService.stop(ctx)
        if (!Prefs.isPersistentEnabled(ctx)) {
            NotificationManagerCompat.from(ctx).cancel(NOTIF_ID_PERSISTENT)
        }
    }

    fun updatePersistentNotification(ctx: Context) {
        if (Prefs.isKeepAlive(ctx)) {
            com.enenhhh.smartschedule.services.KeepAliveService.requestUpdate(ctx)
        } else {
            triggerPersistentUpdateNow(ctx)
        }
    }

    fun notifyApkDownloaded(ctx: Context, file: File, version: String) {
        createChannels(ctx)
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val pi = PendingIntent.getActivity(ctx, 401, installIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val content = "${file.name} 下载完成, 点击安装（版本 ${version}）"
        val builder = NotificationCompat.Builder(ctx, CHANNEL_UPDATES)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("更新下载完成")
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
                if (am.canScheduleExactAlarms()) { am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
                else { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } else {
                am.setExact(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            }
        } catch (e: SecurityException) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi)
                else am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi)
            } catch (e2: Exception) {}
        }
    }
}