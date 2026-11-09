package com.enenhhh.smartschedule.util

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.enenhhh.smartschedule.data.Homework
import com.enenhhh.smartschedule.data.HomeworkRepository
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.theme.ThemeState
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 本地备份：单个 .json 文件（v2），包含课表、时间方案、简称、开学日期、作业和设置。
 * 也能读取旧版「备份文件夹」（prefs.json + homework.json + plans/…）。
 */
object BackupHelper {

    const val MAX_FILE_BYTES = 8 * 1024 * 1024
    private val SETTING_KEYS = setOf(
        "start_date", "keep_alive", "persistent_tomorrow", "reminder_enabled", "reminder_hour", "reminder_min",
        "weather_enabled", "weather_hour", "weather_min", "weather_city", "dark_mode", "dynamic_color",
        "auto_check_update", "theme_color", "palette_style", "contrast_level", "amoled_black", "glass_enabled",
        "glass_intensity", "glass_liquid", "animated_background", "reduce_motion", "custom_seed_color", "show_weekend", "update_source_v2"
    )

    data class BackupPreview(
        val schedule: ShareCodeHelper.Preview,
        val homework: List<Homework>,
        val settings: JSONObject?,
        val created: String?
    )

    fun suggestedFileName(): String =
        "智能课表备份_${SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())}.json"

    // ------------------------------------------------------------------ 导出

    fun buildBackup(ctx: Context, manager: ScheduleManager): JSONObject {
        val root = ShareCodeHelper.buildPayload(ctx, manager, null, includeStartDate = true)
        root.put("kind", "backup")
        root.put("homework", JSONArray().apply { HomeworkRepository(ctx).getAll().forEach { put(it.toJson()) } })
        val settings = JSONObject()
        Prefs.getPrefs(ctx).all.forEach { (k, v) -> if (k in SETTING_KEYS && v != null) settings.put(k, v) }
        root.put("settings", settings)
        return root
    }

    fun writeBackup(ctx: Context, manager: ScheduleManager, uri: Uri) {
        val text = buildBackup(ctx, manager).toString(2)
        ctx.contentResolver.openOutputStream(uri, "wt")?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            ?: throw IllegalStateException("无法写入所选位置")
    }

    // ------------------------------------------------------------------ 读取

    fun readBackupFile(ctx: Context, uri: Uri): BackupPreview {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { input ->
            val buf = ByteArrayOutputStream()
            val chunk = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val n = input.read(chunk)
                if (n < 0) break
                total += n
                if (total > MAX_FILE_BYTES) throw IllegalStateException("文件太大（超过 ${MAX_FILE_BYTES / 1024 / 1024} MB），不像是课表备份")
                buf.write(chunk, 0, n)
            }
            buf.toByteArray()
        } ?: throw IllegalStateException("无法读取文件")
        val root = runCatching { JSONObject(String(bytes, Charsets.UTF_8)) }.getOrElse { throw IllegalStateException("不是有效的 JSON 备份文件") }
        return parseBackup(root)
    }

    fun parseBackup(root: JSONObject): BackupPreview {
        val schedule = ShareCodeHelper.parsePayload(root)
        val homework = ArrayList<Homework>()
        when (val hw = root.opt("homework")) {
            is JSONArray -> for (i in 0 until hw.length()) hw.optJSONObject(i)?.let { o -> runCatching { Homework.fromJson(o) }.getOrNull()?.let(homework::add) }
            is String -> runCatching { JSONArray(hw) }.getOrNull()?.let { arr -> for (i in 0 until arr.length()) arr.optJSONObject(i)?.let { o -> runCatching { Homework.fromJson(o) }.getOrNull()?.let(homework::add) } }
        }
        val settings = root.optJSONObject("settings") ?: root.optJSONObject("prefs")
        return BackupPreview(schedule, homework, settings, root.optString("created", "").ifBlank { null })
    }

    /** 旧版备份文件夹：prefs.json、homework.json、plans/time/ 与 plans/course/ 下的 json 文件 */
    fun readLegacyFolder(ctx: Context, treeUri: Uri): BackupPreview {
        val tree = DocumentFile.fromTreeUri(ctx, treeUri) ?: throw IllegalStateException("无法访问该文件夹")
        fun readDoc(doc: DocumentFile?): String? = doc?.takeIf { it.isFile && it.length() <= MAX_FILE_BYTES }?.let { d ->
            ctx.contentResolver.openInputStream(d.uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
        }
        val root = JSONObject()
        readDoc(tree.findFile("prefs.json"))?.let { runCatching { root.put("prefs", JSONObject(it)) } }
        readDoc(tree.findFile("homework.json"))?.let { root.put("homework", it) }
        val plans = JSONObject()
        val plansDir = tree.findFile("plans")
        listOf("time", "course", "timetable").forEach { sub ->
            plansDir?.findFile(sub)?.listFiles()?.forEach { f ->
                if (f.isFile && f.name?.endsWith(".json") == true) readDoc(f)?.let { plans.put("$sub/${f.name}", it) }
            }
        }
        // 新版文件夹里可能已经是 timetable 格式
        val timetables = JSONArray()
        plans.keys().asSequence().filter { it.startsWith("timetable/") }.forEach { k ->
            runCatching { JSONObject(plans.getString(k)) }.getOrNull()?.let { timetables.put(it) }
        }
        if (timetables.length() > 0) {
            root.put("v", 2)
            root.put("timetables", timetables)
            val timePlans = JSONArray()
            plans.keys().asSequence().filter { it.startsWith("time/") }.forEach { k ->
                runCatching { JSONObject(plans.getString(k)) }.getOrNull()?.let { timePlans.put(it) }
            }
            root.put("timePlans", timePlans)
            root.optJSONObject("prefs")?.optString("start_date", "")?.takeIf { it.isNotBlank() }?.let { root.put("startDate", it) }
            root.optJSONObject("prefs")?.optString("custom_short_names", "")?.takeIf { it.isNotBlank() }?.let { sn ->
                root.put("shortNames", JSONObject().apply { Prefs.parseShortNames(sn).forEach { (k, v) -> put(k, v) } })
            }
        } else {
            root.put("plans", plans)
        }
        return parseBackup(root)
    }

    // ------------------------------------------------------------------ 恢复

    fun restore(
        ctx: Context,
        manager: ScheduleManager,
        preview: BackupPreview,
        replace: Boolean,
        includeHomework: Boolean,
        includeSettings: Boolean
    ) {
        if (!preview.schedule.isEmpty) {
            ShareCodeHelper.applyPreview(ctx, manager, preview.schedule, replace, importStartDate = true)
        }
        if (includeHomework && preview.homework.isNotEmpty()) {
            val repo = HomeworkRepository(ctx)
            if (replace) repo.replaceAll(preview.homework) else repo.mergeAll(preview.homework)
        }
        if (includeSettings && preview.settings != null) {
            val editor = Prefs.getPrefs(ctx).edit()
            val s = preview.settings
            s.keys().forEach { k ->
                if (k !in SETTING_KEYS) return@forEach
                when (val v = s.opt(k)) {
                    is Boolean -> editor.putBoolean(k, v)
                    is Int -> editor.putInt(k, v)
                    is Long -> editor.putLong(k, v)
                    is Double -> editor.putFloat(k, v.toFloat())
                    is Float -> editor.putFloat(k, v)
                    is String -> editor.putString(k, v)
                }
            }
            editor.apply()
            ThemeState.load(ctx)
        }
        ScheduleManager.invalidateCache()
        HomeworkRepository.bumpVersion()
        runCatching { NotificationHelper.refreshAll(ctx) }
    }
}
