package com.enenhhh.smartschedule.util

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.enenhhh.smartschedule.notifications.NotificationHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

/**
 * 应用内更新（纯 HTTPS）。
 *
 * update.txt 的格式（所有更新源通用）：
 *   第 1 行  最新版本号，如 `5.2`
 *   可选行   `sha256: <64 位十六进制>`，有则下载后校验 APK，不一致就丢弃
 *   其余行   更新说明（Markdown）
 *
 * 默认走 GitHub Release（不占自己的服务器带宽，服务器被打也不影响更新）；
 * 设置里可以手动切到自建服务器。首选源失败时会自动依次尝试其它源。
 *
 * GitHub Release 的约定（见 docs/RELEASING.md）：tag 为 `v<版本号>`，附件为
 * `update.txt` 与 `smartschedule-<版本号>.apk`。
 */
object UpdateChecker {

    private const val GITHUB_REPO = "enenhhh/smartschedule"

    /**
     * @param updateUrl 取 update.txt 的地址
     * @param apkUrlFor 根据版本号给出 APK 地址
     */
    data class Source(val name: String, val updateUrl: String, val apkUrlFor: (String) -> String)

    /** 下标 0 是默认源。下标会存进设置，调整顺序前请先想想老用户的存档 */
    val sources = listOf(
        Source(
            "GitHub",
            "https://github.com/$GITHUB_REPO/releases/latest/download/update.txt"
        ) { v -> "https://github.com/$GITHUB_REPO/releases/download/v$v/smartschedule-$v.apk" },
        Source(
            "我的服务器",
            "https://blog.enhub.top/files/smartschedule/update.txt"
        ) { v -> "https://blog.enhub.top/files/smartschedule/$v.apk" },
        Source(
            "服务器备用",
            "https://enhub.top/files/smartschedule/update.txt"
        ) { v -> "https://enhub.top/files/smartschedule/$v.apk" }
    )

    data class UpdateInfo(
        val latest: String,
        val changelog: String,
        val apkUrl: String,
        val source: Source,
        /** update.txt 里声明的 APK SHA-256（小写十六进制）；没声明则为 null，不做校验 */
        val sha256: String? = null
    )

    data class SourceTestResult(val source: Source, val latencyMs: Long?, val latest: String?, val error: String?)

    sealed interface CheckResult {
        data class UpdateAvailable(val info: UpdateInfo) : CheckResult
        data class UpToDate(val latest: String) : CheckResult
        data class Failed(val message: String) : CheckResult
    }

    const val AUTO_CHECK_INTERVAL_MS = 12L * 60 * 60 * 1000

    fun currentVersion(ctx: Context): String =
        runCatching { ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "0" }.getOrDefault("0")

