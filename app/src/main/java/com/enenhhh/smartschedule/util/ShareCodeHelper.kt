package com.enenhhh.smartschedule.util

import android.content.Context
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.models.LegacySchedulePlan
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.models.Timetable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate

/**
 * 分享码（6 位数字）。
 *
 * v5 起只上传「课表 + 时间方案 + 简称 + 开学日期」，不再把 SharedPreferences、作业一股脑发到服务器；
 * 上传前在本地检查大小（[MAX_BYTES]），服务器端同样限制。导入时先解析预览，由用户决定合并还是替换，
 * 任何情况下都不会清空本地设置或作业。
 *
 * 载荷格式 v2：
 * {
 *   "v": 2, "app": "smartschedule", "created": "2026-10-16T12:00:00",
 *   "startDate": "2026-09-01",
 *   "timePlans": [ TimePlan.toJsonObject()... ],
 *   "timetables": [ Timetable.toJsonObject()... ],
 *   "shortNames": { "语文": "语" }
 * }
 * 兼容 v1（旧版）：{ "prefs": {...}, "plans": { "time/x.json": "...", "course/y.json": "..." }, "homework": "..." }
 */
object ShareCodeHelper {

    const val BASE_URL = "https://blog.enhub.top/fxm/"
    const val MAX_BYTES = 64 * 1024
    const val EXPIRE_DAYS = 7
    private const val RECORDS_PREF = "share_codes"
    private const val USER_AGENT = "smartschedule/5.0 (Android)"

    data class ShareResult(val ok: Boolean, val code: String? = null, val error: String? = null)

    data class ShareCodeRecord(val code: String, val createdAt: String)

    /** 解析后的可导入内容 */
    data class Preview(
        val timePlans: List<TimePlan>,
        val timetables: List<Timetable>,
        val shortNames: Map<String, String>,
        val startDate: LocalDate?,
        val legacy: Boolean,
        val created: String?
    ) {
        val isEmpty: Boolean get() = timePlans.isEmpty() && timetables.isEmpty() && shortNames.isEmpty()
        val courseCount: Int get() = timetables.sumOf { tt -> tt.days.sumOf { d -> d.courses.count { it.isNotBlank() } } }
    }

    fun isValidCode(code: String): Boolean = code.length == 6 && code.all { it.isDigit() }

    // ------------------------------------------------------------------ 构建载荷

    /**
     * @param timetableIds 为 null 时分享全部课表；否则只分享给定 id 的课表（及它们用到的时间方案）
     */
    fun buildPayload(ctx: Context, manager: ScheduleManager, timetableIds: List<String>? = null, includeStartDate: Boolean = true): JSONObject {
        val timetables = manager.getTimetables().filter { timetableIds == null || it.id in timetableIds }
        val usedPlans = timetables.map { it.timePlanName }.toSet()
        val timePlans = manager.getTimePlans().filter { timetableIds == null || it.name in usedPlans }
        val courseNames = timetables.flatMap { it.allCourseNames() }.toSet()
        val shortNames = manager.getCustomShortNames().filter { timetableIds == null || it.key in courseNames }

        return JSONObject().apply {
            put("v", 2)
            put("app", "smartschedule")
            put("created", java.time.LocalDateTime.now().withNano(0).toString())
            if (includeStartDate && Prefs.isStartDateSet(ctx)) put("startDate", DateUtils.format(Prefs.getStartDate(ctx)))
            put("timePlans", JSONArray().apply { timePlans.forEach { put(it.toJsonObject()) } })
            put("timetables", JSONArray().apply { timetables.forEach { put(it.toJsonObject()) } })
            put("shortNames", JSONObject().apply { shortNames.forEach { (k, v) -> put(k, v) } })
        }
    }

    fun payloadSize(payload: JSONObject): Int = payload.toString().toByteArray(Charsets.UTF_8).size

    // ------------------------------------------------------------------ 网络

