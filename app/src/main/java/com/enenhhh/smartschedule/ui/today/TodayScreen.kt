package com.enenhhh.smartschedule.ui.today

import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material.icons.rounded.SwapHoriz
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.HomeworkRepository
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.data.TempSwaps
import com.enenhhh.smartschedule.models.DayView
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.ui.app.BottomNavReserve
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.CourseChip
import com.enenhhh.smartschedule.ui.components.EmptyState
import com.enenhhh.smartschedule.ui.components.GlassSheet
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.courseColor
import com.enenhhh.smartschedule.ui.components.onCourseColor
import com.enenhhh.smartschedule.ui.glass.glassAccentCard
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.nav.Route
import com.enenhhh.smartschedule.util.AppClock
import com.enenhhh.smartschedule.util.DateUtils
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalTime

/** 顶层页面统一的内容边距：顶部贴状态栏，底部让出导航胶囊 */
@Composable
fun topLevelContentPadding(): PaddingValues {
    val density = LocalDensity.current
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
    return PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = bottom + BottomNavReserve + 8.dp)
}

private sealed class Status {
    data class InClass(val index: Int, val name: String, val remainMin: Int, val progress: Float) : Status()
    data class Break(val nextIndex: Int, val name: String, val inMin: Int) : Status()
    data class BeforeFirst(val name: String, val startTime: String, val inMin: Int) : Status()
    data object Finished : Status()
    data object NoClass : Status()
    data class MorningRead(val name: String, val remainMin: Int) : Status()
}

private fun computeStatus(view: DayView, nowMin: Int): Status {
    val plan = view.timePlan ?: return Status.NoClass
    if (!view.hasCourses) return Status.NoClass
    val periods = plan.periods
    // 晨读
    if (view.morningRead.isNotBlank()) {
        val ms = TimePlan.timeToMinutes(plan.morningStart)
        val me = TimePlan.timeToMinutes(plan.morningEnd)
        if (nowMin in ms until me) return Status.MorningRead(view.morningRead, me - nowMin)
    }
    val firstIdx = view.courses.indexOfFirst { it.isNotBlank() }
    val lastIdx = view.courses.indexOfLast { it.isNotBlank() }
    if (firstIdx < 0) return Status.NoClass
    periods.forEachIndexed { i, p ->
        val s = p.startMinutes
        val e = p.endMinutes
        if (nowMin in s until e && view.courses.getOrNull(i)?.isNotBlank() == true) {
            val progress = ((nowMin - s).toFloat() / (e - s).coerceAtLeast(1)).coerceIn(0f, 1f)
            return Status.InClass(i, view.courses[i], e - nowMin, progress)
        }
    }
    // 下一节
    for (i in periods.indices) {
        if (view.courses.getOrNull(i).isNullOrBlank()) continue
        val s = periods[i].startMinutes
        if (s > nowMin) {
            return if (i == firstIdx) Status.BeforeFirst(view.courses[i], periods[i].start, s - nowMin)
            else Status.Break(i, view.courses[i], s - nowMin)
        }
    }
    if (lastIdx >= 0 && nowMin >= (periods.getOrNull(lastIdx)?.endMinutes ?: 0)) return Status.Finished
    return Status.NoClass
}

