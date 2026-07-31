package com.enenhhh.smartschedule.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object DateUtils {
    private val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    /** 从开学日期算起，[timeMillis] 落在第几周（开学当周为第 1 周） */
    fun getWeekNumberFrom(startDateStr: String, timeMillis: Long): Int {
        val start = Calendar.getInstance().apply {
            time = synchronized(sdf) { runCatching { sdf.parse(startDateStr) }.getOrNull() } ?: Date()
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val diffDays = ((timeMillis - start.timeInMillis) / (24L * 60 * 60 * 1000)).toInt()
        // 开学日期填成未来时，diffDays 为负，向下取整避免第 0 / 第 -1 周
        return if (diffDays < 0) 1 else (diffDays / 7) + 1
    }
}
