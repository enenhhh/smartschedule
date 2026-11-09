package com.enenhhh.smartschedule.ui.timetable

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.models.Timetable
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.ConfirmDialog
import com.enenhhh.smartschedule.ui.components.EmptyState
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.components.TextInputDialog
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.nav.Route

@Composable
fun TimetablesScreen() {
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val version = ScheduleManager.version
    val list = remember(version) { manager.getTimetables() }
    val active = remember(version) { manager.getActiveTimetable()?.id }

    var newOpen by remember { mutableStateOf(false) }
    var rename by remember { mutableStateOf<Timetable?>(null) }
    var duplicate by remember { mutableStateOf<Timetable?>(null) }
    var delete by remember { mutableStateOf<Timetable?>(null) }

    SubPage(title = "课表管理", subtitle = "可以保存多张课表，随时切换") { padding ->
        Box(Modifier.fillMaxSize()) {
            if (list.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.CalendarViewWeek,
                        title = "还没有课表",
                        description = "点右下角新建一张课表。",
                        actionText = null,
                        onAction = null
                    )
                }
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(list, key = { it.id }) { tt ->
                        TimetableCard(
                            tt = tt,
                            isActive = tt.id == active,
                            timePlanOk = manager.getTimePlan(tt.timePlanName) != null,
                            onOpen = { nav.push(Route.TimetableEditor(tt.id)) },
                            onActivate = { manager.setActiveTimetable(tt.id) },
                            onRename = { rename = tt },
                            onDuplicate = { duplicate = tt },
                            onDelete = { delete = tt }
                        )
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { newOpen = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("新建课表") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }

    if (newOpen) {
        TextInputDialog(
            title = "新建课表",
            initial = if (list.isEmpty()) "我的课表" else "课表 ${list.size + 1}",
            onDismiss = { newOpen = false },
            validate = { if (ScheduleManager.isValidPlanName(it)) null else "名称不能为空、不能超过 30 字或含特殊符号" },
            onConfirm = { name ->
                val tt = manager.createTimetable(name, null)
                nav.push(Route.TimetableEditor(tt.id))
            }
        )
    }
    rename?.let { tt ->
        TextInputDialog(
            title = "重命名",
            initial = tt.name,
            onDismiss = { rename = null },
            validate = { if (ScheduleManager.isValidPlanName(it)) null else "名称不能为空、不能超过 30 字或含特殊符号" },
            onConfirm = { manager.saveTimetable(tt.copy(name = it)) }
        )
    }
    duplicate?.let { tt ->
        TextInputDialog(
            title = "复制课表",
            initial = "${tt.name} 副本",
            onDismiss = { duplicate = null },
            validate = { if (ScheduleManager.isValidPlanName(it)) null else "名称不能为空、不能超过 30 字或含特殊符号" },
            onConfirm = { manager.duplicateTimetable(tt.id, it) }
        )
    }
    delete?.let { tt ->
        ConfirmDialog(
            title = "删除「${tt.name}」？",
            message = "删除后无法恢复。",
            onDismiss = { delete = null },
            onConfirm = { manager.deleteTimetable(tt.id) },
            confirmText = "删除",
            destructive = true
        )
    }
}

@Composable
private fun TimetableCard(
    tt: Timetable,
    isActive: Boolean,
    timePlanOk: Boolean,
    onOpen: () -> Unit,
    onActivate: () -> Unit,
    onRename: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit
) {
    var menu by remember { mutableStateOf(false) }
    val courseCount = tt.days.sumOf { d -> d.courses.count { it.isNotBlank() } }
    Row(
        Modifier
            .fillMaxWidth()
            .glassCard()
            .clickable(onClick = onOpen)
            .padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(tt.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                if (isActive) {
                    Spacer(Modifier.width(6.dp))
                    Icon(Icons.Rounded.CheckCircle, contentDescription = "使用中", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.width(18.dp))
                }
            }
            Text(
                buildString {
                    append("每周 $courseCount 节")
                    if (tt.hasAnyEvenVariant()) append(" · 有单双周")
                    append(" · 时间方案：${tt.timePlanName.ifBlank { "未设置" }}")
                    if (!timePlanOk) append("（缺失）")
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (timePlanOk) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error
            )
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (!isActive) DropdownMenuItem(text = { Text("设为使用中") }, onClick = { menu = false; onActivate() })
                DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; onOpen() })
                DropdownMenuItem(text = { Text("重命名") }, onClick = { menu = false; onRename() })
                DropdownMenuItem(text = { Text("复制") }, onClick = { menu = false; onDuplicate() })
                DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; onDelete() })
            }
        }
    }
}
