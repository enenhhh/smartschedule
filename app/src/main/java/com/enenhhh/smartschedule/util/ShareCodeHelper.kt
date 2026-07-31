package com.enenhhh.smartschedule.util

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ShareCodeHelper {

    private const val BASE_URL = "http://blog.enhub.top/fxm/"

    // 这里以前有一段 trustAllCerts()，它调用的是
    // HttpsURLConnection.setDefault{SSLSocketFactory,HostnameVerifier}，
    // 属于「进程级」开关：一旦 ShareCodeHelper 被加载，整个 App（包括
    // 下载 APK 的更新流程）的 HTTPS 证书校验就全部失效了。已移除。

    data class ShareResult(
        val success: Boolean,
        val code: String = "",
        val error: String = ""
    )

    data class DownloadResult(
        val success: Boolean,
        val payload: JSONObject? = null,
        val error: String = ""
    )

    /**
     * 上传配置，返回分享码
     */
    suspend fun upload(context: Context): ShareResult = withContext(Dispatchers.IO) {
        try {
            val backupData = BackupHelper.exportToJson(context)
            if (backupData == null) {
                return@withContext ShareResult(false, error = "导出配置失败")
            }

            val url = URL("${BASE_URL}?action=upload")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            OutputStreamWriter(conn.outputStream, "UTF-8").use {
                it.write(backupData.toString())
            }

            val responseCode = conn.responseCode
            val reader = BufferedReader(InputStreamReader(
                if (responseCode in 200..299) conn.inputStream else conn.errorStream,
                "UTF-8"
            ))
            val response = reader.readText()
            reader.close()

            val json = JSONObject(response)
            if (json.optBoolean("success", false)) {
                val code = json.getString("code")
                // 保存到本地记录
                saveShareCodeRecord(context, code)
                ShareResult(true, code = code)
            } else {
                ShareResult(false, error = json.optString("error", "未知错误"))
            }
        } catch (e: java.net.UnknownHostException) {
            ShareResult(false, error = "服务器连接失败，分享码功能暂不可用:(")
        } catch (e: java.net.ConnectException) {
            ShareResult(false, error = "服务器连接失败，分享码功能暂不可用:(")
        } catch (e: java.net.SocketTimeoutException) {
            ShareResult(false, error = "服务器连接超时，请稍后重试")
        } catch (e: Exception) {
            ShareResult(false, error = "网络错误：${e.message}")
        }
    }

    // ========== 本地分享码记录 ==========

    data class ShareCodeRecord(
        val code: String,
        val createdAt: String // yyyy-MM-dd HH:mm
    )

    private fun saveShareCodeRecord(context: Context, code: String) {
        val prefs = context.getSharedPreferences("share_codes", Context.MODE_PRIVATE)
        val existing = prefs.getString("records", "[]") ?: "[]"
        val arr = org.json.JSONArray(existing)
        val record = JSONObject().apply {
            put("code", code)
            put("createdAt", java.text.SimpleDateFormat("yyyy-MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date()))
        }
        arr.put(record)
        prefs.edit().putString("records", arr.toString()).apply()
    }

    fun getShareCodeRecords(context: Context): List<ShareCodeRecord> {
        val prefs = context.getSharedPreferences("share_codes", Context.MODE_PRIVATE)
        val existing = prefs.getString("records", "[]") ?: "[]"
        val arr = org.json.JSONArray(existing)
        val result = mutableListOf<ShareCodeRecord>()
        for (i in 0 until arr.length()) {
            val obj = arr.getJSONObject(i)
            result.add(ShareCodeRecord(
                code = obj.getString("code"),
                createdAt = obj.optString("createdAt", "")
            ))
        }
        return result.reversed() // 最新的在前
    }

    /**
     * 通过分享码下载配置
     */
    suspend fun download(code: String): DownloadResult = withContext(Dispatchers.IO) {
        try {
            val url = URL("${BASE_URL}?action=download&code=$code")
            val conn = url.openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            val reader = BufferedReader(InputStreamReader(
                if (responseCode in 200..299) conn.inputStream else conn.errorStream,
                "UTF-8"
            ))
            val response = reader.readText()
            reader.close()

            val json = JSONObject(response)
            if (json.optBoolean("success", false)) {
                DownloadResult(true, payload = json.getJSONObject("payload"))
            } else {
                DownloadResult(false, error = json.optString("error", "未知错误"))
            }
        } catch (e: java.net.UnknownHostException) {
            DownloadResult(false, error = "服务器连接失败，分享码功能暂不可用:(")
        } catch (e: java.net.ConnectException) {
            DownloadResult(false, error = "服务器连接失败，分享码功能暂不可用:(")
        } catch (e: java.net.SocketTimeoutException) {
            DownloadResult(false, error = "服务器连接超时，请稍后重试")
        } catch (e: Exception) {
            DownloadResult(false, error = "网络错误：${e.message}")
        }
    }

    /**
     * 下载并导入配置
     */
    suspend fun downloadAndImport(context: Context, code: String): String {
        val result = download(code)
        if (!result.success) {
            return result.error
        }
        return try {
            BackupHelper.importFromJson(context, result.payload!!)
            "导入成功"
        } catch (e: Exception) {
            "导入失败：${e.message}"
        }
    }
}