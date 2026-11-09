package com.enenhhh.smartschedule.ui.homework

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.Homework
import com.enenhhh.smartschedule.data.HomeworkRepository
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.ConfirmDialog
import com.enenhhh.smartschedule.ui.components.CourseChip
import com.enenhhh.smartschedule.ui.components.DatePickerDialogM3
import com.enenhhh.smartschedule.ui.components.EmptyState
import com.enenhhh.smartschedule.ui.components.GlassSheet
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.courseColor
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.today.topLevelContentPadding
import com.enenhhh.smartschedule.util.DateUtils
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

private enum class HwTab(val label: String) { Pending("待完成"), Done("已完成"), Gantt("时间线") }

@Composable
fun HomeworkScreen() {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val repo = remember { HomeworkRepository(context) }
    val version = HomeworkRepository.version
    val pending = remember(version) { repo.getPending() }
    val done = remember(version) { repo.getCompleted() }

    var tab by rememberSaveable { mutableStateOf(HwTab.Pending) }
    var editing by remember { mutableStateOf<Homework?>(null) }
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Homework?>(null) }

    val overdue = pending.count { it.isOverdue() }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            contentPadding = topLevelContentPadding(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                Column(Modifier.padding(horizontal = 4.dp)) {
                    Text("作业", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                    Text(
                        when {
                            pending.isEmpty() -> "没有待完成的作业，轻松一下 🎉"
                            overdue > 0 -> "${pending.size} 项待完成 · $overdue 项已逾期"
                            else -> "${pending.size} 项待完成"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (overdue > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            item {
                PillTabs(
                    options = HwTab.entries,
                    selected = tab,
                    onSelect = { tab = it },
                    label = { it.label },
                    modifier = Modifier.fillMaxWidth()
                )
            }
            when (tab) {
                HwTab.Pending -> {
                    if (pending.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().glassCard()) {
                                EmptyState(
                                    icon = Icons.Rounded.Checklist,
                                    title = "暂无作业",
                                    description = "点右下角添加一项，设置截止日期后首页会提醒你。"
                                )
                            }
                        }
                    } else {
                        items(pending, key = { it.id }) { hw ->
                            HomeworkCard(
                                modifier = Modifier.animateItem(),
                                hw = hw,
                                onToggle = {
                                    repo.toggleComplete(hw.id)
                                    scope.launch { snackbar.showSnackbar("已完成「${hw.title}」") }
                                },
                                onClick = { editing = hw },
                                onLongClick = { deleting = hw }
                            )
                        }
                    }
                }
                HwTab.Done -> {
                    if (done.isEmpty()) {
                        item {
                            Box(Modifier.fillMaxWidth().glassCard()) {
                                EmptyState(icon = Icons.Rounded.Check, title = "还没有完成的作业", description = "完成的作业会收在这里。")
                            }
                        }
                    } else {
                        items(done, key = { it.id }) { hw ->
                            HomeworkCard(
                                modifier = Modifier.animateItem(),
                                hw = hw,
                                onToggle = { repo.toggleComplete(hw.id) },
                                onClick = { editing = hw },
                                onLongClick = { deleting = hw }
                            )
                        }
                    }
                }
                HwTab.Gantt -> {
                    item {
                        Box(Modifier.fillMaxWidth().glassCard()) {
                            if (pending.isEmpty()) {
                                EmptyState(icon = Icons.Rounded.Checklist, title = "没有可展示的作业", description = "添加作业后这里会显示每项作业的起止时间线。")
                            } else {
                                GanttChart(pending)
                            }
                        }
                    }
                }
            }
        }
        ExtendedFloatingActionButton(
            onClick = { creating = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("添加作业") },
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 20.dp, bottom = 108.dp)
        )
    }

    if (creating || editing != null) {
        HomeworkEditorSheet(
            initial = editing,
            subjects = remember { manager.knownCourseNames() },
            onDismiss = { creating = false; editing = null },
            onSave = { hw ->
                if (editing == null) repo.add(hw) else repo.update(hw)
                creating = false; editing = null
            },
            onDelete = editing?.let { hw -> { deleting = hw; editing = null } }
        )
    }
    deleting?.let { hw ->
        ConfirmDialog(
            title = "删除「${hw.title}」？",
            message = "删除后无法恢复。",
            onDismiss = { deleting = null },
            onConfirm = { repo.delete(hw.id) },
            confirmText = "删除",
            destructive = true
        )
    }
}

