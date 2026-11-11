package com.enenhhh.smartschedule.ui.nav

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * 应用内所有页面。顶层四个页面由底部导航切换，其余页面压栈。
 * 每个页面都能和一个字符串互转，用来在转屏 / 进程重建后恢复栈。
 */
sealed class Route(val key: String) {
    data object Today : Route("today")
    data object Timetable : Route("timetable")
    data object Homework : Route("homework")
    data object Settings : Route("settings")

    data object Timetables : Route("timetables")
    data class TimetableEditor(val id: String) : Route("tt_editor:$id")
    data object TimePlans : Route("timeplans")
    data class TimePlanEditor(val name: String?) : Route("tp_editor:${name.orEmpty()}")
    data object ShortNames : Route("shortnames")
    data object Semester : Route("semester")
    data object Appearance : Route("appearance")
    data object Reminders : Route("reminders")
    data object DataManagement : Route("data")
    data class LegalDocument(val id: String) : Route("legal:$id")
    data object Permissions : Route("permissions")
    data object About : Route("about")
    data object Onboarding : Route("onboarding")
    /** AI 快速导入；[mode] = "timetable" / "homework" */
    data class AiImport(val mode: String) : Route("ai_import:$mode")

    val isTopLevel: Boolean get() = this is Today || this is Timetable || this is Homework || this is Settings

    companion object {
        val topLevel: List<Route> get() = listOf(Today, Timetable, Homework, Settings)

        fun fromKey(key: String): Route = when {
            key == "today" -> Today
            key == "timetable" -> Timetable
            key == "homework" -> Homework
            key == "settings" -> Settings
            key == "timetables" -> Timetables
            key.startsWith("tt_editor:") -> TimetableEditor(key.removePrefix("tt_editor:"))
            key == "timeplans" -> TimePlans
            key.startsWith("tp_editor:") -> TimePlanEditor(key.removePrefix("tp_editor:").ifEmpty { null })
            key == "shortnames" -> ShortNames
            key == "semester" -> Semester
            key == "appearance" -> Appearance
            key == "reminders" -> Reminders
            key == "data" -> DataManagement
            key.startsWith("legal:") -> LegalDocument(key.removePrefix("legal:"))
            key == "permissions" -> Permissions
            key == "about" -> About
            key == "onboarding" -> Onboarding
            key.startsWith("ai_import:") -> AiImport(key.removePrefix("ai_import:"))
            else -> Today
        }
    }
}

@Stable
class Navigator(initial: List<Route>) {
    val stack: SnapshotStateList<Route> = mutableStateListOf<Route>().apply { addAll(initial) }

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1

    /** 最近一次导航是否为前进（决定转场方向） */
    var lastMoveForward: Boolean = true
        private set

    /** 顶层页面切换：替换栈底，不累积历史 */
    fun switchTab(route: Route) {
        if (current == route) return
        lastMoveForward = true
        stack.clear()
        stack.add(route)
    }

    fun push(route: Route) {
        if (current == route) return
        lastMoveForward = true
        stack.add(route)
    }

    fun pop(): Boolean {
        if (stack.size <= 1) return false
        lastMoveForward = false
        stack.removeAt(stack.lastIndex)
        return true
    }

    fun replace(route: Route) {
        lastMoveForward = true
        if (stack.isNotEmpty()) stack.removeAt(stack.lastIndex)
        stack.add(route)
    }

    /** 回到某个顶层页（例如完成向导后） */
    fun resetTo(route: Route) {
        lastMoveForward = false
        stack.clear()
        stack.add(route)
    }
}

@Composable
fun rememberNavigator(start: Route): Navigator = rememberSaveable(
    saver = listSaver(
        save = { nav -> nav.stack.map { it.key } },
        restore = { keys -> Navigator(keys.map { Route.fromKey(it) }.ifEmpty { listOf(start) }) }
    )
) { Navigator(listOf(start)) }
