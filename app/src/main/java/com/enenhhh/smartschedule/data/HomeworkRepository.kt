package com.enenhhh.smartschedule.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

data class Homework(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val subject: String = "",
    val description: String = "",
    val startDate: String = "", // yyyy-MM-dd 布置日期
    val deadline: String = "", // yyyy-MM-dd 截止日期
    val completed: Boolean = false,
    val remind: Boolean = true
) {
    fun getDaysRemaining(): Int {
        if (deadline.isBlank()) return Int.MAX_VALUE
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
            val deadlineDate = fmt.parse(deadline) ?: return Int.MAX_VALUE
            val today = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.time
            val diff = deadlineDate.time - today.time
            val days = (diff / (1000 * 60 * 60 * 24)).toInt()
            days
        } catch (e: Exception) { Int.MAX_VALUE }
    }

    fun isOverdue(): Boolean = getDaysRemaining() < 0 && !completed

    fun toJson(): JSONObject {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("subject", subject)
            put("description", description)
            put("startDate", startDate)
            put("deadline", deadline)
            put("completed", completed)
            put("remind", remind)
        }
    }

    companion object {
        fun fromJson(obj: JSONObject): Homework {
            return Homework(
                id = obj.optString("id", UUID.randomUUID().toString()),
                title = obj.optString("title", ""),
                subject = obj.optString("subject", ""),
                description = obj.optString("description", ""),
                startDate = obj.optString("startDate", ""),
                deadline = obj.optString("deadline", ""),
                completed = obj.optBoolean("completed", false),
                remind = obj.optBoolean("remind", true)
            )
        }
    }
}

class HomeworkRepository(private val context: Context) {

    companion object {
        /** 任何写入都会 +1；Compose 侧用 remember(HomeworkRepository.version) 触发刷新 */
        var version by mutableIntStateOf(0)
            private set

        fun bumpVersion() { version++ }
    }

    private val file: File by lazy {
        File(context.getExternalFilesDir(null), "homework.json")
    }

    private val items = mutableListOf<Homework>()

    init {
        if (file.exists()) load()
    }

    fun getAll(): List<Homework> = items.toList()

    fun getPending(): List<Homework> = items.filter { !it.completed }.sortedBy { it.deadline }

    /** 外部（导入/恢复备份）直接改了文件之后调用，重新读取并通知界面 */
    fun reload() {
        items.clear()
        if (file.exists()) load()
        bumpVersion()
    }

    /** 整体替换（导入备份用） */
    fun replaceAll(list: List<Homework>) {
        items.clear()
        items.addAll(list)
        persist()
    }

    /** 合并：同 id 覆盖，其余追加 */
    fun mergeAll(list: List<Homework>) {
        list.forEach { hw ->
            val idx = items.indexOfFirst { it.id == hw.id }
            if (idx >= 0) items[idx] = hw else items.add(hw)
        }
        persist()
    }

    fun getCompleted(): List<Homework> = items.filter { it.completed }.sortedByDescending { it.deadline }

    fun add(item: Homework) {
        items.add(item)
        persist()
    }

    fun update(item: Homework) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) {
            items[idx] = item
            persist()
        }
    }

    fun delete(id: String) {
        items.removeAll { it.id == id }
        persist()
    }

    fun toggleComplete(id: String) {
        val idx = items.indexOfFirst { it.id == id }
        if (idx >= 0) {
            items[idx] = items[idx].copy(completed = !items[idx].completed)
            persist()
        }
    }

    private fun load() {
        items.clear()
        try {
            val arr = JSONArray(file.readText())
            for (i in 0 until arr.length()) {
                items.add(Homework.fromJson(arr.getJSONObject(i)))
            }
        } catch (e: Exception) {
            // 文件损坏则忽略
        }
    }

    private fun persist() {
        val arr = JSONArray()
        items.forEach { arr.put(it.toJson()) }
        runCatching {
            file.parentFile?.mkdirs()
            file.writeText(arr.toString(2))
        }
        bumpVersion()
    }
}