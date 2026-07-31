package com.enenhhh.smartschedule.util

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.core.content.FileProvider
import com.enenhhh.smartschedule.notifications.NotificationHelper
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import kotlin.concurrent.thread

object UpdateChecker {

    data class Source(val name: String, val baseUrl: String)

    val sources = listOf(
        Source("博客源", "http://blog.enhub.top/files/smartschedule/"),
        Source("备用源", "https://enhub.top/files/smartschedule/")
    )

    private var selectedSourceIndex = 0

    fun getSelectedSource(): Source = sources[selectedSourceIndex]
    fun getSelectedSourceIndex(): Int = selectedSourceIndex
    fun setSelectedSource(index: Int) { selectedSourceIndex = index.coerceIn(0, sources.lastIndex) }

    private fun httpGet(url: String): String {
        // 注意：这里必须走系统默认的证书校验。
        // 之前的版本对 HTTPS 关闭了证书 / 主机名校验，而这条链路最终会
        // 下载并安装 APK，等于给中间人留了直接投毒的口子。
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
        val A = parse(a); val B = parse(b)
        val n = maxOf(A.size, B.size)
        for (i in 0 until n) {
            val ai = if (i < A.size) A[i] else 0
            val bi = if (i < B.size) B[i] else 0
            if (ai != bi) return ai - bi
        }
        return 0
    }

    data class UpdateInfo(val latest: String, val changelog: String, val apkUrl: String)
    data class SourceTestResult(val source: Source, val latency: Long, val latestVersion: String, val error: String?)

    private fun parseUpdateText(text: String, baseUrl: String): UpdateInfo? {
        val lines = text.split("\n")
        if (lines.isEmpty()) return null
        val latest = lines.first().trim()
        if (latest.isBlank()) return null
        val log = lines.drop(1).joinToString("\n").trim()
        return UpdateInfo(latest, log, "${baseUrl}${latest}.apk")
    }

    /**
     * 测试所有源的延迟和最新版本
     */
    fun testAllSources(): List<SourceTestResult> {
        return sources.map { source ->
            try {
                val start = System.currentTimeMillis()
                val content = httpGet("${source.baseUrl}update.txt")
                val latency = System.currentTimeMillis() - start
                val info = parseUpdateText(content, source.baseUrl)
                SourceTestResult(source, latency, info?.latest ?: "未知", null)
            } catch (e: Exception) {
                SourceTestResult(source, -1, "未知", e.message ?: "连接失败")
            }
        }
    }

    fun checkAndPrompt(activity: Activity, manual: Boolean) {
        thread {
            try {
                val source = getSelectedSource()
                val content = httpGet("${source.baseUrl}update.txt")
                val info = parseUpdateText(content, source.baseUrl) ?: run {
                    if (manual) activity.runOnUiThread {
                        Toast.makeText(activity, "未获取到更新信息", Toast.LENGTH_SHORT).show()
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
                        Toast.makeText(activity, "已是最新版本（$current）", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                if (manual) {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "检查更新失败：${e.message ?: "网络错误"}", Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    fun downloadLatestApk(activity: Activity) {
        thread {
            try {
                val source = getSelectedSource()
                val content = httpGet("${source.baseUrl}update.txt")
                val info = parseUpdateText(content, source.baseUrl) ?: run {
                    activity.runOnUiThread {
                        Toast.makeText(activity, "未获取到更新信息", Toast.LENGTH_SHORT).show()
                    }
                    return@thread
                }
                activity.runOnUiThread { downloadAndInstall(activity, info) }
            } catch (e: Exception) {
                activity.runOnUiThread {
                    Toast.makeText(activity, "下载失败：${e.message ?: "网络错误"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun showUpdateDialog(activity: Activity, info: UpdateInfo, current: String) {
        android.app.AlertDialog.Builder(activity)
            .setTitle("发现新版本 ${info.latest}")
            .setMessage("当前版本：$current\n\n更新日志：\n${info.changelog.ifBlank { "（无）" }}")
            .setPositiveButton("更新") { _, _ -> downloadAndInstall(activity, info) }
            .setNegativeButton("稍后", null)
            .show()
    }

    private fun downloadAndInstall(activity: Activity, info: UpdateInfo) {
        val progressBar = android.widget.ProgressBar(activity, null, android.R.attr.progressBarStyleHorizontal).apply {
            max = 100; isIndeterminate = false
        }
        val tvPercent = android.widget.TextView(activity).apply {
            text = "0%"; setPadding(0, 16, 0, 0); gravity = android.view.Gravity.CENTER
        }
        val container = android.widget.LinearLayout(activity).apply {
            orientation = android.widget.LinearLayout.VERTICAL; setPadding(48, 24, 48, 8)
            addView(progressBar); addView(tvPercent)
        }
        val dialog = androidx.appcompat.app.AlertDialog.Builder(activity)
            .setTitle("正在下载").setView(container).setCancelable(false).create()
        dialog.show()

        thread {
            try {
                val url = URL(info.apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 15000; conn.readTimeout = 15000; conn.requestMethod = "GET"; conn.connect()
                val total = conn.contentLength.coerceAtLeast(1)
                val dir = activity.getExternalFilesDir("downloads") ?: activity.filesDir
                if (!dir.exists()) dir.mkdirs()
                val file = File(dir, "smartschedule_${info.latest}.apk")
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { out ->
                        val buf = ByteArray(8192); var read: Int; var sum = 0L
                        while (input.read(buf).also { read = it } != -1) {
                            out.write(buf, 0, read); sum += read
                            val p = (sum * 100 / total).toInt().coerceIn(0, 100)
                            activity.runOnUiThread { progressBar.progress = p; tvPercent.text = "$p%" }
                        }
                        out.flush()
                    }
                }
                activity.runOnUiThread { dialog.dismiss() }
                NotificationHelper.notifyApkDownloaded(activity, file, info.latest)
                tryInstallApk(activity, file)
            } catch (e: Exception) {
                activity.runOnUiThread {
                    dialog.dismiss()
                    Toast.makeText(activity, "下载失败：${e.message ?: "网络错误"}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun tryInstallApk(activity: Activity, file: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!activity.packageManager.canRequestPackageInstalls()) {
                try {
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                        data = Uri.parse("package:${activity.packageName}"); addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                    Toast.makeText(activity, "请允许安装未知应用，然后点击通知完成安装", Toast.LENGTH_LONG).show()
                } catch (_: ActivityNotFoundException) { installApk(activity, file) }
                return
            }
        }
        installApk(activity, file)
    }

    private fun installApk(ctx: Context, file: File) {
        try {
            val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
            ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
            })
        } catch (e: Exception) {
            Toast.makeText(ctx, "安装失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}