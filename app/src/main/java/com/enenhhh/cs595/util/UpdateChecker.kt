package com.enenhhh.smartschedule.util

import android.app.Activity
import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.enenhhh.smartschedule.notifications.NotificationHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker {

    private const val UPDATE_TEXT_URL = "https://enblog.asia/files/smartschedule/update.txt"
    private const val APK_BASE = "https://enblog.asia/files/smartschedule/"

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun compareVersion(a: String, b: String): Int {
        fun parse(v: String) = v.split(".").map { it.toIntOrNull() ?: 0 }
        val A = parse(a)
        val B = parse(b)
        val n = maxOf(A.size, B.size)
        for (i in 0 until n) {
            val ai = if (i < A.size) A[i] else 0
            val bi = if (i < B.size) B[i] else 0
            if (ai != bi) return ai - bi
        }
        return 0
    }

    data class UpdateInfo(val latest: String, val changelog: String, val apkUrl: String)

    private fun parseUpdateText(text: String): UpdateInfo? {
        val lines = text.split("\n")
        if (lines.isEmpty()) return null
        val latest = lines.first().trim()
        if (latest.isBlank()) return null
        val log = lines.drop(1).joinToString("\n").trim()
        val apk = "${APK_BASE}${latest}.apk"
        return UpdateInfo(latest, log, apk)
    }

    fun checkAndPrompt(activity: Activity, manual: Boolean) {
        thread {
            try {
                val content = httpGet(UPDATE_TEXT_URL)
                val info = parseUpdateText(content) ?: run {
                    if (manual) activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "未获取到更新信息", android.widget.Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                val pInfo = activity.packageManager.getPackageInfo(activity.packageName, 0)
                val current = pInfo.versionName ?: "1.0"
                val need = compareVersion(info.latest, current) > 0
                activity.runOnUiThread {
                    if (need) {
                        showUpdateDialog(activity, info, current)
                    } else if (manual) {
                        android.widget.Toast.makeText(activity, "已是最新版本（$current）", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    activity.runOnUiThread {
                        android.widget.Toast.makeText(activity, "检查更新失败：${e.message ?: "网络错误"}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, info: UpdateInfo, current: String) {
        AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${info.latest}")
            .setMessage("当前版本：$current\n\n更新日志：\n${info.changelog.ifBlank { "（无）" }}")
            .setPositiveButton("更新") { _, _ ->
                downloadAndInstall(activity, info)
            }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, info: UpdateInfo) {
        val dialog = android.app.ProgressDialog(activity).apply {
            setCancelable(false)
            setTitle("正在下载")
            setMessage("0%")
            setProgressStyle(android.app.ProgressDialog.STYLE_HORIZONTAL)
            max = 100
            show()
        }
        thread {
            try {
                val url = URL(info.apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000
                conn.readTimeout = 15000
                conn.requestMethod = "GET"
                conn.connect()
                val total = conn.contentLength.coerceAtLeast(1)
                val dir = activity.getExternalFilesDir("downloads") ?: activity.filesDir
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "smartschedule_${info.latest}.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { out ->
                        val buf = ByteArray(8 * 1024)
                        var read: Int
                        var sum = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read)
                            sum += read
                            val p = (sum * 100 / total).toInt().coerceIn(0, 100)
                            activity.runOnUiThread {
                                dialog.progress = p
                                dialog.setMessage("$p%")
                            }
                        }
                        out.flush()
                    }
                }
                activity.runOnUiThread { dialog.dismiss() }

                // 通知栏提示：点击安装
                NotificationHelper.notifyApkDownloaded(activity, file, info.latest)

                // 尝试自动拉起安装
                tryInstallApk(activity, file)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    android.widget.Toast.makeText(activity, "下载失败：${e.message ?: "网络错误"}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun tryInstallApk(activity: Activity, file: File) {
        // Android 8.0+ 需要“安装未知应用”权限
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val allowed = activity.packageManager.canRequestPackageInstalls()
            if (!allowed) {
                // 引导到设置界面开权限
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activity.startActivity(intent)
                    android.widget.Toast.makeText(
                        activity,
                        "请在“安装未知应用”中允许来自此来源，然后点击通知完成安装",
                        android.widget.Toast.LENGTH_LONG
                    ).show()
                } catch (_: ActivityNotFoundException) {
                    // 直接尝试调起安装器（部分系统会自己弹允许页）
                    installApk(activity, file)
                }
                return
            }
        }
        installApk(activity, file)
    }

    private fun installApk(ctx: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            ctx.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            android.widget.Toast.makeText(ctx, "无法打开安装程序", android.widget.Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            android.widget.Toast.makeText(ctx, "安装失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }
}