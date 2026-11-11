package com.enenhhh.smartschedule.ui.timetable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import com.enenhhh.smartschedule.ui.motion.motionEnabled
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Weekend
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.data.TempSwaps
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.CourseChip
import com.enenhhh.smartschedule.ui.components.EmptyState
import com.enenhhh.smartschedule.ui.components.GlassSheet
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.nav.Route
import com.enenhhh.smartschedule.ui.today.topLevelContentPadding
import com.enenhhh.smartschedule.util.AppClock
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import java.time.LocalDate

@Composable
fun TimetableScreen() {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val version = ScheduleManager.version

    val today = AppClock.today
    val timetable = remember(version) { manager.getActiveTimetable() }
    val timePlan = remember(version) { manager.resolveTimePlan(timetable) }
    val all = remember(version) { manager.getTimetables() }
    val todayView = remember(version) { manager.dayView(today) }

    var showOdd by rememberSaveable { mutableStateOf(todayView.isOdd) }
    // 跨周（单双周翻转）时让预览跟着走；用户手动切换后在同一周内保持不变
    LaunchedEffect(todayView.isOdd) { showOdd = todayView.isOdd }
    var showWeekend by rememberSaveable { mutableStateOf(Prefs.isShowWeekend(context)) }
    var picked by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var showSwitcher by remember { mutableStateOf(false) }

    val motion = motionEnabled()
    val nowMin = AppClock.nowMinutes
    val currentPeriod = timePlan?.periods?.indexOfFirst { nowMin in it.startMinutes until it.endMinutes }?.takeIf { it >= 0 }
    val days = remember(showWeekend, timetable) {
        if (showWeekend || timetable?.weekendUsed() == true) (1..7).toList() else (1..5).toList()
    }

    LazyColumn(
        contentPadding = topLevelContentPadding(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("课表", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        buildString {
                            append(timetable?.name ?: "未创建")
                            if (todayView.weekNumber >= 1) append(" · 本周第 ${todayView.weekNumber} 周（${if (todayView.isOdd) "单" else "双"}周）")
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalIconButton(onClick = { nav.push(Route.AiImport("timetable")) }) {
                    Icon(Icons.Rounded.AutoAwesome, contentDescription = "AI 拍照导入课表")
                }
                if (timetable != null) {
                    Spacer(Modifier.width(6.dp))
                    FilledTonalIconButton(
                        onClick = {
                            showWeekend = !showWeekend
                            Prefs.setShowWeekend(context, showWeekend)
                        },
                        colors = if (showWeekend) IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary
                        ) else IconButtonDefaults.filledTonalIconButtonColors()
                    ) { Icon(Icons.Rounded.Weekend, contentDescription = "显示周末") }
                    Spacer(Modifier.width(6.dp))
                    FilledTonalButton(onClick = { nav.push(Route.TimetableEditor(timetable.id)) }) {
                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("编辑")
                    }
                }
            }
        }

        if (timetable == null) {
            item {
                Box(Modifier.fillMaxWidth().glassCard()) {
                    EmptyState(
                        icon = Icons.Rounded.CalendarViewWeek,
                        title = "还没有课表",
                        description = "创建后在网格里点一下格子就能填课，支持单双周不同。",
                        actionText = "创建课表",
                        onAction = {
                            val tt = manager.createTimetable("我的课表", null)
                            nav.push(Route.TimetableEditor(tt.id))
                        }
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().glassCard()) {
                    SettingRow(
                        title = "用 AI 拍照导入",
                        subtitle = "拍一张课表，交给 DeepSeek 等 AI 助手识别，粘贴回来就填好了",
                        icon = Icons.Rounded.AutoAwesome,
                        onClick = { nav.push(Route.AiImport("timetable")) }
                    )
                }
            }
        } else {
            item {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    PillTabs(
                        options = listOf(true, false),
                        selected = showOdd,
                        onSelect = { showOdd = it },
                        label = { if (it) "单周" else "双周" },
                        modifier = Modifier.weight(1f)
                    )
                    if (all.size > 1) {
                        Spacer(Modifier.width(8.dp))
                        TextButton(onClick = { showSwitcher = true }) { Text("切换课表") }
                    }
                }
                if (!timetable.hasAnyEvenVariant()) {
                    Text(
                        "这张课表每周都一样；需要单双周不同时，在编辑页点某一天的表头开启。",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp, top = 6.dp)
                    )
                }
            }
            item {
                Box(Modifier.fillMaxWidth().glassCard()) {
                    if (timePlan == null) {
                        EmptyState(
                            icon = Icons.Rounded.CalendarViewWeek,
                            title = "还没有时间方案",
                            description = "先设置每天有几节课、几点上课。",
                            actionText = "去设置",
                            onAction = { nav.push(Route.TimePlans) }
                        )
                    } else {
                        AnimatedContent(targetState = showOdd, transitionSpec = {
                            (fadeIn(tween(if (motion) 240 else 0)) + slideInVertically(tween(if (motion) 300 else 0)) { it / 20 }) togetherWith fadeOut(tween(if (motion) 100 else 0))
                        }, label = "parity-grid") { odd ->
                        WeekGrid(
                            timetable = timetable,
                            timePlan = timePlan,
                            isOdd = odd,
                            days = days,
                            todayIndex = if (odd == todayView.isOdd) todayView.dayIndex else null,
                            currentPeriod = if (odd == todayView.isOdd) currentPeriod else null,
                            shortName = { manager.getShortName(it) },
                            onCellClick = { d, p -> picked = d to p }
                        )
                        }
                    }
                }
            }
            item {
                Text(
                    "点格子查看详情 · 时间方案：${timePlan?.name ?: "-"}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 6.dp)
                )
            }
        }
    }

    picked?.let { (d, p) ->
        val tt = timetable ?: return@let
        val plan = timePlan ?: return@let
        val name = tt.day(d).courseAt(showOdd, p)
        val period = plan.periods.getOrNull(p)
        val isToday = d == todayView.dayIndex && showOdd == todayView.isOdd
        GlassSheet(onDismiss = { picked = null }) {
            Text(
                if (name.isBlank()) "没有课" else name,
                style = MaterialTheme.typography.headlineSmall
            )
            Text(
                "${DateUtils.dayName(d)} 第 ${p + 1} 节 · ${period?.start ?: ""} – ${period?.end ?: ""} · ${if (showOdd) "单周" else "双周"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            if (name.isNotBlank()) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    CourseChip(text = "简写 ${manager.getShortName(name)}")
                    val count = tt.days.sumOf { day -> day.coursesFor(showOdd).count { it == name } }
                    CourseChip(text = "本周 $count 节", color = MaterialTheme.colorScheme.secondaryContainer)
                }
                Spacer(Modifier.height(8.dp))
            }
            SettingRow(title = "编辑这张课表", icon = Icons.Rounded.Edit, onClick = {
                picked = null
                nav.push(Route.TimetableEditor(tt.id))
            })
            if (isToday) {
                SettingRow(
                    title = "今天这节临时调课",
                    subtitle = "在「今日」页长按也可以",
                    icon = Icons.Rounded.CalendarViewWeek,
                    onClick = {
                        picked = null
                        nav.switchTab(Route.Today)
                    }
                )
                if (p in todayView.swappedPeriods) {
                    SettingRow(title = "撤销今天的临时调课", onClick = {
                        TempSwaps.remove(context, today, p)
                        picked = null
                    })
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showSwitcher) {
        GlassSheet(onDismiss = { showSwitcher = false }) {
            Text("切换课表", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            all.forEach { tt ->
                SettingRow(
                    title = tt.name,
                    subtitle = if (tt.id == timetable?.id) "使用中" else "时间方案：${tt.timePlanName}",
                    onClick = {
                        manager.setActiveTimetable(tt.id)
                        showSwitcher = false
                    },
                    showChevron = false
                )
            }
            TextButton(onClick = { showSwitcher = false; nav.push(Route.Timetables) }) { Text("管理全部课表") }
            Spacer(Modifier.height(8.dp))
        }
    }
}