    private fun httpGet(url: String, timeoutMs: Int = 8000): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        if (conn !is javax.net.ssl.HttpsURLConnection) throw IllegalStateException("只允许 HTTPS 更新源")
        conn.connectTimeout = timeoutMs
        conn.readTimeout = timeoutMs
        conn.requestMethod = "GET"
        conn.setRequestProperty("User-Agent", "smartschedule (Android)")
        return try {
            val code = conn.responseCode
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    fun compareVersion(a: String, b: String): Int {
        fun parse(v: String) = v.trim().removePrefix("v").split(".").map { it.filter { c -> c.isDigit() }.toIntOrNull() ?: 0 }
        val pa = parse(a); val pb = parse(b)
        for (i in 0 until maxOf(pa.size, pb.size)) {
            val x = pa.getOrElse(i) { 0 }; val y = pb.getOrElse(i) { 0 }
            if (x != y) return x.compareTo(y)
        }
        return 0
    }

    private val SHA_LINE = Regex("(?i)sha-?256\\s*[:=]\\s*([0-9a-f]{64})")

    private fun parseUpdateText(text: String, source: Source): UpdateInfo? {
        val lines = text.trim().lines()
        val latest = lines.firstOrNull()?.trim().orEmpty()
        // 版本号只允许数字和点，因为它会被拼进下载地址
        if (latest.isBlank() || latest.length > 20 || !latest.first().isDigit() || !latest.all { it.isDigit() || it == '.' }) return null
        val rest = lines.drop(1)
        val sha = rest.firstNotNullOfOrNull { SHA_LINE.matchEntire(it.trim())?.groupValues?.get(1)?.lowercase() }
        val log = rest.filterNot { SHA_LINE.matches(it.trim()) }.joinToString("\n").trim()
        return UpdateInfo(latest, log, source.apkUrlFor(latest), source, sha)
    }

    suspend fun check(ctx: Context, sourceIndex: Int = Prefs.getUpdateSource(ctx)): CheckResult = withContext(Dispatchers.IO) {
        val order = sources.indices.sortedBy { if (it == sourceIndex.coerceIn(sources.indices)) 0 else 1 }
        var lastError: String? = null
        for (i in order) {
            val src = sources[i]
            try {
                val info = parseUpdateText(httpGet(src.updateUrl), src) ?: throw IllegalStateException("更新信息格式错误")
                Prefs.setLastUpdateCheck(ctx, System.currentTimeMillis())
                return@withContext if (compareVersion(info.latest, currentVersion(ctx)) > 0) CheckResult.UpdateAvailable(info)
                else CheckResult.UpToDate(info.latest)
            } catch (e: Exception) {
                lastError = "${src.name}：${e.message ?: e.javaClass.simpleName}"
            }
        }
        CheckResult.Failed(lastError ?: "无法连接更新服务器")
    }

    suspend fun testAllSources(): List<SourceTestResult> = withContext(Dispatchers.IO) {
        sources.map { src ->
            val start = System.currentTimeMillis()
            try {
                val text = httpGet(src.updateUrl, 6000)
                val info = parseUpdateText(text, src)
                SourceTestResult(src, System.currentTimeMillis() - start, info?.latest, null)
            } catch (e: Exception) {
                SourceTestResult(src, null, null, e.message ?: e.javaClass.simpleName)
            }
        }
    }

    /**
     * 下载 APK 到 app 专属目录并返回文件。[onProgress] 0..1，总大小未知时传 -1。
     */
    suspend fun download(ctx: Context, info: UpdateInfo, onProgress: (Float) -> Unit): File = withContext(Dispatchers.IO) {
        val conn = URL(info.apkUrl).openConnection() as HttpURLConnection
        if (conn !is javax.net.ssl.HttpsURLConnection) throw IllegalStateException("只允许 HTTPS 下载")
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.requestMethod = "GET"
        conn.connect()
        if (conn.responseCode !in 200..299) throw IllegalStateException("HTTP ${conn.responseCode}")
        val total = conn.contentLengthLong
        val dir = ctx.getExternalFilesDir("downloads") ?: ctx.filesDir
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "smartschedule_${info.latest.filter { it.isLetterOrDigit() || it == '.' }}.apk")
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        try {
            conn.inputStream.use { input ->
                FileOutputStream(file).use { out ->
                    val buf = ByteArray(16 * 1024)
                    var sum = 0L
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        digest.update(buf, 0, n)
                        sum += n
                        onProgress(if (total > 0) (sum.toFloat() / total).coerceIn(0f, 1f) else -1f)
                    }
                    out.flush()
                }
            }
        } finally {
            conn.disconnect()
        }
        if (file.length() < 100 * 1024) { file.delete(); throw IllegalStateException("下载的文件不完整") }
        info.sha256?.let { expected ->
            val actual = digest.digest().joinToString("") { "%02x".format(it) }
            if (!actual.equals(expected, ignoreCase = true)) {
                file.delete()
                throw IllegalStateException("安装包校验失败（SHA-256 不一致），已丢弃。请换一个更新源重试")
            }
        }
        runCatching { NotificationHelper.notifyApkDownloaded(ctx, file, info.latest) }
        file
    }

    /** 返回 false 表示需要用户先授予「安装未知应用」权限（已跳转设置页） */
    fun install(ctx: Context, file: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !ctx.packageManager.canRequestPackageInstalls()) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                    data = Uri.parse("package:${ctx.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                })
                return false
            } catch (_: ActivityNotFoundException) { }
        }
        val uri = FileProvider.getUriForFile(ctx, "${ctx.packageName}.fileprovider", file)
        ctx.startActivity(Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        })
        return true
    }
}
