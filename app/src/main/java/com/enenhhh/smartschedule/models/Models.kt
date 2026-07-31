package com.enenhhh.smartschedule.models

import org.json.JSONArray
import org.json.JSONObject

/**
 * 时间方案模型
 */
data class TimePlan(
    var name: String,
    var periods: MutableList<TimePeriod> = mutableListOf(),
    var morningStart: String = "07:30",
    var morningEnd: String = "08:00"
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("name", name)
        root.put("morningStart", morningStart)
        root.put("morningEnd", morningEnd)
        val array = JSONArray()
        periods.forEach { array.put(it.toJSONObject()) }
        root.put("periods", array)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): TimePlan {
            val root = JSONObject(jsonStr)
            val name = root.getString("name")
            val plan = TimePlan(name)
            plan.morningStart = root.optString("morningStart", "07:30")
            plan.morningEnd = root.optString("morningEnd", "08:00")
            val array = root.getJSONArray("periods")
            for (i in 0 until array.length()) {
                plan.periods.add(TimePeriod.fromJSONObject(array.getJSONObject(i)))
            }
            return plan
        }
    }
}

data class TimePeriod(
    var start: String, // HH:mm
    var end: String    // HH:mm
) {
    fun toJSONObject(): JSONObject {
        return JSONObject().put("start", start).put("end", end)
    }
    companion object {
        fun fromJSONObject(obj: JSONObject): TimePeriod {
            return TimePeriod(obj.getString("start"), obj.getString("end"))
        }
    }
}

/**
 * 课程表方案模型
 */
data class SchedulePlan(
    var name: String,
    var timePlanName: String, // 绑定的时间方案名
    var courses: MutableList<String> = mutableListOf(),
    var morningRead: String = "",
    var isMorningReadEnabled: Boolean = true,
    var bindingDays: MutableList<String> = mutableListOf() // 格式: "odd_1", "even_5" 等
) {
    fun toJson(): String {
        val root = JSONObject()
        root.put("name", name)
        root.put("timePlanName", timePlanName)
        root.put("morningRead", morningRead)
        root.put("mrEnabled", isMorningReadEnabled)
        val cArray = JSONArray()
        courses.forEach { cArray.put(it) }
        root.put("courses", cArray)
        val dArray = JSONArray()
        bindingDays.forEach { dArray.put(it) }
        root.put("days", dArray)
        return root.toString()
    }

    companion object {
        fun fromJson(jsonStr: String): SchedulePlan {
            val root = JSONObject(jsonStr)
            val plan = SchedulePlan(
                name = root.getString("name"),
                timePlanName = root.getString("timePlanName"),
                morningRead = root.optString("morningRead", ""),
                isMorningReadEnabled = root.optBoolean("mrEnabled", true)
            )
            val cArray = root.getJSONArray("courses")
            for (i in 0 until cArray.length()) plan.courses.add(cArray.getString(i))
            val dArray = root.getJSONArray("days")
            for (i in 0 until dArray.length()) plan.bindingDays.add(dArray.getString(i))
            return plan
        }
    }
}