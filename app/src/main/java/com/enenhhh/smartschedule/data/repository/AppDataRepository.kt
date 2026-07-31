package com.enenhhh.smartschedule.data.repository

import android.content.Context
import com.enenhhh.smartschedule.data.Homework
import com.enenhhh.smartschedule.data.HomeworkRepository
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.models.SchedulePlan
import com.enenhhh.smartschedule.models.TimePeriod
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.util.Prefs
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

class AppDataRepository(context: Context) {
    private val appContext = context.applicationContext

    private val scheduleManager by lazy { ScheduleManager(appContext) }
    private val homeworkRepository by lazy { HomeworkRepository(appContext) }

    // ========== 方案 ==========

    fun getTimePlans(): List<TimePlan> = scheduleManager.getTimePlans()

    fun getSchedulePlans(): List<SchedulePlan> = scheduleManager.getSchedulePlans()

    fun getActiveSchedule(isOdd: Boolean, dayIndex: Int): SchedulePlan? =
        scheduleManager.getActiveSchedule(isOdd, dayIndex)

    fun saveTimePlan(plan: TimePlan) = scheduleManager.saveTimePlan(plan)

    fun deleteTimePlan(name: String) = scheduleManager.deleteTimePlan(name)

    fun copyTimePlan(sourceName: String, newName: String) =
        scheduleManager.copyTimePlan(sourceName, newName)

    fun saveSchedulePlan(plan: SchedulePlan) = scheduleManager.saveSchedulePlan(plan)

    fun deleteSchedulePlan(name: String) = scheduleManager.deleteSchedulePlan(name)

    fun copySchedulePlan(sourceName: String, newName: String) =
        scheduleManager.copySchedulePlan(sourceName, newName)

    fun calculateAutoFill(
        startTime: String,
        lessonMinutes: Int,
        breakMinutes: Int,
        count: Int
    ): List<TimePeriod> = scheduleManager.calculateAutoFill(startTime, lessonMinutes, breakMinutes, count)

    fun getShortName(fullName: String): String = scheduleManager.getShortName(fullName)

    // ========== 作业 ==========

    fun getPendingHomework(): List<Homework> = homeworkRepository.getPending()

    fun getCompletedHomework(): List<Homework> = homeworkRepository.getCompleted()

    fun addHomework(item: Homework) = homeworkRepository.add(item)

    fun updateHomework(item: Homework) = homeworkRepository.update(item)

    fun deleteHomework(id: String) = homeworkRepository.delete(id)

    fun toggleHomeworkComplete(id: String) = homeworkRepository.toggleComplete(id)

    // ========== 课程简写 ==========

    fun getCustomShortNames(): Map<String, String> = Prefs.getCustomShortNames(appContext)

    fun setCustomShortNames(map: Map<String, String>) {
        Prefs.setCustomShortNames(appContext, map)
        scheduleManager.invalidateCache()
    }

    // ========== 临时换课 ==========

    private val tempSwapPrefs by lazy {
        appContext.getSharedPreferences("temp_swaps", Context.MODE_PRIVATE)
    }

    data class TempSwap(
        val date: String,       // "yyyy-MM-dd"
        val periodIndex: Int,   // 第几节课（0-based，不含早读）
        val newCourse: String
    )

    private fun readSwaps(): JSONArray =
        runCatching { JSONArray(tempSwapPrefs.getString("swaps", "[]") ?: "[]") }
            .getOrDefault(JSONArray())

    private fun writeSwaps(arr: JSONArray) {
        tempSwapPrefs.edit().putString("swaps", arr.toString()).apply()
    }

    /** 过滤掉「同一天同一节」的旧记录 */
    private fun JSONArray.without(date: String, periodIndex: Int): JSONArray {
        val out = JSONArray()
        for (i in 0 until length()) {
            val obj = optJSONObject(i) ?: continue
            if (obj.optString("date") == date && obj.optInt("periodIndex", -1) == periodIndex) continue
            out.put(obj)
        }
        return out
    }

    fun getTempSwapsForDate(date: String): List<TempSwap> {
        val arr = readSwaps()
        val result = mutableListOf<TempSwap>()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("date") != date) continue
            result.add(
                TempSwap(
                    date = obj.optString("date"),
                    periodIndex = obj.optInt("periodIndex", -1),
                    newCourse = obj.optString("newCourse")
                )
            )
        }
        return result
    }

    fun addTempSwap(date: String, periodIndex: Int, newCourse: String) {
        val arr = readSwaps().without(date, periodIndex)
        arr.put(
            JSONObject().apply {
                put("date", date)
                put("periodIndex", periodIndex)
                put("newCourse", newCourse)
            }
        )
        writeSwaps(arr)
    }

    fun removeTempSwap(date: String, periodIndex: Int) {
        writeSwaps(readSwaps().without(date, periodIndex))
    }

    fun clearExpiredSwaps() {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())
        val arr = readSwaps()
        val kept = JSONArray()
        for (i in 0 until arr.length()) {
            val obj = arr.optJSONObject(i) ?: continue
            if (obj.optString("date") >= today) kept.put(obj)
        }
        writeSwaps(kept)
    }

    /** 应用临时换课之后的当天课程列表 */
    fun getCoursesWithSwaps(plan: SchedulePlan, date: String): List<String> {
        val courses = plan.courses.toMutableList()
        getTempSwapsForDate(date).forEach { swap ->
            if (swap.periodIndex in courses.indices) {
                courses[swap.periodIndex] = swap.newCourse
            }
        }
        return courses
    }
}