    suspend fun upload(ctx: Context, payload: JSONObject): ShareResult = withContext(Dispatchers.IO) {
        val bytes = payload.toString().toByteArray(Charsets.UTF_8)
        if (bytes.size > MAX_BYTES) {
            return@withContext ShareResult(false, error = "内容太大（${bytes.size / 1024} KB，上限 ${MAX_BYTES / 1024} KB），请只分享单张课表")
        }
        try {
            val conn = (URL("${BASE_URL}?action=upload").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 10_000
                readTimeout = 15_000
                doOutput = true
                setFixedLengthStreamingMode(bytes.size)
            }
            conn.outputStream.use { it.write(bytes) }
            val (status, body) = readResponse(conn)
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext ShareResult(false, error = "服务器返回了无法识别的内容（HTTP $status）")
            if (json.optBoolean("success", false)) {
                val code = json.optString("code", "")
                if (!isValidCode(code)) return@withContext ShareResult(false, error = "服务器返回的分享码无效")
                saveShareCodeRecord(ctx, code)
                ShareResult(true, code = code)
            } else {
                ShareResult(false, error = friendlyError(json.optString("error", ""), status))
            }
        } catch (e: Exception) {
            ShareResult(false, error = "网络错误：${e.message ?: e.javaClass.simpleName}")
        }
    }

