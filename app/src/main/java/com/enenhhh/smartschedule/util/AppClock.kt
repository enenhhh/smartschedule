package com.enenhhh.smartschedule.util

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.data.TempSwaps
import com.enenhhh.smartschedule.notifications.NotificationHelper
import java.time.LocalDate
import java.time.LocalTime

/**
 * 全应用共用的「现在」。
 *
 * 之前每个页面各自 `remember { LocalDate.now() }`，进程被保活几天不重建后，
 * 页面里的「今天」就停在打开 App 的那一天。现在所有页面读这里的 Compose 状态，
 * 并且在以下时机刷新，任何一个触发都足够把过期的界面纠正过来：
 *
 *  1. Activity 回到前台（[refresh]，由 MainActivity.onResume 调用）
 *  2. 系统广播：日期变化 / 手动改时间 / 时区变化 / 亮屏 / 解锁（[install] 注册）
 *  3. 界面存活期间每 15 秒的心跳（SmartScheduleApp 里的 LaunchedEffect）
 */
object AppClock {
    var today: LocalDate by mutableStateOf(LocalDate.now())
        private set

    /** 当天 0 点起的分钟数，每分钟变化一次 */
    var nowMinutes: Int by mutableIntStateOf(currentMinutes())
        private set

    private var installed = false

    private fun currentMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /** 重新读取系统时间。日期变了返回 true。可在主线程以外调用（Compose 状态写入是线程安全的快照写）。 */
    fun refresh(): Boolean {
        val d = LocalDate.now()
        val changed = d != today
        if (changed) today = d
        val m = currentMinutes()
        if (m != nowMinutes) nowMinutes = m
        return changed
    }

    /** 在 Application.onCreate 里调用一次。Application 级的接收者与进程同寿命，不依赖 Activity 是否存在 */
    fun install(app: Context) {
        if (installed) return
        installed = true
        val ctx = app.applicationContext
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent?) {
                val dayChanged = refresh()
                val action = intent?.action
                // 日期 / 时间 / 时区变化：清理过期的临时调课，并让常驻通知换成新一天的课
                if (dayChanged || action == Intent.ACTION_DATE_CHANGED ||
                    action == Intent.ACTION_TIME_CHANGED || action == Intent.ACTION_TIMEZONE_CHANGED
                ) {
                    runCatching { TempSwaps.clearExpired(ctx) }
                    ScheduleManager.invalidateCache()
                    runCatching {
                        if (Prefs.isPersistentEnabled(ctx) || Prefs.isKeepAlive(ctx)) {
                            NotificationHelper.updatePersistentNotification(ctx)
                        }
                    }
                }
            }
        }
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_DATE_CHANGED)
            addAction(Intent.ACTION_TIME_CHANGED)
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_USER_PRESENT)
        }
        // 这些都是系统广播；RECEIVER_NOT_EXPORTED 不影响接收系统广播
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            ctx.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            ctx.registerReceiver(receiver, filter)
        }
    }
}
