package com.enenhhh.smartschedule.ui

import android.app.DatePickerDialog
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.R
import com.enenhhh.smartschedule.data.Homework
import com.enenhhh.smartschedule.data.repository.AppDataRepository
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.ui.components.AppTextField
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

private val subjectColors = listOf(
    Color(0xFF2196F3), Color(0xFF4CAF50), Color(0xFFFF9800),
    Color(0xFF9C27B0), Color(0xFFE91E63), Color(0xFF00BCD4),
    Color(0xFF795548), Color(0xFF607D8B), Color(0xFFFF5722),
    Color(0xFF3F51B5), Color(0xFF8BC34A), Color(0xFFCDDC39),
    Color(0xFF009688), Color(0xFFF44336), Color(0xFF673AB7)
)

/**
 * 科目配色。以前用「先出现先分配」的全局可变 Map，
 * 同一门课在不同界面/不同启动之间颜色会变；改成按名字哈希取色，稳定可复现。
 */
private fun getSubjectColor(subject: String): Color {
    if (subject.isBlank()) return Color.Gray
    val index = (subject.hashCode().toLong() and 0xFFFFFFFFL) % subjectColors.size
    return subjectColors[index.toInt()]
}

private fun todayStr(): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())

private fun getWeekStart(offset: Int): Calendar {
    val cal = Calendar.getInstance()
    cal.add(Calendar.WEEK_OF_YEAR, offset)
    cal.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal
}

private fun formatDate(cal: Calendar): String = SimpleDateFormat("M.d", Locale.getDefault()).format(cal.time)
private fun formatDateFull(cal: Calendar): String = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)

