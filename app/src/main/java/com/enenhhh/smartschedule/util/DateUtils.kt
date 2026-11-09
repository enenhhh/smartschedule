package com.enenhhh.smartschedule.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/**
 * 周数 / 单双周计算。
 *
 * 旧版用「(今天 - 开学日) / 7 + 1」算周数，开学日不是周一时，一周的分界会落在
 * 周中的某一天，导致周日看「明日课表」时把下周一算成了本周（单双周错位）。
 * 现在一律按「周一到周日为一周」对齐：先把开学日和目标日期都归到各自那周的周一，
 * 再算相差几周。
 */
object DateUtils {
    val ISO: DateTimeFormatter = DateTimeFormatter.ISO_LOCAL_DATE
    const val DEFAULT_START_UNSET = ""

    fun parseDate(s: String?): LocalDate? =
        if (s.isNullOrBlank()) null else runCatching { LocalDate.parse(s.trim(), ISO) }.getOrNull()

    fun format(d: LocalDate): String = d.format(ISO)

    fun mondayOf(d: LocalDate): LocalDate = d.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))

    /** 开学那一周为第 1 周；开学前为 0、-1 …（界面上会显示为「未开学」） */
    fun weekNumber(startDate: LocalDate, date: LocalDate): Int {
        val weeks = ChronoUnit.WEEKS.between(mondayOf(startDate), mondayOf(date))
        return (weeks + 1).toInt()
    }

    /** 第 1、3、5 … 周为单周。开学前的周也按同样的奇偶规律往前推，不会突然跳变 */
    fun isOddWeek(weekNumber: Int): Boolean = Math.floorMod(weekNumber - 1, 2) == 0

    fun isOddWeek(startDate: LocalDate, date: LocalDate): Boolean = isOddWeek(weekNumber(startDate, date))

    /** 1 = 周一 … 7 = 周日 */
    fun dayIndex(d: LocalDate): Int = d.dayOfWeek.value

    fun dayName(dayIndex: Int, prefix: String = "周"): String =
        prefix + listOf("一", "二", "三", "四", "五", "六", "日")[(dayIndex - 1).coerceIn(0, 6)]

    fun nowMinutes(): Int = LocalTime.now().let { it.hour * 60 + it.minute }

    /**
     * 根据「本周是第 N 周」反推开学日期（取那一周的周一）。
     * 首次配置时让用户填当前周数比让用户回忆开学日期容易得多。
     */
    fun startDateForCurrentWeek(currentWeek: Int, today: LocalDate = LocalDate.now()): LocalDate =
        mondayOf(today).minusWeeks((currentWeek - 1).coerceAtLeast(0).toLong())

    /** 用于首页标题：10月16日 周五 */
    fun prettyDate(d: LocalDate): String = "${d.monthValue}月${d.dayOfMonth}日 ${dayName(dayIndex(d))}"
}
