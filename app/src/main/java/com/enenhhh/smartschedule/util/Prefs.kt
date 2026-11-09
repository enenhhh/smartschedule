package com.enenhhh.smartschedule.util

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.time.LocalDate

/**
 * 所有偏好设置的唯一入口。键名保持与 4.0 兼容，老用户升级后设置不丢。
 */
object Prefs {
    const val PREF = "prefs"

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

    // 5.0 新增
    private const val KEY_PALETTE_STYLE = "palette_style"
    private const val KEY_CONTRAST = "contrast_level"
    private const val KEY_AMOLED = "amoled_black"
    private const val KEY_GLASS_ENABLED = "glass_enabled"
    private const val KEY_GLASS_INTENSITY = "glass_intensity"
    private const val KEY_GLASS_LIQUID = "glass_liquid"
    private const val KEY_ANIMATED_BG = "animated_background"
    private const val KEY_ACTIVE_TIMETABLE = "active_timetable"
    private const val KEY_MIGRATED_V5 = "migrated_v5"
    private const val KEY_CUSTOM_SEED = "custom_seed_color"
    private const val KEY_SHOW_WEEKEND = "show_weekend"
    private const val KEY_UPDATE_SOURCE = "update_source"
    private const val KEY_LAST_UPDATE_CHECK = "last_update_check"
    private const val KEY_SKIPPED_VERSION = "skipped_version"

    // 旧版 4 个主题 key 仍然有效，映射到新的种子色
    const val THEME_INDIGO = "indigo"
    const val THEME_TEAL = "teal"
    const val THEME_ORANGE = "orange"
    const val THEME_PURPLE = "purple"

    const val DARK_MODE_SYSTEM = 0
    const val DARK_MODE_ON = 1
    const val DARK_MODE_OFF = 2

    fun getPrefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ---- 学期 ----

    /**
     * 开学日期（yyyy-MM-dd）。4.0 的默认值写死为 2025-09-01，升级用户若从未改过，
     * 这里视为「未设置」，由 [getStartDate] 给一个按当前日期推断的默认值。
     */
    fun getStartDateRaw(ctx: Context): String? = getPrefs(ctx).getString(KEY_START_DATE, null)

    fun isStartDateSet(ctx: Context): Boolean {
        val raw = getStartDateRaw(ctx) ?: return false
        return raw.isNotBlank() && raw != "2025-09-01"
    }

    fun getStartDate(ctx: Context): LocalDate {
        DateUtils.parseDate(getStartDateRaw(ctx))?.let { return it }
        return guessSemesterStart()
    }

    fun setStartDate(ctx: Context, d: LocalDate) =
        getPrefs(ctx).edit().putString(KEY_START_DATE, DateUtils.format(d)).apply()

    /** 没配置时的猜测：2~7 月视为春季学期（最近的 3 月 1 日所在周），其余视为秋季学期（9 月 1 日所在周） */
    fun guessSemesterStart(today: LocalDate = LocalDate.now()): LocalDate {
        val candidate = when (today.monthValue) {
            in 2..7 -> LocalDate.of(today.year, 2, 20)
            1 -> LocalDate.of(today.year - 1, 9, 1)
            else -> LocalDate.of(today.year, 9, 1)
        }
        return DateUtils.mondayOf(candidate)
    }

    fun isShowWeekend(ctx: Context) = getPrefs(ctx).getBoolean(KEY_SHOW_WEEKEND, false)
    fun setShowWeekend(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_SHOW_WEEKEND, v).apply()

    // ---- 课表 ----
    fun getActiveTimetableId(ctx: Context): String? = getPrefs(ctx).getString(KEY_ACTIVE_TIMETABLE, null)
    fun setActiveTimetableId(ctx: Context, id: String?) =
        getPrefs(ctx).edit().putString(KEY_ACTIVE_TIMETABLE, id).apply()

    fun isMigratedV5(ctx: Context) = getPrefs(ctx).getBoolean(KEY_MIGRATED_V5, false)
    fun setMigratedV5(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_MIGRATED_V5, v).apply()

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
    fun setWeatherCity(ctx: Context, city: String) {
        getPrefs(ctx).edit().putString(KEY_WEATHER_CITY, city).apply()
        // Manual edits must not keep querying an older location in another city.
        com.enenhhh.smartschedule.weather.WeatherLocation.clear(ctx)
    }

    // ---- 外观 ----
    fun getDarkMode(ctx: Context) = getPrefs(ctx).getInt(KEY_DARK_MODE, DARK_MODE_SYSTEM)
    fun setDarkMode(ctx: Context, m: Int) = getPrefs(ctx).edit().putInt(KEY_DARK_MODE, m).apply()