    suspend fun download(code: String): Result<Preview> = withContext(Dispatchers.IO) {
        if (!isValidCode(code)) return@withContext Result.failure(IllegalArgumentException("分享码应为 6 位数字"))
        try {
            val conn = (URL("${BASE_URL}?action=download&code=$code").openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", USER_AGENT)
                connectTimeout = 10_000
                readTimeout = 15_000
            }
            val (status, body) = readResponse(conn)
            val json = runCatching { JSONObject(body) }.getOrNull()
                ?: return@withContext Result.failure(IllegalStateException(if (status == 404) "分享码不存在或已过期" else "服务器返回了无法识别的内容（HTTP $status）"))
            if (!json.optBoolean("success", false)) {
                return@withContext Result.failure(IllegalStateException(friendlyError(json.optString("error", ""), status)))
            }
            val payloadAny = json.opt("payload")
            val payload: JSONObject = when (payloadAny) {
                is JSONObject -> payloadAny
                is String -> JSONObject(payloadAny)
                else -> return@withContext Result.failure(IllegalStateException("分享内容为空"))
            }
            val preview = parsePayload(payload)
            if (preview.isEmpty) Result.failure(IllegalStateException("这个分享码里没有可导入的课表"))
            else Result.success(preview)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("网络错误：${e.message ?: e.javaClass.simpleName}"))
        }
    }

    private fun readResponse(conn: HttpURLConnection): Pair<Int, String> {
        return try {
            val status = conn.responseCode
            val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            val buf = ByteArrayOutputStream()
            stream?.use { input ->
                val chunk = ByteArray(8192)
                var total = 0
                while (true) {
                    val n = input.read(chunk)
                    if (n < 0) break
                    total += n
                    if (total > 4 * MAX_BYTES) throw IllegalStateException("响应过大")
                    buf.write(chunk, 0, n)
                }
            }
            status to buf.toString("UTF-8")
        } finally {
            conn.disconnect()
        }
    }

    private fun friendlyError(serverError: String, status: Int): String = when {
        serverError.contains("not found", true) || serverError.contains("expired", true) || status == 404 -> "分享码不存在或已过期"
        serverError.contains("too large", true) || status == 413 -> "内容超过服务器限制"
        serverError.contains("rate", true) || status == 429 -> "操作太频繁，请稍后再试"
        serverError.isNotBlank() -> serverError
        else -> "服务器错误（HTTP $status）"
    }

    // ------------------------------------------------------------------ 解析 / 应用

    fun parsePayload(root: JSONObject): Preview {
        if (root.optInt("v", 0) >= 2 || root.has("timetables")) return parseV2(root)
        return parseV1(root)
    }

    private fun parseV2(root: JSONObject): Preview {
        val timePlans = ArrayList<TimePlan>()
        root.optJSONArray("timePlans")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o -> runCatching { TimePlan.fromJsonObject(o) }.getOrNull()?.takeIf { ScheduleManager.isValidPlanName(it.name) }?.let(timePlans::add) }
            }
        }
        val timetables = ArrayList<Timetable>()
        root.optJSONArray("timetables")?.let { arr ->
            for (i in 0 until arr.length()) {
                arr.optJSONObject(i)?.let { o -> runCatching { Timetable.fromJsonObject(o) }.getOrNull()?.let(timetables::add) }
            }
        }
        val shortNames = LinkedHashMap<String, String>()
        root.optJSONObject("shortNames")?.let { o ->
            o.keys().forEach { k -> val v = o.optString(k, ""); if (k.isNotBlank() && v.isNotBlank() && k.length <= 40 && v.length <= 10) shortNames[k] = v }
        }
        return Preview(
            timePlans = timePlans.take(50),
            timetables = timetables.take(50),
            shortNames = shortNames,
            startDate = DateUtils.parseDate(root.optString("startDate", "")),
            legacy = false,
            created = root.optString("created", "").ifBlank { null }
        )
    }

    /** 旧版格式：prefs + plans{relPath: json 文本} */
    private fun parseV1(root: JSONObject): Preview {
        val timePlans = ArrayList<TimePlan>()
        val legacyPlans = ArrayList<LegacySchedulePlan>()
        root.optJSONObject("plans")?.let { plans ->
            plans.keys().forEach { rel ->
                val text = plans.optString(rel, "")
                if (text.isBlank()) return@forEach
                val norm = rel.replace('\\', '/')
                when {
                    norm.startsWith("time/") -> runCatching { TimePlan.fromJson(text) }.getOrNull()?.takeIf { ScheduleManager.isValidPlanName(it.name) }?.let(timePlans::add)
                    norm.startsWith("course/") -> runCatching { LegacySchedulePlan.fromJson(text) }.getOrNull()?.let(legacyPlans::add)
                }
            }
        }
        val prefs = root.optJSONObject("prefs")
        val shortNames = Prefs.parseShortNames(prefs?.optString("custom_short_names", null))
        val startDate = prefs?.optString("start_date", "")?.let { DateUtils.parseDate(it) }
        val timetables = if (legacyPlans.isEmpty()) emptyList() else listOf(Timetable.fromLegacy(legacyPlans, "导入的课表"))
        return Preview(timePlans, timetables, shortNames, startDate, legacy = true, created = null)
    }

    /**
     * 应用导入内容。
     * @param replace true = 用导入内容替换现有课表与时间方案；false = 合并（同名时间方案覆盖，课表按 id 覆盖否则追加）
     */
    fun applyPreview(ctx: Context, manager: ScheduleManager, preview: Preview, replace: Boolean, importStartDate: Boolean) {
        // 合并模式下给重名但不同 id 的课表加后缀，避免看起来像重复
        val existing = manager.getTimetables()
        val renamed = if (replace) preview.timetables else preview.timetables.map { tt ->
            val clash = existing.any { it.name == tt.name && it.id != tt.id }
            if (clash) tt.copy(name = uniqueName(tt.name, existing.map { it.name })) else tt
        }
        // 课表引用的时间方案不存在时，兜底指到导入的第一个或现有第一个
        val planNames = (preview.timePlans.map { it.name } + if (replace) emptyList() else manager.getTimePlans().map { it.name }).toSet()
        val fixed = renamed.map { tt ->
            if (tt.timePlanName in planNames) tt
            else tt.copy(timePlanName = preview.timePlans.firstOrNull()?.name ?: manager.getTimePlans().firstOrNull()?.name ?: tt.timePlanName)
        }
        manager.importData(preview.timePlans, fixed, preview.shortNames, replace)
        if (importStartDate && preview.startDate != null) {
            Prefs.setStartDate(ctx, DateUtils.mondayOf(preview.startDate))
        }
        ScheduleManager.invalidateCache()
    }

    private fun uniqueName(base: String, taken: List<String>): String {
        var i = 2
        while ("$base $i" in taken) i++
        return "$base $i"
    }

    // ------------------------------------------------------------------ 本地记录

    private fun saveShareCodeRecord(ctx: Context, code: String) {
        val sp = ctx.getSharedPreferences(RECORDS_PREF, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(sp.getString("records", "[]")) }.getOrDefault(JSONArray())
        val now = java.time.LocalDateTime.now().withNano(0).toString().replace('T', ' ')
        val list = ArrayList<JSONObject>()
        list.add(JSONObject().put("code", code).put("createdAt", now))
        for (i in 0 until arr.length()) arr.optJSONObject(i)?.let(list::add)
        val out = JSONArray()
        list.take(10).forEach { out.put(it) }
        sp.edit().putString("records", out.toString()).apply()
    }

    fun getShareCodeRecords(ctx: Context): List<ShareCodeRecord> {
        val sp = ctx.getSharedPreferences(RECORDS_PREF, Context.MODE_PRIVATE)
        val arr = runCatching { JSONArray(sp.getString("records", "[]")) }.getOrDefault(JSONArray())
        val cutoff = java.time.LocalDateTime.now().minusDays(EXPIRE_DAYS.toLong())
        val result = ArrayList<ShareCodeRecord>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val created = o.optString("createdAt", "")
            val time = runCatching { java.time.LocalDateTime.parse(created.replace(' ', 'T')) }.getOrNull()
            if (time != null && time.isBefore(cutoff)) continue
            result.add(ShareCodeRecord(o.optString("code", ""), created))
        }
        return result
    }
}
