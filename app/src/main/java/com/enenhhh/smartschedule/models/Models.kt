package com.enenhhh.smartschedule.models

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/**
 * 时间方案：一天有几节课、每节课几点到几点，以及晨读时段。
 */
data class TimePlan(
    val name: String,
    val periods: List<TimePeriod> = emptyList(),
    val morningStart: String = "07:30",
    val morningEnd: String = "08:00"
) {
    fun toJson(): String = toJsonObject().toString()

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("name", name)
        root.put("morningStart", morningStart)
        root.put("morningEnd", morningEnd)
        val array = JSONArray()
        periods.forEach { array.put(it.toJSONObject()) }
        root.put("periods", array)
        return root
    }

    companion object {
        fun fromJson(jsonStr: String): TimePlan = fromJsonObject(JSONObject(jsonStr))

        fun fromJsonObject(root: JSONObject): TimePlan {
            val name = root.getString("name")
            val array = root.optJSONArray("periods") ?: JSONArray()
            val periods = ArrayList<TimePeriod>(array.length())
            for (i in 0 until array.length()) {
                periods.add(TimePeriod.fromJSONObject(array.getJSONObject(i)))
            }
            return TimePlan(
                name = name,
                periods = periods,
                morningStart = root.optString("morningStart", "07:30"),
                morningEnd = root.optString("morningEnd", "08:00")
            )
        }

        /** 常见中学作息：8:00 开始，每节 40 分钟，课间 10 分钟，8 节，午休在第 4 节后 */
        fun defaultPlan(name: String = "默认作息"): TimePlan = TimePlan(
            name = name,
            periods = generate("08:00", 40, 10, 8, lunchAfter = 4, lunchMinutes = 120)
        )

        /**
         * 按规则批量生成节次时间。
         * [lunchAfter] 第几节之后插入午休（0 表示不插入），[lunchMinutes] 午休长度。
         */
        fun generate(
            firstStart: String,
            lessonMinutes: Int,
            breakMinutes: Int,
            count: Int,
            lunchAfter: Int = 0,
            lunchMinutes: Int = 0
        ): List<TimePeriod> {
            val result = ArrayList<TimePeriod>(count)
            var cur = timeToMinutes(firstStart)
            for (i in 1..count) {
                val end = cur + lessonMinutes
                result.add(TimePeriod(minutesToTime(cur), minutesToTime(end)))
                cur = end + breakMinutes
                if (lunchAfter > 0 && i == lunchAfter) cur += lunchMinutes
            }
            return result
        }

        fun timeToMinutes(time: String): Int {
            val p = time.split(":")
            if (p.size < 2) return 0
            return (p[0].trim().toIntOrNull() ?: 0) * 60 + (p[1].trim().toIntOrNull() ?: 0)
        }

        fun minutesToTime(total: Int): String {
            val t = ((total % (24 * 60)) + 24 * 60) % (24 * 60)
            return "%02d:%02d".format(t / 60, t % 60)
        }
    }
}

data class TimePeriod(
    val start: String, // HH:mm
    val end: String    // HH:mm
) {
    fun toJSONObject(): JSONObject = JSONObject().put("start", start).put("end", end)

    val startMinutes: Int get() = TimePlan.timeToMinutes(start)
    val endMinutes: Int get() = TimePlan.timeToMinutes(end)

    companion object {
        fun fromJSONObject(obj: JSONObject): TimePeriod =
            TimePeriod(obj.optString("start", "08:00"), obj.optString("end", "08:40"))
    }
}

/**
 * 旧版（≤4.0）的「课程方案」：一份方案只描述一天的课，再通过 bindingDays
 * （"odd_1"、"even_5" …）绑定到单/双周的某个星期。
 * 5.0 起不再使用，仅用于读取旧数据做迁移，以及兼容旧分享码 / 旧备份。
 */