@Composable
fun TodayScreen() {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val version = ScheduleManager.version

    // 「今天」和「现在」来自全局时钟（回前台 / 日期广播 / 心跳都会刷新），不再在页面里冻结
    val today = AppClock.today
    val nowMin = AppClock.nowMinutes

    val timetable = remember(version) { manager.getActiveTimetable() }
    val todayView = remember(version, today) { manager.dayView(today) }
    val tomorrowView = remember(version, today) { manager.dayView(today.plusDays(1)) }
    val hwVersion = HomeworkRepository.version
    val homework = remember(version, hwVersion, today) {
        HomeworkRepository(context).getPending().filter { it.getDaysRemaining() <= 3 }.take(4)
    }
    val status = remember(todayView, nowMin) { computeStatus(todayView, nowMin) }

    var swapPeriod by remember { mutableStateOf<Int?>(null) }

    LazyColumn(
        contentPadding = topLevelContentPadding(),
        verticalArrangement = Arrangement.spacedBy(14.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        item {
            Header(todayView, timetable?.name)
        }
        if (timetable == null) {
            item {
                Box(Modifier.fillMaxWidth().glassCard()) {
                    EmptyState(
                        icon = Icons.Rounded.CalendarViewWeek,
                        title = "还没有课表",
                        description = "创建一张整周课表，或者用同学分享的 6 位分享码一键导入。",
                        actionText = "创建课表",
                        onAction = {
                            val tt = manager.createTimetable("我的课表", null)
                            nav.push(Route.TimetableEditor(tt.id))
                        }
                    )
                    TextButton(
                        onClick = { nav.push(Route.DataManagement) },
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 8.dp)
                    ) { Text("用分享码导入") }
                }
            }
        } else {
            item { HeroCard(status, todayView) }
            item { SectionTitle("今日课程") }
            if (!todayView.hasCourses) {
                item {
                    Box(Modifier.fillMaxWidth().glassCard().padding(20.dp)) {
                        Column {
                            Text("今天没有课", style = MaterialTheme.typography.titleMedium)
                            Text(
                                "好好休息，或者去课表页看看这周的安排。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                item {
                    TimelineCard(
                        view = todayView,
                        nowMin = nowMin,
                        status = status,
                        onLongPress = { swapPeriod = it }
                    )
                }
            }
            item { SectionTitle("明天") }
            item { TomorrowCard(tomorrowView, manager) }
            if (homework.isNotEmpty()) {
                item { SectionTitle("即将到期的作业") }
                item {
                    Column(Modifier.fillMaxWidth().glassCard().padding(vertical = 6.dp)) {
                        homework.forEach { hw ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .combinedClickable(onClick = { nav.switchTab(Route.Homework) })
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Rounded.Checklist, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(hw.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    if (hw.subject.isNotBlank()) Text(hw.subject, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                val days = hw.getDaysRemaining()
                                val label = when {
                                    days < 0 -> "已逾期"
                                    days == 0 -> "今天截止"
                                    days == 1 -> "明天截止"
                                    else -> "${days} 天后"
                                }
                                Text(
                                    label,
                                    style = MaterialTheme.typography.labelLarge,
                                    color = if (days <= 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TextButton(onClick = { nav.push(Route.TimetableEditor(timetable.id)) }) {
                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("编辑课表")
                    }
                    TextButton(onClick = { nav.switchTab(Route.Timetable) }) {
                        Icon(Icons.Rounded.CalendarViewWeek, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("查看整周")
                    }
                }
            }
        }
    }

    swapPeriod?.let { period ->
        SwapSheet(
            view = todayView,
            period = period,
            manager = manager,
            onDismiss = { swapPeriod = null }
        )
    }
}

@Composable
private fun Header(view: DayView, timetableName: String?) {
    Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
        Text(
            DateUtils.prettyDate(view.date),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(2.dp))
        val weekText = if (view.weekNumber >= 1) "第 ${view.weekNumber} 周 · ${if (view.isOdd) "单周" else "双周"}" else "尚未开学 · ${if (view.isOdd) "单周" else "双周"}"
        Text(
            buildString {
                append(weekText)
                if (!timetableName.isNullOrBlank()) append(" · ").append(timetableName)
            },
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun HeroCard(status: Status, view: DayView) {
    val cs = MaterialTheme.colorScheme
    val total = view.courses.count { it.isNotBlank() }
    val done = view.timePlan?.periods?.withIndex()?.count { (i, p) ->
        view.courses.getOrNull(i)?.isNotBlank() == true && AppClock.nowMinutes >= p.endMinutes
    } ?: 0
    Column(
        Modifier
            .fillMaxWidth()
            .glassAccentCard(shape = RoundedCornerShape(28.dp))
            .padding(22.dp)
    ) {
        val (title, sub) = when (status) {
            is Status.InClass -> "正在上 ${status.name}" to "第 ${status.index + 1} 节 · 还剩 ${status.remainMin} 分钟"
            is Status.Break -> "课间休息" to "下一节 ${status.name}，${status.inMin} 分钟后开始"
            is Status.BeforeFirst -> "今天第一节是 ${status.name}" to "${status.startTime} 开始，还有 ${fmtMinutes(status.inMin)}"
            is Status.MorningRead -> "晨读 · ${status.name}" to "还剩 ${status.remainMin} 分钟"
            Status.Finished -> "今天的课都上完啦" to "共 $total 节，辛苦了 🎉"
            Status.NoClass -> "今天没有课" to "享受自由的一天"
        }
        Text(title, style = MaterialTheme.typography.headlineSmall, color = cs.onPrimaryContainer, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(sub, style = MaterialTheme.typography.bodyMedium, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
        if (status is Status.InClass) {
            Spacer(Modifier.height(16.dp))
            LinearWavyProgressIndicator(
                progress = { status.progress },
                modifier = Modifier.fillMaxWidth(),
                color = cs.primary,
                trackColor = cs.onPrimaryContainer.copy(alpha = 0.15f)
            )
        } else if (total > 0 && status != Status.NoClass) {
            Spacer(Modifier.height(16.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(6.dp)
                        .background(cs.onPrimaryContainer.copy(alpha = 0.12f), CircleShape)
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(if (total == 0) 0f else done.toFloat() / total)
                            .height(6.dp)
                            .background(cs.primary, CircleShape)
                    )
                }
                Spacer(Modifier.width(12.dp))
                Text("$done / $total 节", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer)
            }
        }
    }
}

private fun fmtMinutes(m: Int): String = if (m >= 60) "${m / 60} 小时 ${m % 60} 分钟" else "$m 分钟"

@Composable
private fun TimelineCard(view: DayView, nowMin: Int, status: Status, onLongPress: (Int) -> Unit) {
    val plan = view.timePlan ?: return
    val cs = MaterialTheme.colorScheme
    val manager = LocalScheduleManager.current
    Column(Modifier.fillMaxWidth().glassCard().padding(vertical = 8.dp)) {
        if (view.morningRead.isNotBlank()) {
            TimelineRow(
                time = plan.morningStart,
                timeEnd = plan.morningEnd,
                label = "晨读",
                name = view.morningRead,
                state = when {
                    status is Status.MorningRead -> RowState.Current
                    nowMin >= TimePlan.timeToMinutes(plan.morningEnd) -> RowState.Past
                    else -> RowState.Upcoming
                },
                swapped = false,
                leading = { Icon(Icons.Rounded.MenuBook, null, tint = cs.tertiary, modifier = Modifier.size(18.dp)) },
                onLongPress = null
            )
        }
        plan.periods.forEachIndexed { i, p ->
            val name = view.courses.getOrNull(i).orEmpty()
            val state = when {
                status is Status.InClass && status.index == i -> RowState.Current
                nowMin >= p.endMinutes -> RowState.Past
                else -> RowState.Upcoming
            }
            TimelineRow(
                time = p.start,
                timeEnd = p.end,
                label = "第 ${i + 1} 节",
                name = name,
                state = state,
                swapped = i in view.swappedPeriods,
                shortName = if (name.isNotBlank()) manager.getShortName(name) else "",
                onLongPress = { onLongPress(i) }
            )
        }
        Text(
            "长按某一节可以临时调课（只影响今天）",
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant.copy(alpha = 0.7f),
            modifier = Modifier.padding(start = 16.dp, top = 6.dp, bottom = 4.dp)
        )
    }
}

private enum class RowState { Past, Current, Upcoming }

@Composable
private fun TimelineRow(
    time: String,
    timeEnd: String,
    label: String,
    name: String,
    state: RowState,
    swapped: Boolean,
    shortName: String = "",
    leading: (@Composable () -> Unit)? = null,
    onLongPress: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    val alpha = if (state == RowState.Past) 0.5f else 1f
    val isCurrent = state == RowState.Current
    val chipColor = if (name.isBlank()) cs.surfaceVariant.copy(alpha = 0.5f) else courseColor(name)
    Row(
        Modifier
            .fillMaxWidth()
            .let { m -> if (onLongPress != null) m.combinedClickable(onClick = {}, onLongClick = onLongPress) else m }
            .background(if (isCurrent) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.width(52.dp)) {
            Text(time, style = MaterialTheme.typography.labelLarge, color = cs.onSurface.copy(alpha = alpha))
            Text(timeEnd, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = alpha))
        }
        Box(
            Modifier
                .size(10.dp)
                .background(
                    when (state) {
                        RowState.Current -> cs.primary
                        RowState.Past -> cs.outlineVariant
                        RowState.Upcoming -> cs.primary.copy(alpha = 0.35f)
                    },
                    CircleShape
                )
        )
        Spacer(Modifier.width(14.dp))
        if (leading != null) {
            leading(); Spacer(Modifier.width(8.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant.copy(alpha = alpha))
            if (name.isBlank()) {
                Text("没课", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant.copy(alpha = alpha * 0.8f))
            } else {
                Text(
                    name,
                    style = MaterialTheme.typography.titleMedium,
                    color = cs.onSurface.copy(alpha = alpha),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (swapped) {
            Row(
                Modifier
                    .background(cs.tertiaryContainer, RoundedCornerShape(8.dp))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Rounded.SwapHoriz, null, tint = cs.onTertiaryContainer, modifier = Modifier.size(14.dp))
                Spacer(Modifier.width(3.dp))
                Text("调课", style = MaterialTheme.typography.labelSmall, color = cs.onTertiaryContainer)
            }
            Spacer(Modifier.width(8.dp))
        }
        if (name.isNotBlank() && shortName.isNotBlank()) {
            Box(
                Modifier
                    .background(chipColor.copy(alpha = alpha), RoundedCornerShape(10.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp)
            ) {
                Text(shortName, style = MaterialTheme.typography.labelLarge, color = onCourseColor(chipColor).copy(alpha = alpha))
            }
        }
    }
}

@Composable
private fun TomorrowCard(view: DayView, manager: ScheduleManager) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "${DateUtils.dayName(view.dayIndex)} · ${if (view.isOdd) "单周" else "双周"}",
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(Modifier.weight(1f))
            Text(
                "${view.date.monthValue}月${view.date.dayOfMonth}日",
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(10.dp))
        if (!view.hasCourses) {
            Text("明天没有课", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
        } else {
            androidx.compose.foundation.layout.FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (view.morningRead.isNotBlank()) {
                    CourseChip(text = "晨读 ${manager.getShortName(view.morningRead)}", color = cs.tertiaryContainer)
                }
                view.courses.forEachIndexed { i, c ->
                    if (c.isNotBlank()) CourseChip(text = manager.getShortName(c), color = courseColor(c))
                }
            }
        }
    }
}

@Composable
private fun SwapSheet(view: DayView, period: Int, manager: ScheduleManager, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val known = remember { manager.knownCourseNames() }
    var custom by remember { mutableStateOf("") }
    val current = view.courses.getOrNull(period).orEmpty()
    GlassSheet(onDismiss = onDismiss) {
        Text("临时调课 · 第 ${period + 1} 节", style = MaterialTheme.typography.titleLarge)
        Text(
            "只影响 ${DateUtils.prettyDate(view.date)}，不会改动课表本身。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        androidx.compose.foundation.layout.FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            known.forEach { name ->
                CourseChip(
                    text = name,
                    selected = name == current,
                    onClick = {
                        TempSwaps.set(context, view.date, period, name)
                        onDismiss()
                    }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = custom,
            onValueChange = { custom = it },
            label = { Text("其它课程名") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                TextButton(
                    onClick = {
                        if (custom.isNotBlank()) {
                            TempSwaps.set(context, view.date, period, custom.trim())
                            onDismiss()
                        }
                    },
                    enabled = custom.isNotBlank()
                ) { Text("应用") }
            }
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(
                onClick = { TempSwaps.set(context, view.date, period, ""); onDismiss() },
                modifier = Modifier.weight(1f)
            ) { Text("这节临时没课") }
            if (period in view.swappedPeriods) {
                FilledTonalButton(
                    onClick = { TempSwaps.remove(context, view.date, period); onDismiss() },
                    modifier = Modifier.weight(1f)
                ) { Text("恢复原课") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}
