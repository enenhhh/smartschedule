package com.enenhhh.smartschedule.services

import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.util.Prefs

class KeepAliveService : Service() {

    companion object {
        private const val ACTION_UPDATE = "ACTION_UPDATE"
        fun start(ctx: Context) {
            try {
                NotificationHelper.createChannels(ctx)
                val i = Intent(ctx, KeepAliveService::class.java)
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
                NotificationHelper.scheduleDailyPersistentUpdate(ctx)
            } catch (e: SecurityException) {
                android.util.Log.e("KeepAliveService", "启动服务失败，缺少精确闹钟权限", e)
                throw e
            }
        }

        fun stop(ctx: Context) {
            ctx.stopService(Intent(ctx, KeepAliveService::class.java))
        }

        fun requestUpdate(ctx: Context) {
            try {
                // 确保渠道已创建，避免 Bad notification for startForeground
                NotificationHelper.createChannels(ctx)
                val i = Intent(ctx, KeepAliveService::class.java).apply { action = ACTION_UPDATE }
                if (android.os.Build.VERSION.SDK_INT >= 26) {
                    ctx.startForegroundService(i)
                } else {
                    ctx.startService(i)
                }
            } catch (e: SecurityException) {
                android.util.Log.e("KeepAliveService", "请求更新失败，缺少精确闹钟权限", e)
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        try {
            // 再次兜底创建渠道，确保前台通知有效
            NotificationHelper.createChannels(this)

            val builder = NotificationHelper.buildPersistentNotification(this)
            val notif = builder.build()
            startForeground(NotificationHelper.NOTIF_ID_PERSISTENT, notif)

            if (!Prefs.isPersistentEnabled(this) && !Prefs.isKeepAlive(this)) {
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    @Suppress("DEPRECATION")
                    stopForeground(true)
                }
                stopSelf()
            }
            return START_STICKY
        } catch (e: SecurityException) {
            android.util.Log.e("KeepAliveService", "启动前台服务失败", e)
            stopSelf()
            return START_NOT_STICKY
        } catch (e: Exception) {
            android.util.Log.e("KeepAliveService", "启动前台服务异常", e)
            stopSelf()
            return START_NOT_STICKY
        }
    }

    override fun onBind(p0: Intent?): IBinder? = null
}