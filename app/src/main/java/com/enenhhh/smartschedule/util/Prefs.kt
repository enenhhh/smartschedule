package com.enenhhh.smartschedule.util

import android.content.Context
import org.json.JSONObject

object Prefs {
    private const val PREF = "prefs"
    private const val KEY_START_DATE = "start_date"
    private const val KEY_KEEP_ALIVE = "keep_alive"
    private const val KEY_PERSISTENT = "persistent_tomorrow"
    private const val KEY_REMINDER_ENABLED = "reminder_enabled"
    private const val KEY_REMINDER_HOUR = "reminder_hour"
    private const val KEY_REMINDER_MIN = "reminder_min"
    private const val KEY_WEATHER_ENABLED = "weather_enabled"
    private const val KEY_WEATHER_HOUR = "weather_hour"
    private const val KEY_WEATHER_MIN = "weather_min"
    private const val KEY_WEATHER_CITY = "weather_city"
    private const val KEY_CUSTOM_SHORT_NAMES = "custom_short_names"
    private const val KEY_DARK_MODE = "dark_mode"
    private const val KEY_DYNAMIC_COLOR = "dynamic_color"
    private const val KEY_AUTO_CHECK_UPDATE = "auto_check_update"
    private const val KEY_FIRST_RUN_DONE = "first_run_done"
    private const val KEY_THEME_COLOR = "theme_color"

    const val THEME_INDIGO = "indigo"
    const val THEME_TEAL = "teal"
    const val THEME_ORANGE = "orange"
    const val THEME_PURPLE = "purple"

    /** [getDarkMode] 的取值 */
    const val DARK_MODE_SYSTEM = 0
    const val DARK_MODE_ON = 1
    const val DARK_MODE_OFF = 2

    private fun getPrefs(ctx: Context) = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- 学期 ----
    fun getStartDate(ctx: Context): String =
        getPrefs(ctx).getString(KEY_START_DATE, "2025-09-01") ?: "2025-09-01"

    fun setStartDate(ctx: Context, d: String) =
        getPrefs(ctx).edit().putString(KEY_START_DATE, d).apply()

    // ---- 通知 / 提醒 ----
    fun isKeepAlive(ctx: Context) = getPrefs(ctx).getBoolean(KEY_KEEP_ALIVE, false)
    fun setKeepAlive(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_KEEP_ALIVE, v).apply()

    fun isPersistentEnabled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_PERSISTENT, false)
    fun setPersistentEnabled(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_PERSISTENT, v).apply()

    fun isTomorrowReminderEnabled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_REMINDER_ENABLED, false)
    fun setTomorrowReminderEnabled(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_REMINDER_ENABLED, v).apply()

    fun getReminderHour(ctx: Context) = getPrefs(ctx).getInt(KEY_REMINDER_HOUR, 19)
    fun getReminderMinute(ctx: Context) = getPrefs(ctx).getInt(KEY_REMINDER_MIN, 0)
    fun setReminderTime(ctx: Context, h: Int, m: Int) =
        getPrefs(ctx).edit().putInt(KEY_REMINDER_HOUR, h).putInt(KEY_REMINDER_MIN, m).apply()

    fun isWeatherReminderEnabled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_WEATHER_ENABLED, false)
    fun setWeatherReminderEnabled(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_WEATHER_ENABLED, v).apply()

    fun getWeatherHour(ctx: Context) = getPrefs(ctx).getInt(KEY_WEATHER_HOUR, 8)
    fun getWeatherMinute(ctx: Context) = getPrefs(ctx).getInt(KEY_WEATHER_MIN, 0)
    fun setWeatherTime(ctx: Context, h: Int, m: Int) =
        getPrefs(ctx).edit().putInt(KEY_WEATHER_HOUR, h).putInt(KEY_WEATHER_MIN, m).apply()

    fun getWeatherCity(ctx: Context) = getPrefs(ctx).getString(KEY_WEATHER_CITY, "北京") ?: "北京"
    fun setWeatherCity(ctx: Context, city: String) =
        getPrefs(ctx).edit().putString(KEY_WEATHER_CITY, city).apply()

    // ---- 外观 ----
    fun getDarkMode(ctx: Context) = getPrefs(ctx).getInt(KEY_DARK_MODE, DARK_MODE_SYSTEM)
    fun setDarkMode(ctx: Context, m: Int) = getPrefs(ctx).edit().putInt(KEY_DARK_MODE, m).apply()

    fun isDynamicColorEnabled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_DYNAMIC_COLOR, false)
    fun setDynamicColorEnabled(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_DYNAMIC_COLOR, v).apply()

    fun getThemeColor(ctx: Context) =
        getPrefs(ctx).getString(KEY_THEME_COLOR, THEME_INDIGO) ?: THEME_INDIGO

    fun setThemeColor(ctx: Context, v: String) =
        getPrefs(ctx).edit().putString(KEY_THEME_COLOR, v).apply()

    // ---- 其他 ----
    fun isFirstRunDone(ctx: Context) = getPrefs(ctx).getBoolean(KEY_FIRST_RUN_DONE, false)
    fun setFirstRunDone(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_FIRST_RUN_DONE, v).apply()

    fun isAutoCheckUpdate(ctx: Context) = getPrefs(ctx).getBoolean(KEY_AUTO_CHECK_UPDATE, true)
    fun setAutoCheckUpdate(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_AUTO_CHECK_UPDATE, v).apply()

    // ---- 课程简写 ----
    fun getCustomShortNames(ctx: Context): Map<String, String> {
        val jsonStr = getPrefs(ctx).getString(KEY_CUSTOM_SHORT_NAMES, "{}") ?: "{}"
        val map = mutableMapOf<String, String>()
        runCatching {
            val obj = JSONObject(jsonStr)
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.getString(k)
            }
        }
        return map
    }

    fun setCustomShortNames(ctx: Context, map: Map<String, String>) {
        val root = JSONObject()
        map.forEach { (k, v) -> root.put(k, v) }
        getPrefs(ctx).edit().putString(KEY_CUSTOM_SHORT_NAMES, root.toString()).apply()
    }
}
