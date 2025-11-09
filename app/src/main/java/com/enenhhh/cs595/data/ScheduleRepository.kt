package com.enenhhh.smartschedule.data

import android.content.Context
import com.enenhhh.smartschedule.util.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class ScheduleRepository(private val context: Context) {

    private val odd = WeekSchedule()
    private val even = WeekSchedule()

    // 可分享配置（保存到 schedule.json）
    private val shortNames: MutableMap<String, String> = mutableMapOf()
    private val splitPositions: MutableList<Int> = mutableListOf(1, 5)
    private var splitSeparator: String = "｜"

    private val file: File by lazy {
        File(context.getExternalFilesDir(null), "schedule.json")
    }

    init {
        if (file.exists()) {
            load()
        } // 否则首次由用户导入/编辑
    }

    fun getDayEntry(day: String, oddWeek: Boolean): DayEntry {
        val ws = if (oddWeek) odd else even
        val entry = ws.getOrCreate(day)
        ensureCourseSize(entry)
        return DayEntry(
            morningRead = entry.morningRead,
            courses = entry.courses.toMutableList()
        )
    }

    fun updateDayEntry(day: String, oddWeek: Boolean, morningRead: String?, courses: List<String>) {
        val ws = if (oddWeek) odd else even
        val entry = ws.getOrCreate(day)
        entry.morningRead = morningRead?.ifBlank { null }
        entry.courses.clear()
        val max = Prefs.getCourseCount(context)
        courses.take(max).forEach { course ->
            entry.courses.add(course)
        }
        ensureCourseSize(entry)
    }

    // 简写配置（外部使用）
    fun getShortNames(): Map<String, String> = shortNames.toMap()
    fun setShortNames(map: Map<String, String>) {
        shortNames.clear()
        shortNames.putAll(map)
        persist()
    }

    fun getSplitPositions(): List<Int> = splitPositions.toList()
    fun setSplitPositions(positions: List<Int>) {
        splitPositions.clear()
        splitPositions.addAll(positions.filter { it > 0 }.sorted())
        persist()
    }

    fun getSplitSeparator(): String = splitSeparator
    fun setSplitSeparator(sep: String) {
        splitSeparator = if (sep.isBlank()) "｜" else sep
        persist()
    }

    fun persist() {
        val root = JSONObject()
        root.put("odd", weekToJson(odd))
        root.put("even", weekToJson(even))

        val cfg = JSONObject().apply {
            // 供分享的可见配置
            val sn = JSONObject()
            shortNames.forEach { (k, v) -> sn.put(k, v) }
            put("shortNames", sn)

            val pos = JSONArray()
            splitPositions.forEach { pos.put(it) }
            put("splitPositions", pos)

            put("splitSeparator", splitSeparator)
            put("courseCount", Prefs.getCourseCount(context))
        }
        root.put("config", cfg)

        file.writeText(root.toString(2))
    }

    fun importFromJson(jsonStr: String) {
        val root = JSONObject(jsonStr)
        jsonToWeek(root.optJSONObject("odd"), odd)
        jsonToWeek(root.optJSONObject("even"), even)
        readConfig(root.optJSONObject("config"))
        // 统一课程长度
        val courseCount = Prefs.getCourseCount(context)
        odd.days.values.forEach { ensureCourseSize(it, courseCount) }
        even.days.values.forEach { ensureCourseSize(it, courseCount) }
        persist()
    }

    private fun load() {
        val content = file.readText()
        val root = JSONObject(content)
        jsonToWeek(root.optJSONObject("odd"), odd)
        jsonToWeek(root.optJSONObject("even"), even)
        readConfig(root.optJSONObject("config"))

        // 统一长度
        val courseCount = Prefs.getCourseCount(context)
        odd.days.values.forEach { ensureCourseSize(it, courseCount) }
        even.days.values.forEach { ensureCourseSize(it, courseCount) }
    }

    private fun readConfig(cfg: JSONObject?) {
        if (cfg == null) return
        // 课程数
        val count = cfg.optInt("courseCount", -1)
        if (count > 0) {
            Prefs.setCourseCount(context, count)
        }
        // 简写
        shortNames.clear()
        cfg.optJSONObject("shortNames")?.let { sn ->
            val it = sn.keys()
            while (it.hasNext()) {
                val k = it.next()
                shortNames[k] = sn.optString(k)
            }
        }
        // 分割
        splitPositions.clear()
        cfg.optJSONArray("splitPositions")?.let { arr ->
            for (i in 0 until arr.length()) {
                val v = arr.optInt(i, -1)
                if (v > 0) splitPositions.add(v)
            }
        }
        if (splitPositions.isEmpty()) {
            splitPositions.addAll(listOf(1, 5))
        }
        splitSeparator = cfg.optString("splitSeparator", "｜").ifBlank { "｜" }
    }

    private fun ensureCourseSize(entry: DayEntry, targetSize: Int = Prefs.getCourseCount(context)) {
        while (entry.courses.isNotEmpty() && entry.courses.last().isBlank()) {
            entry.courses.removeAt(entry.courses.size - 1)
        }
        while (entry.courses.size < targetSize) {
            entry.courses.add("")
        }
        while (entry.courses.size > targetSize) {
            entry.courses.removeAt(entry.courses.size - 1)
        }
    }

    private fun weekToJson(week: WeekSchedule): JSONObject {
        val obj = JSONObject()
        week.days.forEach { (day, entry) ->
            val d = JSONObject()
            d.put("morningRead", entry.morningRead ?: JSONObject.NULL)
            val arr = JSONArray()
            entry.courses.forEach { arr.put(it) }
            d.put("courses", arr)
            obj.put(day, d)
        }
        return obj
    }

    private fun jsonToWeek(obj: JSONObject?, week: WeekSchedule) {
        week.days.clear()
        if (obj == null) return
        val keys = obj.keys()
        while (keys.hasNext()) {
            val day = keys.next()
            val d = obj.optJSONObject(day) ?: continue
            val entry = DayEntry()
            entry.morningRead = d.optString("morningRead").takeIf { it != "null" && it.isNotBlank() }
            val arr = d.optJSONArray("courses") ?: JSONArray()
            for (i in 0 until arr.length()) {
                entry.courses.add(arr.optString(i))
            }
            week.days[day] = entry
        }
    }

    // 课程简称：仅使用用户自定义；默认回退首字
    fun shortName(name: String): String {
        if (shortNames.containsKey(name)) {
            return shortNames[name] ?: ""
        }
        return name.firstOrNull()?.toString() ?: ""
    }

    fun buildTomorrowSummary(nowMillis: Long = System.currentTimeMillis()): String {
        val (label, mr, list) = buildTomorrowData(nowMillis)
        val mrPart = if (!mr.isNullOrBlank()) shortName(mr) else ""
        val shorts = list.map { shortName(it) }

        val parts = mutableListOf<String>()
        if (mrPart.isNotBlank()) parts.add(mrPart)

        var startIndex = 0
        splitPositions.forEach { position ->
            if (position > startIndex && position <= shorts.size) {
                val part = shorts.subList(startIndex, position).joinToString(" ")
                if (part.isNotBlank()) parts.add(part)
                startIndex = position
            }
        }
        if (startIndex < shorts.size) {
            val part = shorts.subList(startIndex, shorts.size).joinToString(" ")
            if (part.isNotBlank()) parts.add(part)
        }

        return if (parts.isEmpty()) {
            "明天（$label）：无课"
        } else {
            "明天（$label）：${parts.joinToString(splitSeparator)}"
        }
    }

    data class TomorrowData(val label: String, val morning: String?, val courses: List<String>)

    private fun buildTomorrowData(nowMillis: Long): TomorrowData {
        val cal = java.util.Calendar.getInstance().apply { timeInMillis = nowMillis }
        cal.add(java.util.Calendar.DAY_OF_YEAR, 1)
        val weekday = cal.get(java.util.Calendar.DAY_OF_WEEK) // 1=周日,2=周一...
        val label = when (weekday) {
            java.util.Calendar.MONDAY -> "周一"
            java.util.Calendar.TUESDAY -> "周二"
            java.util.Calendar.WEDNESDAY -> "周三"
            java.util.Calendar.THURSDAY -> "周四"
            java.util.Calendar.FRIDAY -> "周五"
            java.util.Calendar.SATURDAY -> "周六"
            else -> "周日"
        }

        val isOddWeek = com.enenhhh.smartschedule.util.DateUtils.isOddWeekRelativeTo(
            cal.timeInMillis,
            com.enenhhh.smartschedule.util.Prefs.getStartDate(context)
        )
        return if (label in listOf("周一", "周二", "周三", "周四", "周五")) {
            val entry = getDayEntry(label, isOddWeek)
            TomorrowData(label, entry.morningRead, entry.courses)
        } else {
            TomorrowData(label, null, emptyList())
        }
    }
}