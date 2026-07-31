package com.enenhhh.smartschedule.data

import android.content.Context
import com.enenhhh.smartschedule.models.SchedulePlan
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.models.TimePeriod
import org.json.JSONObject
import java.io.File

class ScheduleManager(private val context: Context) {
    // 改回外部存储路径，方便用户调试
    private val plansDir = File(context.getExternalFilesDir(null), "plans")
    private val timeDir = File(plansDir, "time")
    private val courseDir = File(plansDir, "course")

    init {
        if (!timeDir.exists()) timeDir.mkdirs()
        if (!courseDir.exists()) courseDir.mkdirs()
    }

    /**
     * 方案文件和课程简写会被首页每秒读一次、课表页每个格子读一次，
     * 每次都走磁盘 + JSON 解析开销很大，这里做进程级缓存，写入时失效。
     */
    companion object {
        @Volatile private var cachedTimePlans: List<TimePlan>? = null
        @Volatile private var cachedSchedulePlans: List<SchedulePlan>? = null
        @Volatile private var cachedShortNames: Map<String, String>? = null

        /** 磁盘上的方案 / 简写被外部改动（导入、恢复）后必须调用 */
        fun invalidateCache() {
            cachedTimePlans = null
            cachedSchedulePlans = null
            cachedShortNames = null
        }
    }

    fun invalidateCache() = Companion.invalidateCache()

    // 获取当前日期对应的课程方案
    fun getActiveSchedule(isOdd: Boolean, dayIndex: Int): SchedulePlan? {
        val dayKey = "${if (isOdd) "odd" else "even"}_$dayIndex"
        return getSchedulePlans().find { it.bindingDays.contains(dayKey) }
    }

    // 3.0 的迁移逻辑已移除：它只搬课程名，节次时间会被替换成一张写死的默认时间表，
    // 迁完仍需手工重调，收益低于直接重新录入。

    // --- 时间方案管理 ---
    fun saveTimePlan(plan: TimePlan) {
        File(timeDir, "${plan.name}.json").writeText(plan.toJson())
        invalidateCache()
    }

    fun getTimePlans(): List<TimePlan> {
        cachedTimePlans?.let { return it }
        // 单个文件损坏不应该让整页崩溃，跳过读不出来的
        val loaded = timeDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f -> runCatching { TimePlan.fromJson(f.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()
        cachedTimePlans = loaded
        return loaded
    }

    fun deleteTimePlan(name: String) {
        File(timeDir, "$name.json").delete()
        invalidateCache()
    }

    fun calculateAutoFill(startTime: String, lessonMinutes: Int, breakMinutes: Int, count: Int): List<TimePeriod> {
        val result = mutableListOf<TimePeriod>()
        var currentStart = timeToMinutes(startTime)
        for (i in 0 until count) {
            val end = currentStart + lessonMinutes
            result.add(TimePeriod(minutesToTime(currentStart), minutesToTime(end)))
            currentStart = end + breakMinutes
        }
        return result
    }
// --- 课程方案管理 ---
    fun saveSchedulePlan(plan: SchedulePlan) {
        File(courseDir, "${plan.name}.json").writeText(plan.toJson())
        invalidateCache()
    }

    fun getSchedulePlans(): List<SchedulePlan> {
        cachedSchedulePlans?.let { return it }
        val loaded = courseDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f -> runCatching { SchedulePlan.fromJson(f.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()
        cachedSchedulePlans = loaded
        return loaded
    }

    fun deleteSchedulePlan(name: String) {
        File(courseDir, "$name.json").delete()
        invalidateCache()
    }

    fun copySchedulePlan(sourceName: String, newName: String) {
        val sourceFile = File(courseDir, "$sourceName.json")
        if (sourceFile.exists()) {
            val plan = SchedulePlan.fromJson(sourceFile.readText())
            plan.name = newName
            saveSchedulePlan(plan)
        }
    }

    fun copyTimePlan(sourceName: String, newName: String) {
        val sourceFile = File(timeDir, "$sourceName.json")
        if (sourceFile.exists()) {
            val plan = TimePlan.fromJson(sourceFile.readText())
            plan.name = newName
            saveTimePlan(plan)
        }
    }
    private fun timeToMinutes(time: String): Int {
        val p = time.split(":")
        if (p.size < 2) return 0
        return p[0].toInt() * 60 + p[1].toInt()
    }

    private fun minutesToTime(total: Int): String {
        val h = (total / 60) % 24
        val m = total % 60
        return "%02d:%02d".format(h, m)
    }

    // --- 课程名简写处理 ---
    fun getShortName(fullName: String): String {
        if (fullName.isBlank()) return ""
        val custom = getCustomShortNames()[fullName]
        if (!custom.isNullOrBlank()) return custom
        return if (fullName.length > 2) fullName.substring(0, 2) else fullName
    }

    private fun getCustomShortNames(): Map<String, String> {
        cachedShortNames?.let { return it }
        val jsonStr = context.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("custom_short_names", "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
        } catch (_: Exception) {
        }
        cachedShortNames = map
        return map
    }
}