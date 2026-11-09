package com.enenhhh.smartschedule.ui.timeplan

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.models.TimePeriod
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.ConfirmDialog
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.components.TimePickerDialogM3
import kotlinx.coroutines.launch

/**
 * 时间方案编辑：
 *  - 「快速生成」按第一节开始时间 / 每节时长 / 课间 / 节数 / 午休 一键铺满
 *  - 每一节都可以单独点开改时间
 *  - 顶部 ✓ 保存；有未保存改动时返回会提示
 */
@Composable
fun TimePlanEditorScreen(name: String?) {
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    val original = remember(name) { manager.getTimePlan(name) }
    val isNew = original == null
    var plan by remember(name) { mutableStateOf(original ?: TimePlan.defaultPlan(suggestName(manager))) }
    var dirty by remember { mutableStateOf(isNew) }
    var confirmLeave by remember { mutableStateOf(false) }

    // 快速生成参数
    var genStart by rememberSaveable { mutableStateOf(original?.periods?.firstOrNull()?.start ?: "08:00") }
    var genLesson by rememberSaveable { mutableIntStateOf(40) }
    var genBreak by rememberSaveable { mutableIntStateOf(10) }
    var genCount by rememberSaveable { mutableIntStateOf(original?.periods?.size?.coerceIn(1, 14) ?: 8) }
    var genLunchAfter by rememberSaveable { mutableIntStateOf(4) }
    var genLunch by rememberSaveable { mutableIntStateOf(120) }

    var picker by remember { mutableStateOf<TimeTarget?>(null) }

    fun update(p: TimePlan) { plan = p; dirty = true }

    val nameError = when {
        !ScheduleManager.isValidPlanName(plan.name) -> "名称不能为空、不能超过 30 字或含特殊符号"
        plan.name != original?.name && manager.getTimePlan(plan.name) != null -> "已存在同名方案"
        else -> null
    }
    val orderError = plan.periods.withIndex().firstOrNull { (i, p) ->
        p.endMinutes <= p.startMinutes || (i > 0 && p.startMinutes < plan.periods[i - 1].endMinutes)
    }?.let { "第 ${it.index + 1} 节时间不对：结束要晚于开始，且不能早于上一节结束" }
    val canSave = nameError == null && orderError == null && plan.periods.isNotEmpty()

    fun save() {
        manager.saveTimePlan(plan, original?.name)
        dirty = false
        scope.launch { snackbar.showSnackbar("已保存「${plan.name}」") }
        nav.pop()
    }

    fun back() { if (dirty) confirmLeave = true else nav.pop() }
    BackHandler(enabled = dirty) { confirmLeave = true }

    SubPage(
        title = if (isNew) "新建时间方案" else "编辑时间方案",
        subtitle = if (dirty) "有未保存的修改" else null,
        onBack = { back() },
        actions = {
            IconButton(onClick = { save() }, enabled = canSave) {
                Icon(Icons.Rounded.Check, contentDescription = "保存")
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
            OutlinedTextField(
                value = plan.name,
                onValueChange = { update(plan.copy(name = it)) },
                label = { Text("方案名称") },
                singleLine = true,
                isError = nameError != null,
                supportingText = nameError?.let { { Text(it) } },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )

            SectionTitle("快速生成")
            GlassGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("第一节开始", modifier = Modifier.weight(1f))
                        FilledTonalButton(onClick = { picker = TimeTarget.GenStart }) { Text(genStart) }
                    }
                    StepperRow("每节时长", genLesson, 20, 90, 5, " 分钟") { genLesson = it }
                    StepperRow("课间休息", genBreak, 0, 30, 5, " 分钟") { genBreak = it }
                    StepperRow("每天节数", genCount, 1, 14, 1, " 节") { genCount = it; if (genLunchAfter > it) genLunchAfter = it }
                    StepperRow("午休在第几节后", genLunchAfter, 0, genCount, 1, if (genLunchAfter == 0) "（不午休）" else " 节后") { genLunchAfter = it }
                    if (genLunchAfter > 0) StepperRow("午休时长", genLunch, 30, 180, 10, " 分钟") { genLunch = it }
                    Spacer(Modifier.height(8.dp))
                    Button(
                        onClick = {
                            update(plan.copy(periods = TimePlan.generate(genStart, genLesson, genBreak, genCount, genLunchAfter, genLunch)))
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("按以上规则生成 ${genCount} 节")
                    }
                }
            }

            SectionTitle("节次时间（点时间可单独修改）")
            GlassGroup {
                if (plan.periods.isEmpty()) {
                    Text(
                        "还没有节次，先用上面的快速生成，或点下面手动添加。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                plan.periods.forEachIndexed { i, p ->
                    Row(
                        Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("第 ${i + 1} 节", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                        TimeChip(p.start) { picker = TimeTarget.Start(i) }
                        Text(" – ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TimeChip(p.end) { picker = TimeTarget.End(i) }
                        IconButton(onClick = { update(plan.copy(periods = plan.periods.filterIndexed { j, _ -> j != i })) }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    if (i != plan.periods.lastIndex) RowDivider()
                }
                TextButton(
                    onClick = {
                        val last = plan.periods.lastOrNull()
                        val start = if (last != null) last.endMinutes + genBreak else TimePlan.timeToMinutes(genStart)
                        update(plan.copy(periods = plan.periods + TimePeriod(TimePlan.minutesToTime(start), TimePlan.minutesToTime(start + genLesson))))
                    },
                    modifier = Modifier.padding(horizontal = 8.dp)
                ) {
                    Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("在末尾添加一节")
                }
            }
            if (orderError != null) {
                Text(orderError, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(horizontal = 8.dp))
            }

            SectionTitle("晨读时段")
            GlassGroup {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("晨读时间", style = MaterialTheme.typography.bodyLarge)
                        Text("只在课表里填了晨读的那天显示", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    TimeChip(plan.morningStart) { picker = TimeTarget.MorningStart }
                    Text(" – ", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    TimeChip(plan.morningEnd) { picker = TimeTarget.MorningEnd }
                }
            }

            Button(onClick = { save() }, enabled = canSave, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                Text(if (isNew) "创建方案" else "保存修改")
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    picker?.let { target ->
        val initial = when (target) {
            TimeTarget.GenStart -> genStart
            TimeTarget.MorningStart -> plan.morningStart
            TimeTarget.MorningEnd -> plan.morningEnd
            is TimeTarget.Start -> plan.periods[target.index].start
            is TimeTarget.End -> plan.periods[target.index].end
        }
        val minutes = TimePlan.timeToMinutes(initial)
        TimePickerDialogM3(
            initialHour = minutes / 60,
            initialMinute = minutes % 60,
            title = when (target) {
                TimeTarget.GenStart -> "第一节开始时间"
                TimeTarget.MorningStart -> "晨读开始"
                TimeTarget.MorningEnd -> "晨读结束"
                is TimeTarget.Start -> "第 ${target.index + 1} 节开始"
                is TimeTarget.End -> "第 ${target.index + 1} 节结束"
            },
            onDismiss = { picker = null },
            onConfirm = { h, m ->
                val t = TimePlan.minutesToTime(h * 60 + m)
                when (target) {
                    TimeTarget.GenStart -> genStart = t
                    TimeTarget.MorningStart -> update(plan.copy(morningStart = t))
                    TimeTarget.MorningEnd -> update(plan.copy(morningEnd = t))
                    is TimeTarget.Start -> update(plan.copy(periods = plan.periods.mapIndexed { i, p -> if (i == target.index) p.copy(start = t) else p }))
                    is TimeTarget.End -> update(plan.copy(periods = plan.periods.mapIndexed { i, p -> if (i == target.index) p.copy(end = t) else p }))
                }
            }
        )
    }

    if (confirmLeave) {
        ConfirmDialog(
            title = "放弃修改？",
            message = "这个时间方案还有没保存的改动。",
            onDismiss = { confirmLeave = false },
            onConfirm = { dirty = false; nav.pop() },
            confirmText = "放弃",
            destructive = true
        )
    }
}

private sealed interface TimeTarget {
    data object GenStart : TimeTarget
    data object MorningStart : TimeTarget
    data object MorningEnd : TimeTarget
    data class Start(val index: Int) : TimeTarget
    data class End(val index: Int) : TimeTarget
}

private fun suggestName(manager: ScheduleManager): String {
    val names = manager.getTimePlans().map { it.name }.toSet()
    if ("默认作息" !in names) return "默认作息"
    var i = 2
    while ("作息 $i" in names) i++
    return "作息 $i"
}

@Composable
private fun TimeChip(text: String, onClick: () -> Unit) {
    Box(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun StepperRow(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    suffix: String,
    onChange: (Int) -> Unit
) {
    Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, modifier = Modifier.weight(1f))
            Text("$value$suffix", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = if (max > min) ((max - min) / step - 1).coerceAtLeast(0) else 0
        )
    }
}
