package com.enenhhh.smartschedule

import android.app.Application
import android.util.Log
import com.enenhhh.smartschedule.ui.theme.ThemeState
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class App : Application() {

    override fun onCreate() {
        super.onCreate()
        // 深色模式 / 主题色统一由 ThemeState 管理，这里读一次落到内存，
        // 之后设置页改动会直接驱动所有界面重组，不需要重启应用。
        // （原先是每个 Activity 各自 setTheme + AppCompatDelegate，改完必须重启才生效。）
        ThemeState.load(this)
        installCrashLogger()
    }

    private fun installCrashLogger() {
        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = File(dir, "last_crash.txt")
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                f.writeText(
                    buildString {
                        appendLine("Time: $ts")
                        appendLine("Version: ${versionLabel()}")
                        appendLine("Device: ${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL} (API ${android.os.Build.VERSION.SDK_INT})")
                        appendLine("Thread: ${thread.name}")
                        appendLine("Exception: $throwable")
                        appendLine()
                        appendLine(Log.getStackTraceString(throwable))
                    }
                )
            } catch (_: Throwable) {
                // 写日志失败不能影响崩溃本身的处理
            }

            // 交还给系统默认处理（照常崩溃退出）
            oldHandler?.uncaughtException(thread, throwable) ?: exitProcess(2)
        }
    }

    private fun versionLabel(): String = runCatching {
        packageManager.getPackageInfo(packageName, 0).versionName ?: "unknown"
    }.getOrDefault("unknown")
}
