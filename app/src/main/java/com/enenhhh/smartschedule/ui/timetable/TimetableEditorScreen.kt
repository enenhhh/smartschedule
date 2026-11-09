package com.enenhhh.smartschedule.ui.timetable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.models.DaySchedule
import com.enenhhh.smartschedule.models.Timetable
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.ConfirmDialog
import com.enenhhh.smartschedule.ui.components.CourseChip
import com.enenhhh.smartschedule.ui.components.GlassSheet
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.components.TextInputDialog
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.nav.Route
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs

@Composable
fun TimetableEditorScreen(id: String) {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val version = ScheduleManager.version

    var tt by remember { mutableStateOf(manager.getTimetable(id)) }
    val timePlan = remember(tt?.timePlanName, version) { manager.resolveTimePlan(tt) }
    val timePlans = remember(version) { manager.getTimePlans() }

    var showOdd by rememberSaveable { mutableStateOf(true) }
    var showWeekend by rememberSaveable { mutableStateOf(Prefs.isShowWeekend(context) || tt?.weekendUsed() == true) }
    var autoAdvance by rememberSaveable { mutableStateOf(true) }

    var editingCell by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var dayMenu by remember { mutableStateOf<Int?>(null) }
    var morningEdit by remember { mutableStateOf<Int?>(null) }
    var renameOpen by remember { mutableStateOf(false) }
    var timePlanPicker by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    fun update(newTt: Timetable) {
        tt = newTt
        manager.saveTimetable(newTt)
    }

    val table = tt
    if (table == null) {
        SubPage(title = "课表不存在") { padding ->
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("这张课表已被删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        return
    }

    val days = if (showWeekend) (1..7).toList() else (1..5).toList()

    SubPage(
        title = table.name,
        subtitle = "点格子填课 · 自动保存",
        actions = {
            IconButton(onClick = { timePlanPicker = true }) {
                Icon(Icons.Rounded.Schedule, contentDescription = "时间方案")
            }
            Box {
                IconButton(onClick = { moreMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "更多")
                }
                DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                    DropdownMenuItem(text = { Text("重命名") }, onClick = { moreMenu = false; renameOpen = true })
                    DropdownMenuItem(
                        text = { Text(if (showWeekend) "隐藏周末" else "显示周末") },
                        onClick = {
                            moreMenu = false
                            showWeekend = !showWeekend
                            Prefs.setShowWeekend(context, showWeekend)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("所有天：双周 = 单周") },
                        onClick = {
                            moreMenu = false
                            update(table.copy(days = table.days.map { it.copy(evenCourses = null, evenMorningRead = null) }))
                        }
                    )
                    DropdownMenuItem(text = { Text("清空整张课表") }, onClick = { moreMenu = false; confirmClear = true })
                    DropdownMenuItem(text = { Text("删除此课表") }, onClick = { moreMenu = false; confirmDelete = true })
                }
            }
        }
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillTabs(
                options = listOf(true, false),
                selected = showOdd,
                onSelect = { showOdd = it },
                label = { if (it) "单周" else "双周" },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (showOdd) "正在编辑单周（每周相同的天也在这里填）。" else "正在编辑双周。只有开启了「单双周不同」的天才会在双周使用这里的内容，其它天跟随单周。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
            Box(Modifier.fillMaxWidth().glassCard()) {
                if (timePlan == null) {
                    Column(Modifier.padding(20.dp)) {
                        Text("还没有时间方案", style = MaterialTheme.typography.titleMedium)
                        Text("先创建一个时间方案（每天几节课、几点上课）。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(10.dp))
                        Button(onClick = { nav.push(Route.TimePlanEditor(null)) }) { Text("创建时间方案") }
                    }
                } else {
                    WeekGrid(
                        timetable = table,
                        timePlan = timePlan,
                        isOdd = showOdd,
                        days = days,
                        todayIndex = null,
                        currentPeriod = null,
                        shortName = { manager.getShortName(it) },
                        editing = true,
                        onCellClick = { d, p -> editingCell = d to p },
                        onDayClick = { d -> dayMenu = d },
                        onMorningClick = { d -> morningEdit = d }
                    )
                }
            }
            Column(Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                Text("小技巧", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(6.dp))
                Text(
                    "• 点表头的「周几」：开启单双周不同、复制这一天到其它天、清空\n" +
                        "• 点最上面书本那一行：设置晨读\n" +
                        "• 填课时会自动跳到下一节，连续输入很快\n" +
                        "• 右上角时钟图标：更换时间方案（节数和上课时间）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // ---- 格子编辑
    editingCell?.let { (d, p) ->
        val plan = timePlan ?: return@let
        CourseInputSheet(
            dayIndex = d,
            period = p,
            periodCount = plan.periods.size,
            isOdd = showOdd,
            day = table.day(d),
            suggestions = remember(version) { manager.knownCourseNames() },
            autoAdvance = autoAdvance,
            onAutoAdvanceChange = { autoAdvance = it },
            onApply = { name, next ->
                var day = table.day(d)
                if (!showOdd && !day.alternates) day = day.copy(evenCourses = day.courses)
                update(table.withDay(d, day.withCourse(showOdd, p, name)))
                if (next && autoAdvance && p + 1 < plan.periods.size) editingCell = d to (p + 1) else editingCell = null
            },
            onDismiss = { editingCell = null }
        )
    }

    // ---- 晨读
    morningEdit?.let { d ->
        val day = table.day(d)
        TextInputDialog(
            title = "${DateUtils.dayName(d)}晨读 · ${if (showOdd) "单周" else "双周"}",
            initial = day.morningReadFor(showOdd),
            label = "晨读内容（留空表示没有晨读）",
            onDismiss = { morningEdit = null },
            validate = { null },
            onConfirm = { text ->
                val newDay = if (showOdd || !day.alternates) day.copy(morningRead = text) else day.copy(evenMorningRead = text)
                update(table.withDay(d, newDay))
            }
        )
    }

    // ---- 天菜单
    dayMenu?.let { d ->
        DaySheet(
            dayIndex = d,
            day = table.day(d),
            onDismiss = { dayMenu = null },
            onToggleAlternate = { on ->
                val day = table.day(d)
                update(table.withDay(d, if (on) day.copy(evenCourses = day.courses, evenMorningRead = day.morningRead) else day.copy(evenCourses = null, evenMorningRead = null)))
            },
            onCopyTo = { targets ->
                var newTt: Timetable = table
                val src = table.day(d)
                targets.forEach { t -> newTt = newTt.withDay(t, src) }
                update(newTt)
                dayMenu = null
            },
            onClear = {
                update(table.withDay(d, DaySchedule()))
                dayMenu = null
            }
        )
    }

    if (renameOpen) {
        TextInputDialog(
            title = "重命名课表",
            initial = table.name,
            onDismiss = { renameOpen = false },
            validate = { if (ScheduleManager.isValidPlanName(it)) null else "名称不能为空、不能超过 30 字或含特殊符号" },
            onConfirm = { update(table.copy(name = it)) }
        )
    }

    if (timePlanPicker) {
        GlassSheet(onDismiss = { timePlanPicker = false }) {
            Text("时间方案", style = MaterialTheme.typography.titleLarge)
            Text("决定每天有几节课、每节几点上。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            timePlans.forEach { plan ->
                SettingRow(
                    title = plan.name,
                    subtitle = "${plan.periods.size} 节 · ${plan.periods.firstOrNull()?.start ?: ""} – ${plan.periods.lastOrNull()?.end ?: ""}",
                    onClick = {
                        update(table.copy(timePlanName = plan.name))
                        timePlanPicker = false
                    },
                    showChevron = false,
                    trailing = if (plan.name == timePlan?.name) ({ Text("使用中", color = MaterialTheme.colorScheme.primary) }) else null
                )
            }
            Row {
                TextButton(onClick = { timePlanPicker = false; nav.push(Route.TimePlans) }) { Text("管理时间方案") }
                TextButton(onClick = { timePlanPicker = false; nav.push(Route.TimePlanEditor(null)) }) { Text("新建") }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (confirmClear) {
        ConfirmDialog(
            title = "清空整张课表？",
            message = "所有天的课程和晨读都会被清空，此操作不可撤销。",
            onDismiss = { confirmClear = false },
            onConfirm = { update(table.copy(days = List(7) { DaySchedule() })) },
            confirmText = "清空",
            destructive = true
        )
    }
    if (confirmDelete) {
        ConfirmDialog(
            title = "删除「${table.name}」？",
            message = "删除后无法恢复。如果这是唯一的课表，首页将显示为空。",
            onDismiss = { confirmDelete = false },
            onConfirm = {
                manager.deleteTimetable(table.id)
                nav.pop()
            },
            confirmText = "删除",
            destructive = true
        )
    }
}

@Composable
private fun CourseInputSheet(
    dayIndex: Int,
    period: Int,
    periodCount: Int,
    isOdd: Boolean,
    day: DaySchedule,
    suggestions: List<String>,
    autoAdvance: Boolean,
    onAutoAdvanceChange: (Boolean) -> Unit,
    onApply: (name: String, next: Boolean) -> Unit,
    onDismiss: () -> Unit
) {
    val current = day.courseAt(isOdd, period)
    var text by remember(dayIndex, period, isOdd) { mutableStateOf(current) }
    val focus = remember { FocusRequester() }
    LaunchedEffect(dayIndex, period) { runCatching { focus.requestFocus() } }

    GlassSheet(onDismiss = onDismiss) {
        Text(
            "${DateUtils.dayName(dayIndex)} · 第 ${period + 1} 节 · ${if (isOdd) "单周" else "双周"}",
            style = MaterialTheme.typography.titleLarge
        )
        if (!isOdd && !day.alternates) {
            Text(
                "这一天目前单双周相同；修改后会自动开启「单双周不同」。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text("课程名") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { onApply(text.trim(), true) }),
            modifier = Modifier.fillMaxWidth().focusRequester(focus)
        )
        if (suggestions.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text("常用课程", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(30).forEach { name ->
                    CourseChip(text = name, selected = name == text, onClick = { onApply(name, true) })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("填完自动跳到下一节", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
            Switch(checked = autoAdvance, onCheckedChange = onAutoAdvanceChange)
        }
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { onApply("", true) }, modifier = Modifier.weight(1f)) { Text("这节没课") }
            Button(onClick = { onApply(text.trim(), true) }, modifier = Modifier.weight(1f)) {
                Text(if (autoAdvance && period + 1 < periodCount) "下一节" else "完成")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun DaySheet(
    dayIndex: Int,
    day: DaySchedule,
    onDismiss: () -> Unit,
    onToggleAlternate: (Boolean) -> Unit,
    onCopyTo: (List<Int>) -> Unit,
    onClear: () -> Unit
) {
    var copyMode by remember { mutableStateOf(false) }
    var targets by remember { mutableStateOf(setOf<Int>()) }
    var confirmOff by remember { mutableStateOf(false) }
    GlassSheet(onDismiss = onDismiss) {
        Text(DateUtils.dayName(dayIndex), style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("单双周不同", style = MaterialTheme.typography.bodyLarge)
                Text(
                    if (day.alternates) "双周用单独的一套课；关闭后双周将跟随单周" else "开启后可以给双周填不同的课（先复制单周的内容）",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = day.alternates,
                onCheckedChange = { on -> if (on) onToggleAlternate(true) else confirmOff = true }
            )
        }
        if (!copyMode) {
            SettingRow(title = "复制这一天到其它天", onClick = { copyMode = true })
            SettingRow(title = "清空这一天", onClick = onClear, showChevron = false)
        } else {
            Text("复制到：", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                (1..7).filter { it != dayIndex }.forEach { d ->
                    FilterChip(
                        selected = d in targets,
                        onClick = { targets = if (d in targets) targets - d else targets + d },
                        label = { Text(DateUtils.dayName(d)) }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { copyMode = false }) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onCopyTo(targets.toList()) }, enabled = targets.isNotEmpty()) { Text("复制") }
            }
        }
        Spacer(Modifier.height(8.dp))
    }
    if (confirmOff) {
        ConfirmDialog(
            title = "关闭单双周不同？",
            message = "这一天双周单独填写的课程会被丢弃，之后双周跟随单周。",
            onDismiss = { confirmOff = false },
            onConfirm = { onToggleAlternate(false) },
            confirmText = "关闭",
            destructive = true
        )
    }
}
