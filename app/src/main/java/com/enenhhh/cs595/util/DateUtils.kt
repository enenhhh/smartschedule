package com.enenhhh.smartschedule.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object DateUtils {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    fun getWeekNumberFrom(startDateStr: String, timeMillis: Long): Int {
        val start = Calendar.getInstance().apply {
            time = sdf.parse(startDateStr) ?: java.util.Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val now = Calendar.getInstance().apply { timeInMillis = timeMillis }
        val diffDays = ((now.timeInMillis - start.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
        return (diffDays / 7) + 1
    }

    fun isOddWeekRelativeTo(timeMillis: Long, startDateStr: String): Boolean {
        val w = getWeekNumberFrom(startDateStr, timeMillis)
        return w % 2 == 1
    }

    fun todayChinese(): String {
        val w = Calendar.getInstance().get(Calendar.DAY_OF_WEEK)
        return dowChinese(w)
    }

    fun tomorrowChinese(): String {
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        return dowChinese(cal.get(Calendar.DAY_OF_WEEK))
    }

    private fun dowChinese(w: Int): String {
        return when (w) {
            Calendar.MONDAY -> "周一"
            Calendar.TUESDAY -> "周二"
            Calendar.WEDNESDAY -> "周三"
            Calendar.THURSDAY -> "周四"
            Calendar.FRIDAY -> "周五"
            Calendar.SATURDAY -> "周六"
            else -> "周日"
        }
    }

    // 新增：给指定周计算日期范围（M/d-M/d）
    fun getWeekRangeLabel(startDateStr: String, weekIndex: Int): String {
        val startCal = Calendar.getInstance().apply {
            time = sdf.parse(startDateStr) ?: java.util.Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, ((weekIndex - 1) * 7).coerceAtLeast(0))
        }
        val endCal = (startCal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) }
        fun Calendar.md(): String = "${get(Calendar.MONTH) + 1}/${get(Calendar.DAY_OF_MONTH)}"
        return "${startCal.md()}-${endCal.md()}"
    }
}