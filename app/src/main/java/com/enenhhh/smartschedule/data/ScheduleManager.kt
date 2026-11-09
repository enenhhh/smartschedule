package com.enenhhh.smartschedule.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.enenhhh.smartschedule.models.DayView
import com.enenhhh.smartschedule.models.LegacySchedulePlan
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.models.Timetable
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import java.io.File
import java.time.LocalDate

/**
 * 课表 / 时间方案 / 简写的存取中心。
 *
 * 文件布局（外部私有目录，用户可以用文件管理器看到，方便手动备份）：
 *   plans/time/<方案名>.json        时间方案
 *   plans/timetable/<id>.json       5.0 课表
 *   plans/course/<方案名>.json      4.0 及以前的课程方案（迁移后保留不动，只读）
 *
 * 所有读操作走进程级缓存；任何写操作都会让缓存失效并把 [version] +1，
 * Compose 界面用 `remember(ScheduleManager.version)` 就能自动刷新。
 */
class ScheduleManager(context: Context) {
    private val appContext = context.applicationContext
    private val baseDir = appContext.getExternalFilesDir(null) ?: appContext.filesDir
    private val plansDir = File(baseDir, "plans")
    private val timeDir = File(plansDir, "time")
    private val legacyCourseDir = File(plansDir, "course")
    private val timetableDir = File(plansDir, "timetable")

    init {
        timeDir.mkdirs()
        timetableDir.mkdirs()
        migrateIfNeeded()
    }