data class LegacySchedulePlan(
    val name: String,
    val timePlanName: String,
    val courses: List<String> = emptyList(),
    val morningRead: String = "",
    val isMorningReadEnabled: Boolean = true,
    val bindingDays: List<String> = emptyList()
) {
    companion object {
        fun fromJson(jsonStr: String): LegacySchedulePlan = fromJsonObject(JSONObject(jsonStr))

        fun fromJsonObject(root: JSONObject): LegacySchedulePlan {
            val cArray = root.optJSONArray("courses") ?: JSONArray()
            val dArray = root.optJSONArray("days") ?: JSONArray()
            return LegacySchedulePlan(
                name = root.optString("name", "课程方案"),
                timePlanName = root.optString("timePlanName", ""),
                courses = List(cArray.length()) { cArray.optString(it, "") },
                morningRead = root.optString("morningRead", ""),
                isMorningReadEnabled = root.optBoolean("mrEnabled", true),
                bindingDays = List(dArray.length()) { dArray.optString(it, "") }
            )
        }
    }
}

/**
 * 一天的课：
 *  - [courses]      单周（或每周相同时）的课程，下标 = 节次 - 1，空串表示没课
 *  - [evenCourses]  双周课程；为 null 表示这一天单双周相同
 *  - [morningRead]  晨读内容，空串表示这一天没有晨读
 */
data class DaySchedule(
    val courses: List<String> = emptyList(),
    val evenCourses: List<String>? = null,
    val morningRead: String = "",
    val evenMorningRead: String? = null
) {
    val alternates: Boolean get() = evenCourses != null

    fun coursesFor(isOdd: Boolean): List<String> =
        if (isOdd || evenCourses == null) courses else evenCourses

    fun morningReadFor(isOdd: Boolean): String =
        if (isOdd || evenMorningRead == null) morningRead else evenMorningRead

    fun courseAt(isOdd: Boolean, period: Int): String = coursesFor(isOdd).getOrNull(period).orEmpty()

    fun isEmpty(): Boolean =
        courses.all { it.isBlank() } && (evenCourses?.all { it.isBlank() } != false) &&
            morningRead.isBlank() && evenMorningRead.isNullOrBlank()

    fun withCourse(isOdd: Boolean, period: Int, name: String): DaySchedule {
        fun List<String>.set(i: Int, v: String): List<String> {
            val list = ArrayList(this)
            while (list.size <= i) list.add("")
            list[i] = v
            return list
        }
        return if (isOdd || evenCourses == null) copy(courses = courses.set(period, name))
        else copy(evenCourses = evenCourses.set(period, name))
    }

    fun toJsonObject(): JSONObject {
        val o = JSONObject()
        o.put("courses", JSONArray(courses))
        if (evenCourses != null) o.put("even", JSONArray(evenCourses))
        o.put("morningRead", morningRead)
        if (evenMorningRead != null) o.put("evenMorningRead", evenMorningRead)
        return o
    }

    companion object {
        fun fromJsonObject(o: JSONObject?): DaySchedule {
            if (o == null) return DaySchedule()
            fun arr(key: String): List<String>? {
                val a = o.optJSONArray(key) ?: return null
                return List(a.length()) { a.optString(it, "") }
            }
            return DaySchedule(
                courses = arr("courses") ?: emptyList(),
                evenCourses = arr("even"),
                morningRead = o.optString("morningRead", ""),
                evenMorningRead = if (o.has("evenMorningRead")) o.optString("evenMorningRead", "") else null
            )
        }
    }
}

/**
 * 5.0 的课表：一张表覆盖整周（周一到周日），自带单双周差异，绑定一个时间方案。
 * 用户通常只需要一张课表；换学期可以再建一张并切换「使用中」。
 */
