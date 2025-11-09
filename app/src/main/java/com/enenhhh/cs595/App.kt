package com.enenhhh.smartschedule

import android.app.Application
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.exitProcess

class App : Application() {
    override fun onCreate() {
        super.onCreate()

        val oldHandler = Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                val dir = getExternalFilesDir(null) ?: filesDir
                val f = File(dir, "last_crash.txt")
                val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())
                val trace = Log.getStackTraceString(throwable)
                val content = buildString {
                    appendLine("Time: $ts")
                    appendLine("Thread: ${thread.name}")
                    appendLine("Exception: ${throwable}")
                    appendLine()
                    appendLine(trace)
                }
                f.writeText(content)
            } catch (_: Throwable) {
                // 忽略写文件失败
            }

            // 交还给系统默认处理（照常崩溃退出）
            oldHandler?.uncaughtException(thread, throwable) ?: run {
                // 没有旧 handler 就直接退出
                exitProcess(2)
            }
        }
    }
}