    fun isDynamicColorEnabled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_DYNAMIC_COLOR, false)
    fun setDynamicColorEnabled(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_DYNAMIC_COLOR, v).apply()

    fun getThemeColor(ctx: Context) = getPrefs(ctx).getString(KEY_THEME_COLOR, THEME_INDIGO) ?: THEME_INDIGO
    fun setThemeColor(ctx: Context, v: String) = getPrefs(ctx).edit().putString(KEY_THEME_COLOR, v).apply()

    fun getCustomSeed(ctx: Context): Int = getPrefs(ctx).getInt(KEY_CUSTOM_SEED, 0xFF006BDF.toInt())
    fun setCustomSeed(ctx: Context, argb: Int) = getPrefs(ctx).edit().putInt(KEY_CUSTOM_SEED, argb).apply()

    fun getPaletteStyle(ctx: Context) = getPrefs(ctx).getString(KEY_PALETTE_STYLE, "fidelity") ?: "fidelity"
    fun setPaletteStyle(ctx: Context, v: String) = getPrefs(ctx).edit().putString(KEY_PALETTE_STYLE, v).apply()

    /** 0 标准 / 1 中 / 2 高 */
    fun getContrast(ctx: Context) = getPrefs(ctx).getInt(KEY_CONTRAST, 0)
    fun setContrast(ctx: Context, v: Int) = getPrefs(ctx).edit().putInt(KEY_CONTRAST, v).apply()

    fun isAmoled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_AMOLED, false)
    fun setAmoled(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_AMOLED, v).apply()

    fun isGlassEnabled(ctx: Context) = getPrefs(ctx).getBoolean(KEY_GLASS_ENABLED, true)
    fun setGlassEnabled(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_GLASS_ENABLED, v).apply()

    /** 0..1 */
    fun getGlassIntensity(ctx: Context) = getPrefs(ctx).getFloat(KEY_GLASS_INTENSITY, 0.6f)
    fun setGlassIntensity(ctx: Context, v: Float) =
        getPrefs(ctx).edit().putFloat(KEY_GLASS_INTENSITY, v.coerceIn(0f, 1f)).apply()

    /** 液态玻璃（折射 / 高光），Android 13+ 才有完整效果 */
    fun isLiquidGlass(ctx: Context) = getPrefs(ctx).getBoolean(KEY_GLASS_LIQUID, true)
    fun setLiquidGlass(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_GLASS_LIQUID, v).apply()

    fun isAnimatedBackground(ctx: Context) = getPrefs(ctx).getBoolean(KEY_ANIMATED_BG, true)
    fun setAnimatedBackground(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_ANIMATED_BG, v).apply()

    fun isReduceMotion(ctx: Context) = getPrefs(ctx).getBoolean("reduce_motion", false)
    fun setReduceMotion(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean("reduce_motion", v).apply()

    // One-time migration: old default Expressive rotates the blue seed into another hue.
    // Preserve explicitly customized colors/palettes. New blue uses Fidelity by default.
    fun migrateAppearance51(ctx: Context) {
        val p = getPrefs(ctx)
        if (p.getBoolean("appearance_51", false)) return
        val e = p.edit().putBoolean("appearance_51", true)
        if (getThemeColor(ctx) == THEME_INDIGO && getPaletteStyle(ctx) == "expressive" && !isDynamicColorEnabled(ctx)) {
            e.putString(KEY_PALETTE_STYLE, "fidelity")
        }
        e.apply()
    }

    // ---- 其他 ----
    fun isFirstRunDone(ctx: Context) = getPrefs(ctx).getBoolean(KEY_FIRST_RUN_DONE, false)
    fun setFirstRunDone(ctx: Context, v: Boolean) = getPrefs(ctx).edit().putBoolean(KEY_FIRST_RUN_DONE, v).apply()

    fun isAutoCheckUpdate(ctx: Context) = getPrefs(ctx).getBoolean(KEY_AUTO_CHECK_UPDATE, true)
    fun setAutoCheckUpdate(ctx: Context, v: Boolean) =
        getPrefs(ctx).edit().putBoolean(KEY_AUTO_CHECK_UPDATE, v).apply()

    fun getUpdateSource(ctx: Context) = getPrefs(ctx).getInt(KEY_UPDATE_SOURCE, 0)
    fun setUpdateSource(ctx: Context, v: Int) = getPrefs(ctx).edit().putInt(KEY_UPDATE_SOURCE, v).apply()

    fun getLastUpdateCheck(ctx: Context) = getPrefs(ctx).getLong(KEY_LAST_UPDATE_CHECK, 0L)
    fun setLastUpdateCheck(ctx: Context, v: Long) = getPrefs(ctx).edit().putLong(KEY_LAST_UPDATE_CHECK, v).apply()

    fun getSkippedVersion(ctx: Context) = getPrefs(ctx).getString(KEY_SKIPPED_VERSION, "") ?: ""
    fun setSkippedVersion(ctx: Context, v: String) = getPrefs(ctx).edit().putString(KEY_SKIPPED_VERSION, v).apply()

    // ---- 课程简写 ----
    fun getCustomShortNames(ctx: Context): Map<String, String> =
        parseShortNames(getPrefs(ctx).getString(KEY_CUSTOM_SHORT_NAMES, "{}"))

    fun setCustomShortNames(ctx: Context, map: Map<String, String>) {
        val root = JSONObject()
        map.forEach { (k, v) -> if (k.isNotBlank()) root.put(k, v) }
        getPrefs(ctx).edit().putString(KEY_CUSTOM_SHORT_NAMES, root.toString()).apply()
    }

    fun parseShortNames(jsonStr: String?): Map<String, String> {
        val map = LinkedHashMap<String, String>()
        runCatching {
            val obj = JSONObject(jsonStr ?: "{}")
            val keys = obj.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                map[k] = obj.optString(k, "")
            }
        }
        return map
    }
}