@Composable
private fun HomeworkCard(hw: Homework, onToggle: () -> Unit, onClick: () -> Unit, onLongClick: () -> Unit, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    val days = hw.getDaysRemaining()
    val (label, color) = when {
        hw.completed -> "已完成" to cs.onSurfaceVariant
        days < 0 -> "已逾期 ${-days} 天" to cs.error
        days == 0 -> "今天截止" to cs.error
        days == 1 -> "明天截止" to cs.tertiary
        days <= 3 -> "还剩 $days 天" to cs.tertiary
        days == Int.MAX_VALUE -> "未设截止" to cs.onSurfaceVariant
        else -> "还剩 $days 天" to cs.onSurfaceVariant
    }
    Row(
        modifier
            .fillMaxWidth()
            .glassCard()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp)
            .alpha(if (hw.completed) 0.7f else 1f),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier
                .size(44.dp)
                .clickable(onClick = onToggle),
            contentAlignment = Alignment.Center
        ) {
            Box(
                Modifier
                    .size(26.dp)
                    .background(if (hw.completed) cs.primary else Color.Transparent, CircleShape)
                    .border(2.dp, if (hw.completed) cs.primary else cs.outline, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                if (hw.completed) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(16.dp))
            }
        }
        Spacer(Modifier.width(6.dp))
        Column(Modifier.weight(1f)) {
            Text(
                hw.title,
                style = MaterialTheme.typography.titleMedium,
                textDecoration = if (hw.completed) TextDecoration.LineThrough else null,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (hw.description.isNotBlank()) {
                Text(hw.description, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (hw.subject.isNotBlank()) CourseChip(text = hw.subject)
                Text(label, style = MaterialTheme.typography.labelMedium, color = color, fontWeight = FontWeight.SemiBold)
                if (hw.deadline.isNotBlank()) {
                    Text(hw.deadline.removePrefix("${LocalDate.now().year}-"), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
                }
            }
        }
    }
}

@Composable
private fun GanttChart(items: List<Homework>) {
    val cs = MaterialTheme.colorScheme
    val today = LocalDate.now()
    val parsed = items.map { hw ->
        val end = DateUtils.parseDate(hw.deadline) ?: today
        val start = DateUtils.parseDate(hw.startDate)?.takeIf { !it.isAfter(end) } ?: minOf(today, end)
        Triple(hw, start, end)
    }
    val rangeStart = minOf(parsed.minOf { it.second }, today).minusDays(1)
    val rangeEnd = maxOf(parsed.maxOf { it.third }, today).plusDays(1)
    val totalDays = (ChronoUnit.DAYS.between(rangeStart, rangeEnd).toInt() + 1).coerceAtLeast(2)
    val dayWidth = 36.dp
    val labelWidth = 86.dp
    val scroll = rememberScrollState()

    Column(Modifier.padding(12.dp)) {
        Text("每条横杠是从布置到截止的时间段，竖线是今天。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(10.dp))
        Row {
            Column(Modifier.width(labelWidth)) {
                Spacer(Modifier.height(28.dp))
                parsed.forEach { (hw, _, _) ->
                    Box(Modifier.height(36.dp), contentAlignment = Alignment.CenterStart) {
                        Text(hw.title, style = MaterialTheme.typography.labelMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            Box(Modifier.weight(1f).horizontalScroll(scroll)) {
                Column(Modifier.width(dayWidth * totalDays)) {
                    Row(Modifier.height(28.dp)) {
                        for (i in 0 until totalDays) {
                            val d = rangeStart.plusDays(i.toLong())
                            Box(Modifier.width(dayWidth).fillMaxHeight(), contentAlignment = Alignment.Center) {
                                Text(
                                    "${d.monthValue}/${d.dayOfMonth}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (d == today) cs.primary else cs.onSurfaceVariant,
                                    fontWeight = if (d == today) FontWeight.Bold else null
                                )
                            }
                        }
                    }
                    parsed.forEach { (hw, start, end) ->
                        val offsetDays = ChronoUnit.DAYS.between(rangeStart, start).toInt()
                        val lengthDays = (ChronoUnit.DAYS.between(start, end).toInt() + 1).coerceAtLeast(1)
                        val color = if (hw.subject.isNotBlank()) courseColor(hw.subject) else cs.primaryContainer
                        Box(Modifier.height(36.dp).fillMaxWidth()) {
                            Box(
                                Modifier
                                    .offset(x = dayWidth * offsetDays)
                                    .width(dayWidth * lengthDays)
                                    .padding(vertical = 7.dp)
                                    .fillMaxHeight()
                                    .background(color, RoundedCornerShape(10.dp))
                                    .let { m -> if (hw.isOverdue()) m.border(2.dp, cs.error, RoundedCornerShape(10.dp)) else m }
                            )
                        }
                    }
                }
                // 今天竖线
                val todayOffset = ChronoUnit.DAYS.between(rangeStart, today).toInt()
                Box(
                    Modifier
                        .offset(x = dayWidth * todayOffset + dayWidth / 2 - 1.dp)
                        .width(2.dp)
                        .height(28.dp + 36.dp * parsed.size)
                        .background(cs.primary.copy(alpha = 0.6f))
                )
            }
        }
    }
}

@Composable
private fun HomeworkEditorSheet(
    initial: Homework?,
    subjects: List<String>,
    onDismiss: () -> Unit,
    onSave: (Homework) -> Unit,
    onDelete: (() -> Unit)?
) {
    val today = LocalDate.now()
    var title by remember { mutableStateOf(initial?.title ?: "") }
    var subject by remember { mutableStateOf(initial?.subject ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var startDate by remember { mutableStateOf(DateUtils.parseDate(initial?.startDate) ?: today) }
    var deadline by remember { mutableStateOf(DateUtils.parseDate(initial?.deadline) ?: today.plusDays(1)) }
    var remind by remember { mutableStateOf(initial?.remind ?: true) }
    var pickStart by remember { mutableStateOf(false) }
    var pickEnd by remember { mutableStateOf(false) }

    GlassSheet(onDismiss = onDismiss) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (initial == null) "添加作业" else "编辑作业", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            if (onDelete != null) {
                IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, contentDescription = "删除", tint = MaterialTheme.colorScheme.error) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("作业内容") },
            placeholder = { Text("例如：数学练习册 P12–P15") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        Text("科目", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            subjects.take(16).forEach { s ->
                CourseChip(text = s, selected = s == subject, onClick = { subject = if (subject == s) "" else s })
            }
        }
        OutlinedTextField(
            value = subject,
            onValueChange = { subject = it },
            label = { Text("科目（可自定义）") },
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp)
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = { pickStart = true }, modifier = Modifier.weight(1f)) {
                Text("布置 ${startDate.monthValue}/${startDate.dayOfMonth}")
            }
            FilledTonalButton(onClick = { pickEnd = true }, modifier = Modifier.weight(1f)) {
                Text("截止 ${deadline.monthValue}/${deadline.dayOfMonth}")
            }
        }
        Row(Modifier.padding(top = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("今天" to 0L, "明天" to 1L, "后天" to 2L, "下周" to 7L).forEach { (lbl, d) ->
                CourseChip(
                    text = lbl,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                    selected = deadline == today.plusDays(d),
                    onClick = { deadline = today.plusDays(d) }
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = description,
            onValueChange = { description = it },
            label = { Text("备注（可选）") },
            minLines = 2,
            maxLines = 4,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
        Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("临近截止时在首页提醒", modifier = Modifier.weight(1f))
            Switch(checked = remind, onCheckedChange = { remind = it })
        }
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = {
                val base = initial ?: Homework(title = title.trim())
                onSave(
                    base.copy(
                        title = title.trim(),
                        subject = subject.trim(),
                        description = description.trim(),
                        startDate = DateUtils.format(startDate),
                        deadline = DateUtils.format(deadline),
                        remind = remind
                    )
                )
            },
            enabled = title.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) { Text("保存") }
        Spacer(Modifier.height(8.dp))
    }

    if (pickStart) DatePickerDialogM3(initial = startDate, onDismiss = { pickStart = false }, onConfirm = { startDate = it; if (deadline.isBefore(it)) deadline = it })
    if (pickEnd) DatePickerDialogM3(initial = deadline, onDismiss = { pickEnd = false }, onConfirm = { deadline = it })
}