    companion object {
        /** 任意数据变动 +1；界面以它为 key 重新读取 */
        var version by mutableIntStateOf(0)
            private set

        @Volatile private var cachedTimePlans: List<TimePlan>? = null
        @Volatile private var cachedTimetables: List<Timetable>? = null
        @Volatile private var cachedShortNames: Map<String, String>? = null

        fun invalidateCache() {
            cachedTimePlans = null
            cachedTimetables = null
            cachedShortNames = null
            version++
        }

        /** 文件名只保留安全字符，避免 "/"、".." 之类把路径带跑 */
        fun sanitizeFileName(name: String): String {
            val cleaned = name.trim()
                .replace(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"), "_")
                .replace(Regex("\\.{2,}"), "_")
                .trim('.', ' ')
            val limited = if (cleaned.length > 60) cleaned.substring(0, 60) else cleaned
            return limited.ifBlank { "unnamed" }
        }

        fun isValidPlanName(name: String): Boolean =
            name.isNotBlank() && name.length <= 30 && !name.contains(Regex("[\\\\/:*?\"<>|\\u0000-\\u001f]"))
    }

    fun invalidateCache() = Companion.invalidateCache()

    // ------------------------------------------------------------------ 时间方案

    fun getTimePlans(): List<TimePlan> {
        cachedTimePlans?.let { return it }
        val loaded = timeDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f -> runCatching { TimePlan.fromJson(f.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()
        cachedTimePlans = loaded
        return loaded
    }

    fun getTimePlan(name: String?): TimePlan? =
        if (name.isNullOrBlank()) null else getTimePlans().firstOrNull { it.name == name }

    /** 保存；[oldName] 不为空且与新名字不同视为重命名，会一并更新引用它的课表 */
    fun saveTimePlan(plan: TimePlan, oldName: String? = null) {
        if (!oldName.isNullOrBlank() && oldName != plan.name) {
            timePlanFile(oldName).delete()
            getTimetables().filter { it.timePlanName == oldName }.forEach { tt ->
                writeTimetable(tt.copy(timePlanName = plan.name))
            }
        }
        timePlanFile(plan.name).writeText(plan.toJson())
        invalidateCache()
    }

    fun deleteTimePlan(name: String) {
        timePlanFile(name).delete()
        // 兼容旧版未净化的文件名
        timeDir.listFiles()?.forEach { f ->
            if (f.extension == "json" && runCatching { TimePlan.fromJson(f.readText()).name }.getOrNull() == name) f.delete()
        }
        invalidateCache()
    }

    fun duplicateTimePlan(sourceName: String, newName: String) {
        val src = getTimePlan(sourceName) ?: return
        saveTimePlan(src.copy(name = newName))
    }

    private fun timePlanFile(name: String) = File(timeDir, sanitizeFileName(name) + ".json")

    // ------------------------------------------------------------------ 课表

    fun getTimetables(): List<Timetable> {
        cachedTimetables?.let { return it }
        val loaded = timetableDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f -> runCatching { Timetable.fromJson(f.readText()) }.getOrNull() }
            ?.sortedBy { it.createdAt }
            ?: emptyList()
        cachedTimetables = loaded
        return loaded
    }

    fun getTimetable(id: String?): Timetable? =
        if (id.isNullOrBlank()) null else getTimetables().firstOrNull { it.id == id }

    fun saveTimetable(tt: Timetable) {
        writeTimetable(tt)
        if (Prefs.getActiveTimetableId(appContext) == null) Prefs.setActiveTimetableId(appContext, tt.id)
        invalidateCache()
    }

    private fun writeTimetable(tt: Timetable) {
        File(timetableDir, sanitizeFileName(tt.id) + ".json").writeText(tt.toJson())
    }

    fun deleteTimetable(id: String) {
        File(timetableDir, sanitizeFileName(id) + ".json").delete()
        if (Prefs.getActiveTimetableId(appContext) == id) {
            Prefs.setActiveTimetableId(appContext, null)
        }
        invalidateCache()
        // 删掉使用中的那张后自动切到剩余的第一张
        if (Prefs.getActiveTimetableId(appContext) == null) {
            getTimetables().firstOrNull()?.let { Prefs.setActiveTimetableId(appContext, it.id) }
        }
    }

    fun duplicateTimetable(id: String, newName: String): Timetable? {
        val src = getTimetable(id) ?: return null
        val copy = src.copy(id = Timetable.newId(), name = newName, createdAt = System.currentTimeMillis())
        saveTimetable(copy)
        return copy
    }

    fun getActiveTimetable(): Timetable? {
        val all = getTimetables()
        if (all.isEmpty()) return null
        val id = Prefs.getActiveTimetableId(appContext)
        return all.firstOrNull { it.id == id } ?: all.first().also { Prefs.setActiveTimetableId(appContext, it.id) }
    }

    fun setActiveTimetable(id: String) {
        Prefs.setActiveTimetableId(appContext, id)
        invalidateCache()
    }

    /** 课表绑定的时间方案；绑定失效时退回第一个可用的方案 */
    fun resolveTimePlan(tt: Timetable?): TimePlan? {
        val plans = getTimePlans()
        if (plans.isEmpty()) return null
        return plans.firstOrNull { it.name == tt?.timePlanName } ?: plans.first()
    }

    /** 新建一张课表并设为使用中（首次配置 / 空状态按钮用） */
    fun createTimetable(name: String, timePlanName: String?): Timetable {
        val tp = timePlanName ?: getTimePlans().firstOrNull()?.name ?: run {
            val def = TimePlan.defaultPlan()
            saveTimePlan(def)
            def.name
        }
        val tt = Timetable(name = name.ifBlank { "我的课表" }, timePlanName = tp)
        saveTimetable(tt)
        setActiveTimetable(tt.id)
        return tt
    }

    // ------------------------------------------------------------------ 某一天的课

    fun dayView(date: LocalDate, timetable: Timetable? = getActiveTimetable()): DayView {
        val start = Prefs.getStartDate(appContext)
        val week = DateUtils.weekNumber(start, date)
        val isOdd = DateUtils.isOddWeek(week)
        val dayIndex = DateUtils.dayIndex(date)
        val plan = resolveTimePlan(timetable)
        val periodCount = plan?.periods?.size ?: 0
        val day = timetable?.day(dayIndex)
        val base = day?.coursesFor(isOdd) ?: emptyList()
        val courses = MutableList(periodCount) { base.getOrNull(it).orEmpty() }
        val swaps = TempSwaps.forDate(appContext, date)
        val swapped = HashSet<Int>()
        swaps.forEach { s ->
            if (s.period in courses.indices) {
                courses[s.period] = s.course
                swapped.add(s.period)
            }
        }
        return DayView(
            date = date,
            dayIndex = dayIndex,
            weekNumber = week,
            isOdd = isOdd,
            timePlan = plan,
            morningRead = day?.morningReadFor(isOdd).orEmpty(),
            courses = courses,
            swappedPeriods = swapped
        )
    }

    /** 当前课表 + 所有课表里出现过的课程名，用于编辑时的联想 */
    fun knownCourseNames(): List<String> {
        val names = LinkedHashSet<String>()
        getActiveTimetable()?.allCourseNames()?.let { names.addAll(it) }
        getTimetables().forEach { names.addAll(it.allCourseNames()) }
        names.addAll(getCustomShortNames().keys)
        return names.filter { it.isNotBlank() }
    }

    // ------------------------------------------------------------------ 课程简写

    fun getShortName(fullName: String): String {
        if (fullName.isBlank()) return ""
        val custom = getCustomShortNames()[fullName]
        if (!custom.isNullOrBlank()) return custom
        return if (fullName.length > 2) fullName.substring(0, 2) else fullName
    }

    fun getCustomShortNames(): Map<String, String> {
        cachedShortNames?.let { return it }
        val map = Prefs.getCustomShortNames(appContext)
        cachedShortNames = map
        return map
    }

    fun setCustomShortNames(map: Map<String, String>) {
        Prefs.setCustomShortNames(appContext, map)
        invalidateCache()
    }

    // ------------------------------------------------------------------ 旧数据迁移

    fun readLegacyPlans(): List<LegacySchedulePlan> =
        legacyCourseDir.listFiles()
            ?.filter { it.extension == "json" }
            ?.mapNotNull { f -> runCatching { LegacySchedulePlan.fromJson(f.readText()) }.getOrNull() }
            ?.sortedBy { it.name }
            ?: emptyList()

    /**
     * 4.0 → 5.0：把「一天一份」的课程方案合成一张整周课表。只在还没有任何 5.0
     * 课表时执行一次；旧文件原样保留，万一有问题用户还能回退旧版本。
     */
    private fun migrateIfNeeded() {
        if (Prefs.isMigratedV5(appContext)) return
        val existing = timetableDir.listFiles()?.any { it.extension == "json" } == true
        if (!existing) {
            val legacy = readLegacyPlans()
            if (legacy.isNotEmpty()) {
                val tt = Timetable.fromLegacy(legacy, "我的课表")
                writeTimetable(tt)
                Prefs.setActiveTimetableId(appContext, tt.id)
            }
        }
        Prefs.setMigratedV5(appContext, true)
        Companion.invalidateCache()
    }

    // ------------------------------------------------------------------ 导入（分享码 / 备份）

    /**
     * 合并导入：同名时间方案覆盖；课表按 id 覆盖、否则追加。
     * [replace] 为 true 时先清空现有的时间方案和课表。
     */
    fun importData(timePlans: List<TimePlan>, timetables: List<Timetable>, shortNames: Map<String, String>, replace: Boolean) {
        if (replace) {
            timeDir.listFiles()?.forEach { it.delete() }
            timetableDir.listFiles()?.forEach { it.delete() }
            Prefs.setActiveTimetableId(appContext, null)
        }
        timePlans.forEach { timePlanFile(it.name).writeText(it.toJson()) }
        timetables.forEach { writeTimetable(it) }
        if (shortNames.isNotEmpty()) {
            val merged = LinkedHashMap(if (replace) emptyMap() else getCustomShortNames())
            merged.putAll(shortNames)
            Prefs.setCustomShortNames(appContext, merged)
        }
        invalidateCache()
        if (Prefs.getActiveTimetableId(appContext) == null || getTimetable(Prefs.getActiveTimetableId(appContext)) == null) {
            (timetables.firstOrNull() ?: getTimetables().firstOrNull())?.let { Prefs.setActiveTimetableId(appContext, it.id) }
        }
        invalidateCache()
    }
}
