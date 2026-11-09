package com.enenhhh.smartschedule

import android.app.Application
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.theme.ThemeState
import com.enenhhh.smartschedule.util.CrashLogger
import com.enenhhh.smartschedule.util.FontManager

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        CrashLogger.install(this)
        // 主题 / 字体只在这里读一次落到内存；设置页改动直接驱动重组，不需要重启
        com.enenhhh.smartschedule.util.Prefs.migrateAppearance51(this)
        ThemeState.load(this)
        FontManager.init(this)
        runCatching { NotificationHelper.createChannels(this) }
    }
}
