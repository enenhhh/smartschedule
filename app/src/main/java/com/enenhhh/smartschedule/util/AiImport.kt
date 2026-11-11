package com.enenhhh.smartschedule.util

import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.enenhhh.smartschedule.data.Homework
import com.enenhhh.smartschedule.data.HomeworkRepository
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.models.Timetable
import java.time.LocalDate

/**
 * 「AI 快速导入」里需要 Context 的部分：生成提示词、打开 AI 助手、写入数据、撤销。
 * 提示词规则和解析在 [AiImportParser]。
 */
object AiImport {

    /** 引导里列出的 AI 助手。装了 App 就直接打开，没装就打开网页版 */
    data class Assistant(val name: String, val packageName: String, val url: String)

    val ASSISTANTS = listOf(
        Assistant("DeepSeek", "com.deepseek.chat", "https://chat.deepseek.com/"),
        Assistant("豆包", "com.larus.nova", "https://www.doubao.com/chat/"),
        Assistant("Kimi", "com.moonshot.kimichat", "https://www.kimi.com/"),
        Assistant("元宝", "com.tencent.hunyuan.app.chat", "https://yuanbao.tencent.com/chat"),
    )

    fun open(ctx: Context, a: Assistant): Boolean {
        val launch = ctx.packageManager.getLaunchIntentForPackage(a.packageName)
        val intent = launch ?: Intent(Intent.ACTION_VIEW, Uri.parse(a.url))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return try { ctx.startActivity(intent); true } catch (_: ActivityNotFoundException) { false } catch (_: SecurityException) { false }
    }

    fun isInstalled(ctx: Context, a: Assistant): Boolean = ctx.packageManager.getLaunchIntentForPackage(a.packageName) != null

    // ------------------------------------------------------------------ 提示词

    fun timetablePrompt(manager: ScheduleManager): String {
        val plan = manager.resolveTimePlan(manager.getActiveTimetable())
        return AiImportParser.timetablePrompt(manager.knownCourseNames(), plan?.periods?.size ?: 0, plan?.periods?.firstOrNull()?.start)
    }

    fun homeworkPrompt(manager: ScheduleManager, today: LocalDate): String =
        AiImportParser.homeworkPrompt(manager.knownCourseNames(), today, defaultDeadline(manager, today))

    fun defaultDeadline(manager: ScheduleManager, today: LocalDate): LocalDate =
        AiImportParser.nextSchoolDay(today, manager.getActiveTimetable()?.weekendUsed() == true)

    fun parse(text: String, manager: ScheduleManager, today: LocalDate): AiImportParser.Parsed =
        AiImportParser.parse(text, today, manager.knownCourseNames(), manager.getCustomShortNames(), defaultDeadline(manager, today))

    // ------------------------------------------------------------------ 剪贴板

    fun copy(ctx: Context, label: String, text: String) {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
    }

    /** 读剪贴板文字；App 不在前台或没有文字时返回 null */
    fun readClipboard(ctx: Context): String? = runCatching {
        val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(ctx)?.toString()
    }.getOrNull()?.takeIf { it.isNotBlank() }

    // ------------------------------------------------------------------ 写入 + 撤销

    /** 导入前的样子，用于撤销 */
    class Undo internal constructor(private val restore: () -> Unit) {
        fun run() = restore()
    }

    /**
     * 写入识别出的课表。
     * @param target   要更新的课表；为 null 时新建一张（并设为使用中）
     * @param withTime 同时导入作息时间（AI 识别到了上下课时间才有意义）
     * @return 写入后的课表 + 撤销动作
     */
    fun applyTimetable(
        manager: ScheduleManager,
        r: AiImportParser.TimetableResult,
        target: Timetable?,
        newName: String,
        withTime: Boolean
    ): Pair<Timetable, Undo> {
        val prevActive = manager.getActiveTimetable()?.id
        val prevPlans = manager.getTimePlans()

        // 1. 时间方案
        var planName: String? = target?.timePlanName?.takeIf { manager.getTimePlan(it) != null }
        var createdPlan: String? = null
        var overwrittenPlan: TimePlan? = null
        if (withTime && r.periods.isNotEmpty()) {
            val current = planName?.let { manager.getTimePlan(it) }
            val sharedWithOthers = current != null && manager.getTimetables().any { it.id != target?.id && it.timePlanName == current.name }
            val updated = TimePlan(
                name = "",
                periods = r.periods,
                morningStart = r.morningStart ?: current?.morningStart ?: "07:30",
                morningEnd = r.morningEnd ?: current?.morningEnd ?: "08:00"
            )
            if (current != null && !sharedWithOthers) {
                overwrittenPlan = current
                manager.saveTimePlan(updated.copy(name = current.name))
            } else {
                val base = "${(target?.name ?: newName).take(24)} 作息"
                val name = uniqueName(base, prevPlans.map { it.name })
                manager.saveTimePlan(updated.copy(name = name))
                createdPlan = name
                planName = name
            }
        }
        if (planName == null) {
            planName = prevPlans.firstOrNull()?.name ?: TimePlan.defaultPlan().also { manager.saveTimePlan(it); createdPlan = it.name }.name
        }

        // 2. 课表：只覆盖 AI 输出了的那几天，其它天保持不变
        var tt = target?.copy(timePlanName = planName) ?: Timetable(
            name = uniqueName(newName.ifBlank { "我的课表" }, manager.getTimetables().map { it.name }),
            timePlanName = planName
        )
        r.days.forEach { (d, day) -> tt = tt.withDay(d, day) }
        manager.saveTimetable(tt)
        if (target == null) manager.setActiveTimetable(tt.id)

        val undo = Undo {
            if (target != null) manager.saveTimetable(target) else manager.deleteTimetable(tt.id)
            overwrittenPlan?.let { manager.saveTimePlan(it) }
            createdPlan?.let { manager.deleteTimePlan(it) }
            prevActive?.let { id -> if (manager.getTimetable(id) != null) manager.setActiveTimetable(id) }
        }
        return tt to undo
    }

    /**
     * 写入作业；返回撤销动作。
     * 每次都新建 [HomeworkRepository]（它在内存里缓存列表），撤销时也重新读文件，
     * 免得在提示条显示期间用户在作业页做的改动被旧列表覆盖。
     */
    fun applyHomework(ctx: Context, items: List<AiImportParser.HomeworkItem>, today: LocalDate): Undo {
        val repo = HomeworkRepository(ctx.applicationContext)
        val app = ctx.applicationContext
        val added = items.map { hw ->
            Homework(
                title = hw.title,
                subject = hw.subject,
                description = hw.note,
                startDate = DateUtils.format(minOf(today, hw.deadline)),
                deadline = DateUtils.format(hw.deadline)
            ).also { repo.add(it) }
        }
        return Undo { HomeworkRepository(app).let { r -> added.forEach { r.delete(it.id) } } }
    }

    /** 已经有同科目、同内容、同截止日期的作业，就算重复 */
    fun isDuplicate(existing: List<Homework>, hw: AiImportParser.HomeworkItem): Boolean =
        existing.any { it.subject == hw.subject && it.title.trim() == hw.title.trim() && it.deadline == DateUtils.format(hw.deadline) }

    private fun uniqueName(base: String, taken: List<String>): String {
        if (base !in taken) return base
        var i = 2
        while ("$base $i" in taken) i++
        return "$base $i"
    }
}
