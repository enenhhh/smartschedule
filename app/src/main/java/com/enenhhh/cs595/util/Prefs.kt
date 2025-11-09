package com.enenhhh.smartschedule.util

import android.content.Context
import android.content.SharedPreferences
import java.util.Calendar

object Prefs {
    private const val PREF = "prefs"
    private const val KEY_START_DATE = "start_date"
    private const val KEY_KEEP_ALIVE = "keep_alive"
    private const val KEY_PERSISTENT = "persistent_tomorrow"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MIN = "reminder_min"

    // Weather
    private const val KEY_WEATHER_ENABLED = "weather_enabled"
    private const val KEY_WEATHER_HOUR = "weather_hour"
    private const val KEY_WEATHER_MIN = "weather_min"
    private const val KEY_WEATHER_CITY = "weather_city"

    // 新增设置
    private const val KEY_COURSE_COUNT = "course_count"
    private const val KEY_CUSTOM_SHORT_NAMES = "custom_short_names"
    private const val KEY_SPLIT_POSITIONS = "split_positions"
    private const val KEY_SPLIT_SEPARATOR = "split_separator"

    // 引导完成标记
    private const val KEY_FIRST_RUN_DONE = "first_run_done"

    // 新增：隐藏最近任务
    private const val KEY_HIDE_RECENTS = "hide_recents"

    private const val DEFAULT_START_DATE = "2025-09-01"
    private const val DEFAULT_COURSE_COUNT = 8
    private const val DEFAULT_SPLIT_SEPARATOR = "｜"

    private fun getPrefs(ctx: Context): SharedPreferences {
        return ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)
    }

    fun getStartDate(ctx: Context): String {
        return getPrefs(ctx).getString(KEY_START_DATE, DEFAULT_START_DATE) ?: DEFAULT_START_DATE
    }

    fun setStartDate(ctx: Context, date: String) {
        getPrefs(ctx).edit().putString(KEY_START_DATE, normalize(date)).apply()
    }

    fun getStartDateCalendar(ctx: Context): Calendar {
        val cal = Calendar.getInstance()
        val str = getStartDate(ctx)
        val parts = str.split("-")
        if (parts.size == 3) {
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
        return cal
    }

    private fun normalize(input: String): String {
        return input.trim()
            .replace('/', '-')
            .split("-")
            .mapIndexed { i, s -> if (i == 0) s.padStart(4, '0') else s.padStart(2, '0') }
            .joinToString("-")
    }

    fun isKeepAlive(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_KEEP_ALIVE, false)
    }

    fun setKeepAlive(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_KEEP_ALIVE, v).apply()
    }

    fun isPersistentEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_PERSISTENT, false)
    }

    fun setPersistentEnabled(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_PERSISTENT, v).apply()
    }

    fun isTomorrowReminderEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_REMINDER_ENABLED, false)
    }

    fun setTomorrowReminderEnabled(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_REMINDER_ENABLED, v).apply()
    }

    fun getReminderHour(ctx: Context): Int {
        return getPrefs(ctx).getInt(KEY_REMINDER_HOUR, 19)
    }

    fun getReminderMinute(ctx: Context): Int {
        return getPrefs(ctx).getInt(KEY_REMINDER_MIN, 0)
    }

    fun setReminderTime(ctx: Context, hour: Int, minute: Int) {
        getPrefs(ctx).edit()
            .putInt(KEY_REMINDER_HOUR, hour)
            .putInt(KEY_REMINDER_MIN, minute)
            .apply()
    }

    // Weather
    fun isWeatherReminderEnabled(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_WEATHER_ENABLED, false)
    }

    fun setWeatherReminderEnabled(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_WEATHER_ENABLED, v).apply()
    }

    fun getWeatherHour(ctx: Context): Int {
        return getPrefs(ctx).getInt(KEY_WEATHER_HOUR, 20)
    }

    fun getWeatherMinute(ctx: Context): Int {
        return getPrefs(ctx).getInt(KEY_WEATHER_MIN, 0)
    }

    fun setWeatherTime(ctx: Context, hour: Int, minute: Int) {
        getPrefs(ctx).edit()
            .putInt(KEY_WEATHER_HOUR, hour)
            .putInt(KEY_WEATHER_MIN, minute)
            .apply()
    }

    fun getWeatherCity(ctx: Context): String {
        return getPrefs(ctx).getString(KEY_WEATHER_CITY, "北京") ?: "北京"
    }

    fun setWeatherCity(ctx: Context, city: String) {
        getPrefs(ctx).edit().putString(KEY_WEATHER_CITY, city.trim()).apply()
    }

    // 课程数量设置
    fun getCourseCount(ctx: Context): Int {
        return getPrefs(ctx).getInt(KEY_COURSE_COUNT, DEFAULT_COURSE_COUNT)
    }

    fun setCourseCount(ctx: Context, count: Int) {
        getPrefs(ctx).edit().putInt(KEY_COURSE_COUNT, count.coerceAtLeast(1).coerceAtMost(20)).apply()
    }

    // 自定义简写设置
    fun getCustomShortNames(ctx: Context): Map<String, String> {
        val jsonStr = getPrefs(ctx).getString(KEY_CUSTOM_SHORT_NAMES, "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        try {
            val json = org.json.JSONObject(jsonStr)
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = json.getString(key)
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return map
    }

    fun setCustomShortNames(ctx: Context, map: Map<String, String>) {
        val json = org.json.JSONObject()
        map.forEach { (key, value) ->
            json.put(key, value)
        }
        getPrefs(ctx).edit().putString(KEY_CUSTOM_SHORT_NAMES, json.toString()).apply()
    }

    // 分割线位置设置
    fun getSplitPositions(ctx: Context): List<Int> {
        val jsonStr = getPrefs(ctx).getString(KEY_SPLIT_POSITIONS, "[1,5]") ?: "[1,5]"
        val list = mutableListOf<Int>()
        try {
            val jsonArray = org.json.JSONArray(jsonStr)
            for (i in 0 until jsonArray.length()) {
                list.add(jsonArray.getInt(i))
            }
        } catch (e: Exception) {
            e.printStackTrace()
            list.addAll(listOf(1, 5))
        }
        return list
    }

    fun setSplitPositions(ctx: Context, positions: List<Int>) {
        val jsonArray = org.json.JSONArray()
        positions.forEach { jsonArray.put(it) }
        getPrefs(ctx).edit().putString(KEY_SPLIT_POSITIONS, jsonArray.toString()).apply()
    }

    // 分割线符号设置
    fun getSplitSeparator(ctx: Context): String {
        return getPrefs(ctx).getString(KEY_SPLIT_SEPARATOR, DEFAULT_SPLIT_SEPARATOR) ?: DEFAULT_SPLIT_SEPARATOR
    }

    fun setSplitSeparator(ctx: Context, separator: String) {
        getPrefs(ctx).edit().putString(KEY_SPLIT_SEPARATOR, separator).apply()
    }

    // 引导状态
    fun isFirstRunDone(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_FIRST_RUN_DONE, false)
    }

    fun setFirstRunDone(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_FIRST_RUN_DONE, v).apply()
    }

    // 隐藏最近任务
    fun isHideRecents(ctx: Context): Boolean {
        return getPrefs(ctx).getBoolean(KEY_HIDE_RECENTS, false)
    }

    fun setHideRecents(ctx: Context, v: Boolean) {
        getPrefs(ctx).edit().putBoolean(KEY_HIDE_RECENTS, v).apply()
    }
}