data class Timetable(
    val id: String = newId(),
    val name: String = "我的课表",
    val timePlanName: String = "",
    val days: List<DaySchedule> = List(7) { DaySchedule() },
    val createdAt: Long = System.currentTimeMillis()
) {
    /** [dayIndex] 1 = 周一 … 7 = 周日 */
    fun day(dayIndex: Int): DaySchedule = days.getOrElse(dayIndex - 1) { DaySchedule() }

    fun withDay(dayIndex: Int, day: DaySchedule): Timetable {
        val list = ArrayList(days)
        while (list.size < 7) list.add(DaySchedule())
        list[dayIndex - 1] = day
        return copy(days = list)
    }

    /** 这张课表里出现过的所有课程名（去重，按出现次数排序） */
    fun allCourseNames(): List<String> {
        val counts = LinkedHashMap<String, Int>()
        days.forEach { d ->
            (d.courses + (d.evenCourses ?: emptyList())).forEach { c ->
                if (c.isNotBlank()) counts[c] = (counts[c] ?: 0) + 1
            }
        }
        return counts.entries.sortedByDescending { it.value }.map { it.key }
    }

    fun hasAnyEvenVariant(): Boolean = days.any { it.alternates }

    /** 周六 / 周日是否有课（决定课表页要不要显示周末列） */
    fun weekendUsed(): Boolean = !day(6).isEmpty() || !day(7).isEmpty()

    fun toJson(): String = toJsonObject().toString()

    fun toJsonObject(): JSONObject {
        val root = JSONObject()
        root.put("v", 1)
        root.put("id", id)
        root.put("name", name)
        root.put("timePlanName", timePlanName)
        root.put("createdAt", createdAt)
        val arr = JSONArray()
        days.forEach { arr.put(it.toJsonObject()) }
        root.put("days", arr)
        return root
    }

    companion object {
        fun newId(): String = "tt_" + UUID.randomUUID().toString().substring(0, 8)

        fun fromJson(jsonStr: String): Timetable = fromJsonObject(JSONObject(jsonStr))

        fun fromJsonObject(root: JSONObject): Timetable {
            val arr = root.optJSONArray("days") ?: JSONArray()
            val days = List(7) { i -> DaySchedule.fromJsonObject(arr.optJSONObject(i)) }
            return Timetable(
                id = root.optString("id", newId()),
                name = root.optString("name", "我的课表"),
                timePlanName = root.optString("timePlanName", ""),
                days = days,
                createdAt = root.optLong("createdAt", System.currentTimeMillis())
            )
        }

        /**
         * 把旧版「一天一份」的课程方案合并成一张整周课表。
         * 同一天同时存在 odd_N 和 even_N 两份不同的方案时，自动记成单双周差异。
         */
        fun fromLegacy(plans: List<LegacySchedulePlan>, name: String = "我的课表"): Timetable {
            if (plans.isEmpty()) return Timetable(name = name)
            val timePlanName = plans.groupingBy { it.timePlanName }.eachCount()
                .maxByOrNull { it.value }?.key ?: plans.first().timePlanName
            val oddByDay = HashMap<Int, LegacySchedulePlan>()
            val evenByDay = HashMap<Int, LegacySchedulePlan>()
            plans.forEach { p ->
                p.bindingDays.forEach { key ->
                    val parts = key.split("_")
                    if (parts.size == 2) {
                        val d = parts[1].toIntOrNull() ?: return@forEach
                        if (d !in 1..7) return@forEach
                        when (parts[0]) {
                            "odd" -> oddByDay.putIfAbsent(d, p)
                            "even" -> evenByDay.putIfAbsent(d, p)
                        }
                    }
                }
            }
            val days = List(7) { i ->
                val d = i + 1
                val odd = oddByDay[d]
                val even = evenByDay[d]
                when {
                    odd == null && even == null -> DaySchedule()
                    odd != null && (even == null || even === odd || even.courses == odd.courses && even.morningRead == odd.morningRead) ->
                        DaySchedule(
                            courses = odd.courses,
                            morningRead = if (odd.isMorningReadEnabled) odd.morningRead else ""
                        )
                    odd == null -> DaySchedule(
                        courses = even!!.courses,
                        morningRead = if (even.isMorningReadEnabled) even.morningRead else ""
                    )
                    else -> DaySchedule(
                        courses = odd.courses,
                        evenCourses = even!!.courses,
                        morningRead = if (odd.isMorningReadEnabled) odd.morningRead else "",
                        evenMorningRead = if (even.isMorningReadEnabled) even.morningRead else ""
                    )
                }
            }
            return Timetable(name = name, timePlanName = timePlanName, days = days)
        }
    }
}

/** 某一天最终要显示的课（已套用单双周和临时调课） */
data class DayView(
    val date: java.time.LocalDate,
    val dayIndex: Int,          // 1..7
    val weekNumber: Int,
    val isOdd: Boolean,
    val timePlan: TimePlan?,
    val morningRead: String,
    val courses: List<String>,  // 与 timePlan.periods 对齐
    val swappedPeriods: Set<Int> // 被临时调课的节次下标
) {
    val hasCourses: Boolean get() = courses.any { it.isNotBlank() } || morningRead.isNotBlank()
}