@Composable
fun HomeworkScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { AppDataRepository(context) }
    var allHomework by remember {
        mutableStateOf(repository.getPendingHomework() + repository.getCompletedHomework())
    }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingHomework by remember { mutableStateOf<Homework?>(null) }
    var showActionMenu by remember { mutableStateOf<Homework?>(null) }
    var weekOffset by remember { mutableStateOf(0) }

    fun refresh() {
        allHomework = repository.getPendingHomework() + repository.getCompletedHomework()
        NotificationHelper.updatePersistentNotification(context)
    }

    val weekStart = remember(weekOffset) { getWeekStart(weekOffset) }
    val weekEnd = remember(weekOffset) {
        getWeekStart(weekOffset).apply { add(Calendar.DAY_OF_YEAR, 6) }
    }
    val pending = allHomework.filter { !it.completed }.sortedBy { it.deadline }
    val overdue = pending.filter { it.isOverdue() }
    val upcoming = pending.filter { !it.isOverdue() }
    val completed = allHomework.filter { it.completed }

    Scaffold(
        modifier = modifier,
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "添加作业")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            // 周切换器
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { weekOffset-- }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "上一周")
                }
                Text(
                    text = "${formatDate(weekStart)} - ${formatDate(weekEnd)}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                IconButton(onClick = { weekOffset++ }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "下一周")
                }
            }

            WeekGanttView(
                weekStart = weekStart,
                homework = pending,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
            )

            Spacer(Modifier.height(12.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (overdue.isNotEmpty()) {
                    item {
                        SectionHeader("已过期", MaterialTheme.colorScheme.error)
                    }
                    items(overdue, key = { it.id }) { hw ->
                        HomeworkCard(
                            homework = hw,
                            onLongClick = { showActionMenu = hw },
                            onToggle = { repository.toggleHomeworkComplete(hw.id); refresh() }
                        )
                    }
                    item { Spacer(Modifier.height(12.dp)) }
                }

                item { SectionHeader("待完成") }

                if (upcoming.isEmpty()) {
                    item {
                        Text(
                            text = "暂无待办作业",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                } else {
                    items(upcoming, key = { it.id }) { hw ->
                        HomeworkCard(
                            homework = hw,
                            onLongClick = { showActionMenu = hw },
                            onToggle = { repository.toggleHomeworkComplete(hw.id); refresh() }
                        )
                    }
                }

                if (completed.isNotEmpty()) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        SectionHeader("已完成 (${completed.size})", MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    items(completed, key = { it.id }) { hw ->
                        HomeworkCard(
                            homework = hw,
                            onLongClick = { showActionMenu = hw },
                            onToggle = { repository.toggleHomeworkComplete(hw.id); refresh() },
                            dimmed = true
                        )
                    }
                }

                item { Spacer(Modifier.height(88.dp)) } // 给 FAB 留位置
            }
        }
    }

    if (showAddDialog) {
        AddHomeworkDialog(
            repository = repository,
            onDismiss = { showAddDialog = false },
            onAdd = { hw ->
                repository.addHomework(hw)
                refresh()
            },
            onFinish = { showAddDialog = false }
        )
    }

    editingHomework?.let { hw ->
        EditHomeworkDialog(
            repository = repository,
            homework = hw,
            onDismiss = { editingHomework = null },
            onSave = { updated ->
                repository.updateHomework(updated)
                refresh()
                editingHomework = null
            }
        )
    }

    showActionMenu?.let { hw ->
        AppDialog(
            title = hw.title,
            summary = "${hw.subject} · 截止：${hw.deadline.ifBlank { "未设置" }}",
            onDismissRequest = { showActionMenu = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(
                    onClick = {
                        repository.toggleHomeworkComplete(hw.id)
                        refresh()
                        showActionMenu = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(if (hw.completed) "标记未完成" else "标记完成") }

                OutlinedButton(
                    onClick = {
                        showActionMenu = null
                        editingHomework = hw
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("修改") }

                OutlinedButton(
                    onClick = {
                        repository.deleteHomework(hw.id)
                        refresh()
                        showActionMenu = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("删除", color = MaterialTheme.colorScheme.error) }

                TextButton(
                    onClick = { showActionMenu = null },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("取消") }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, color: Color = Color.Unspecified) {
    Column {
        Text(
            text = text,
            style = MaterialTheme.typography.titleMedium,
            color = if (color == Color.Unspecified) MaterialTheme.colorScheme.onSurface else color
        )
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun WeekGanttView(weekStart: Calendar, homework: List<Homework>, modifier: Modifier) {
    val days = listOf("一", "二", "三", "四", "五", "六", "日")
    val fmt = remember { SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()) }

    Card(modifier = modifier) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                days.forEachIndexed { i, d ->
                    val dayCal = (weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, i) }
                    val isToday = formatDateFull(dayCal) == todayStr()
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                        Text(
                            text = d,
                            fontSize = 12.sp,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                            color = if (isToday) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(6.dp))

            val weekStartStr = formatDateFull(weekStart)
            val weekEndStr = formatDateFull((weekStart.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, 6) })
            val visibleHw = homework.filter { hw ->
                val start = hw.startDate.ifBlank { hw.deadline }
                val end = hw.deadline.ifBlank { start }
                start.isNotBlank() && end >= weekStartStr && start <= weekEndStr
            }

            if (visibleHw.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "本周暂无作业",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                ) {
                    visibleHw.forEach { hw ->
                        val startStr = hw.startDate.ifBlank { hw.deadline }
                        val endStr = hw.deadline.ifBlank { startStr }
                        val startDay = dayOffset(fmt, weekStartStr, startStr, fallback = 0)
                        val endDay = dayOffset(fmt, weekStartStr, endStr, fallback = 6)
                        val color = getSubjectColor(hw.subject)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 1.dp)
                        ) {
                            repeat(7) { dayIdx ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(14.dp)
                                        .padding(horizontal = 1.dp)
                                ) {
                                    if (dayIdx in startDay..endDay) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(12.dp)
                                                .clip(RoundedCornerShape(2.dp))
                                                .background(color.copy(alpha = 0.75f)),
                                            contentAlignment = Alignment.CenterStart
                                        ) {
                                            if (dayIdx == startDay) {
                                                Text(
                                                    text = "${hw.subject.take(1)} ${hw.title.take(3)}",
                                                    fontSize = 8.sp,
                                                    color = Color.White,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    modifier = Modifier.padding(start = 2.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun dayOffset(
    fmt: SimpleDateFormat,
    weekStartStr: String,
    dateStr: String,
    fallback: Int
): Int = runCatching {
    val start = fmt.parse(weekStartStr)!!.time
    val target = fmt.parse(dateStr)!!.time
    ((target - start) / 86_400_000L).toInt().coerceIn(0, 6)
}.getOrDefault(fallback)

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HomeworkCard(
    homework: Homework,
    onLongClick: () -> Unit,
    onToggle: () -> Unit,
    dimmed: Boolean = false
) {
    val color = getSubjectColor(homework.subject)
    val daysLeft = homework.getDaysRemaining()
    val alpha = if (dimmed) 0.5f else 1f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .combinedClickable(onClick = {}, onLongClick = onLongClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 完成状态圆形按钮
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(
                        if (homework.completed) color.copy(alpha = 0.8f)
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
                    )
                    .combinedClickable(onClick = onToggle, onLongClick = onLongClick),
                contentAlignment = Alignment.Center
            ) {
                if (homework.completed) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = "已完成",
                        tint = Color.White,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            Spacer(Modifier.width(10.dp))

            // 科目色条
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(36.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(color.copy(alpha = alpha))
            )

            Spacer(Modifier.width(10.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = homework.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (homework.subject.isNotBlank()) {
                    Text(text = homework.subject, fontSize = 12.sp, color = color.copy(alpha = alpha))
                }
            }

            if (dimmed) {
                Text(
                    text = "已完成",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
            } else {
                val statusText = when {
                    homework.isOverdue() -> "已过期"
                    daysLeft == 0 -> "今天截止"
                    daysLeft == 1 -> "明天截止"
                    daysLeft == Int.MAX_VALUE -> ""
                    else -> "剩${daysLeft}天"
                }
                if (statusText.isNotBlank()) {
                    Text(
                        text = statusText,
                        fontSize = 12.sp,
                        color = when {
                            homework.isOverdue() -> MaterialTheme.colorScheme.error
                            daysLeft <= 1 -> Color(0xFFFF9800)
                            else -> MaterialTheme.colorScheme.primary
                        },
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun AddHomeworkDialog(
    repository: AppDataRepository,
    onDismiss: () -> Unit,
    onAdd: (Homework) -> Unit,
    onFinish: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(TextFieldValue("")) }
    var subject by remember { mutableStateOf("") }
    var startDate by remember { mutableStateOf(todayStr()) }
    var deadline by remember { mutableStateOf("") }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var batchMode by remember { mutableStateOf(false) }
    var batchSubjects by remember { mutableStateOf(setOf<String>()) }
    var batchStep by remember { mutableStateOf(1) }
    var batchItems by remember { mutableStateOf(mapOf<String, String>()) }
    var batchDeadline by remember { mutableStateOf("") }

    val allSubjects = remember {
        repository.getSchedulePlans().flatMap { it.courses }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var showSubjectPicker by remember { mutableStateOf(false) }

    AppDialog(
        title = if (batchMode) "批量添加作业" else "添加作业",
        onDismissRequest = onDismiss
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (!batchMode) {
                AppTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = "标题",
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = { showSubjectPicker = !showSubjectPicker },
                    modifier = Modifier.fillMaxWidth()
                ) { Text(subject.ifBlank { "选择科目" }) }

                if (showSubjectPicker) {
                    SubjectPicker(allSubjects) { subject = it; showSubjectPicker = false }
                }

                OutlinedButton(
                    onClick = { pickDate(context, startDate) { startDate = it } },
                    modifier = Modifier.fillMaxWidth()
                ) { Text("开始：$startDate") }

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { deadline = addDays(todayStr(), 1) }) { Text("明天") }
                    FilledTonalButton(onClick = { deadline = addDays(todayStr(), 2) }) { Text("后天") }
                    FilledTonalButton(onClick = { deadline = nextMonday(todayStr()) }) { Text("下周一") }
                    FilledTonalButton(onClick = { pickDate(context, deadline) { deadline = it } }) { Text("自定义") }
                }

                if (deadline.isNotBlank()) {
                    Text("截止：$deadline", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }

                AppTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = "描述（可选）",
                    singleLine = false,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    TextButton(onClick = { batchMode = true; batchStep = 1 }) { Text("批量") }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        FilledTonalButton(
                            enabled = title.text.isNotBlank(),
                            onClick = {
                                onAdd(
                                    Homework(
                                        title = title.text.trim(),
                                        subject = subject,
                                        startDate = startDate,
                                        deadline = deadline,
                                        description = description.text.trim()
                                    )
                                )
                                onFinish()
                            }
                        ) { Text("添加") }
                    }
                }
            } else if (batchStep == 1) {
                Text("选择科目（多选）", style = MaterialTheme.typography.bodyMedium)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    allSubjects.forEach { s ->
                        val selected = batchSubjects.contains(s)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                batchSubjects = if (selected) batchSubjects - s else batchSubjects + s
                            },
                            label = { Text(s.take(3), fontSize = 12.sp) }
                        )
                    }
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { batchMode = false }) { Text("返回") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = batchSubjects.isNotEmpty(),
                        onClick = {
                            batchItems = batchSubjects.associateWith { "" }
                            batchStep = 2
                        }
                    ) { Text("下一步 (${batchSubjects.size})") }
                }
            } else {
                Text("输入各科作业（每行一条）", style = MaterialTheme.typography.bodyMedium)
                batchSubjects.toList().sorted().forEach { s ->
                    val color = getSubjectColor(s)
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(30.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(color)
                        )
                        Spacer(Modifier.width(8.dp))
                        AppTextField(
                            value = batchItems[s] ?: "",
                            onValueChange = { batchItems = batchItems + (s to it) },
                            label = s.take(3),
                            singleLine = false,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }

                Spacer(Modifier.height(4.dp))

                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilledTonalButton(onClick = { batchDeadline = addDays(todayStr(), 1) }) { Text("明天") }
                    FilledTonalButton(onClick = { batchDeadline = addDays(todayStr(), 2) }) { Text("后天") }
                    FilledTonalButton(onClick = { batchDeadline = nextMonday(todayStr()) }) { Text("下周一") }
                }
                if (batchDeadline.isNotBlank()) {
                    Text("截止：$batchDeadline", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { batchStep = 1 }) { Text("返回") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(onClick = {
                        batchItems.forEach { (subj, content) ->
                            content.split("\n").map { it.trim() }.filter { it.isNotBlank() }.forEach { line ->
                                onAdd(
                                    Homework(
                                        title = line,
                                        subject = subj,
                                        startDate = todayStr(),
                                        deadline = batchDeadline
                                    )
                                )
                            }
                        }
                        onFinish()
                    }) { Text("全部添加") }
                }
            }
        }
    }
}

@Composable
private fun EditHomeworkDialog(
    repository: AppDataRepository,
    homework: Homework,
    onDismiss: () -> Unit,
    onSave: (Homework) -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(TextFieldValue(homework.title)) }
    var subject by remember { mutableStateOf(homework.subject) }
    var deadline by remember { mutableStateOf(homework.deadline) }
    var description by remember { mutableStateOf(TextFieldValue(homework.description)) }
    val allSubjects = remember {
        repository.getSchedulePlans().flatMap { it.courses }.filter { it.isNotBlank() }.distinct().sorted()
    }
    var showSubjectPicker by remember { mutableStateOf(false) }

    AppDialog(title = "修改作业", onDismissRequest = onDismiss) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            AppTextField(
                value = title,
                onValueChange = { title = it },
                label = "标题",
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedButton(
                onClick = { showSubjectPicker = !showSubjectPicker },
                modifier = Modifier.fillMaxWidth()
            ) { Text(subject.ifBlank { "选择科目" }) }

            if (showSubjectPicker) {
                SubjectPicker(allSubjects) { subject = it; showSubjectPicker = false }
            }

            OutlinedButton(
                onClick = { pickDate(context, deadline) { deadline = it } },
                modifier = Modifier.fillMaxWidth()
            ) { Text(if (deadline.isBlank()) "选择截止日期" else "截止：$deadline") }

            AppTextField(
                value = description,
                onValueChange = { description = it },
                label = "描述（可选）",
                singleLine = false,
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text("取消") }
                Spacer(Modifier.width(12.dp))
                FilledTonalButton(
                    enabled = title.text.isNotBlank(),
                    onClick = {
                        onSave(
                            homework.copy(
                                title = title.text.trim(),
                                subject = subject,
                                deadline = deadline,
                                description = description.text.trim()
                            )
                        )
                    }
                ) { Text("保存") }
            }
        }
    }
}

@Composable
private fun SubjectPicker(subjects: List<String>, onPick: (String) -> Unit) {
    if (subjects.isEmpty()) {
        Text(
            text = "还没有课程，先去「方案管理」里添加",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        return
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        subjects.forEach { s ->
            OutlinedButton(onClick = { onPick(s) }) {
                Text(s.take(3), fontSize = 12.sp)
            }
        }
    }
}

private fun pickDate(
    context: android.content.Context,
    current: String,
    onPicked: (String) -> Unit
) {
    val cal = Calendar.getInstance()
    runCatching {
        if (current.isNotBlank()) {
            val parts = current.split("-")
            cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
        }
    }
    DatePickerDialog(
        ContextThemeWrapper(context, R.style.Theme_smartschedule),
        { _, y, m, d -> onPicked("%04d-%02d-%02d".format(y, m + 1, d)) },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}

private fun addDays(dateStr: String, days: Int): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    cal.time = fmt.parse(dateStr) ?: return dateStr
    cal.add(Calendar.DAY_OF_YEAR, days)
    return fmt.format(cal.time)
}

private fun nextMonday(dateStr: String): String {
    val fmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val cal = Calendar.getInstance()
    cal.time = fmt.parse(dateStr) ?: return dateStr
    val dayOfWeek = cal.get(Calendar.DAY_OF_WEEK)
    val daysUntilMonday = if (dayOfWeek == Calendar.MONDAY) 7 else (Calendar.SATURDAY - dayOfWeek + 2) % 7
    cal.add(Calendar.DAY_OF_YEAR, if (daysUntilMonday == 0) 7 else daysUntilMonday)
    return fmt.format(cal.time)
}
