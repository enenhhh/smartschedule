package com.enenhhh.smartschedule.data

data class DayEntry(
    var morningRead: String? = null,
    var courses: MutableList<String> = mutableListOf()
)

data class WeekSchedule(
    val days: MutableMap<String, DayEntry> = mutableMapOf()
) {
    fun getOrCreate(day: String): DayEntry {
        return days.getOrPut(day) { DayEntry() }
    }
}