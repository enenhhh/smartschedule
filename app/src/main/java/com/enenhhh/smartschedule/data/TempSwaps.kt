package com.enenhhh.smartschedule.data

import android.content.Context
import com.enenhhh.smartschedule.util.DateUtils
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/**
 * 临时调课：只对某一天的某一节生效，过期自动清理。
 * 存在 SharedPreferences "temp_swaps" 的 "swaps" 里，格式：
 *   [{"date":"2026-10-16","period":2,"course":"体育"}, ...]
 */
object TempSwaps {
    private const val PREF = "temp_swaps"
    private const val KEY = "swaps"

    data class Swap(val date: LocalDate, val period: Int, val course: String)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    private fun readAll(ctx: Context): MutableList<Swap> {
        val arr = runCatching { JSONArray(prefs(ctx).getString(KEY, "[]") ?: "[]") }.getOrDefault(JSONArray())
        val list = ArrayList<Swap>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val d = DateUtils.parseDate(o.optString("date")) ?: continue
            list.add(Swap(d, o.optInt("period", -1), o.optString("course", "")))
        }
        return list
    }

    private fun writeAll(ctx: Context, list: List<Swap>) {
        val arr = JSONArray()
        list.forEach { s ->
            arr.put(JSONObject().put("date", DateUtils.format(s.date)).put("period", s.period).put("course", s.course))
        }
        prefs(ctx).edit().putString(KEY, arr.toString()).apply()
    }

    fun forDate(ctx: Context, date: LocalDate): List<Swap> = readAll(ctx).filter { it.date == date }

    fun set(ctx: Context, date: LocalDate, period: Int, course: String) {
        val list = readAll(ctx).filterNot { it.date == date && it.period == period }.toMutableList()
        list.add(Swap(date, period, course))
        writeAll(ctx, list)
        ScheduleManager.invalidateCache()
    }

    fun remove(ctx: Context, date: LocalDate, period: Int) {
        writeAll(ctx, readAll(ctx).filterNot { it.date == date && it.period == period })
        ScheduleManager.invalidateCache()
    }

    fun clearDate(ctx: Context, date: LocalDate) {
        writeAll(ctx, readAll(ctx).filterNot { it.date == date })
        ScheduleManager.invalidateCache()
    }

    /** 删掉昨天及更早的记录 */
    fun clearExpired(ctx: Context) {
        val today = LocalDate.now()
        val all = readAll(ctx)
        val kept = all.filter { !it.date.isBefore(today) }
        if (kept.size != all.size) writeAll(ctx, kept)
    }
}
