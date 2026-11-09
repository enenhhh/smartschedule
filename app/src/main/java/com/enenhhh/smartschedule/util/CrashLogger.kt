package com.enenhhh.smartschedule.util

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** 把未捕获异常写到 files/crash.log，方便用户在「关于」里复制给作者 */
object CrashLogger {
    private const val FILE = "crash.log"
    private const val MAX_BYTES = 200 * 1024

    fun install(context: Context) {
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            runCatching { write(app, thread, e) }
            previous?.uncaughtException(thread, e)
        }
    }

    private fun write(ctx: Context, thread: Thread, e: Throwable) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val version = runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName }.getOrNull()
        val header = buildString {
            append("==== ").append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date())).append(" ====\n")
            append("app ").append(version).append(" · Android ").append(Build.VERSION.RELEASE).append(" (API ").append(Build.VERSION.SDK_INT).append(") · ")
            append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("thread ").append(thread.name).append('\n')
        }
        val f = file(ctx)
        val old = if (f.exists()) f.readText() else ""
        val merged = (header + sw.toString() + "\n\n" + old)
        f.writeText(if (merged.length > MAX_BYTES) merged.substring(0, MAX_BYTES) else merged)
    }

    private fun file(ctx: Context) = File(ctx.filesDir, FILE)

    fun hasLog(ctx: Context): Boolean = file(ctx).let { it.exists() && it.length() > 0 }

    fun read(ctx: Context): String = runCatching { file(ctx).readText() }.getOrDefault("")

    fun clear(ctx: Context) { runCatching { file(ctx).delete() } }
}
