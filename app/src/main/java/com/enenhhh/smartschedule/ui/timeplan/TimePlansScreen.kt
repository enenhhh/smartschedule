package com.enenhhh.smartschedule.ui.timeplan

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Schedule
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
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.ConfirmDialog
import com.enenhhh.smartschedule.ui.components.EmptyState
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.components.TextInputDialog
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.nav.Route

@Composable
fun TimePlansScreen() {
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val version = ScheduleManager.version
    val plans = remember(version) { manager.getTimePlans() }
    val usage = remember(version) { manager.getTimetables().groupingBy { it.timePlanName }.eachCount() }

    var duplicate by remember { mutableStateOf<TimePlan?>(null) }
    var delete by remember { mutableStateOf<TimePlan?>(null) }

    SubPage(title = "时间方案", subtitle = "每天几节课、每节几点上") { padding ->
        Box(Modifier.fillMaxSize()) {
            if (plans.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(
                        icon = Icons.Rounded.Schedule,
                        title = "还没有时间方案",
                        description = "点右下角新建，几秒钟就能按规则生成一整天的节次。"
                    )
                }
            } else {
                LazyColumn(contentPadding = padding, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(plans, key = { it.name }) { plan ->
                        var menu by remember { mutableStateOf(false) }
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .glassCard()
                                .clickable { nav.push(Route.TimePlanEditor(plan.name)) }
                                .padding(start = 18.dp, end = 6.dp, top = 14.dp, bottom = 14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(plan.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    "${plan.periods.size} 节 · ${plan.periods.firstOrNull()?.start ?: "--"} – ${plan.periods.lastOrNull()?.end ?: "--"}" +
                                        " · 晨读 ${plan.morningStart}–${plan.morningEnd}" +
                                        (usage[plan.name]?.let { " · ${it} 张课表使用" } ?: ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Box {
                                IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = "更多") }
                                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                    DropdownMenuItem(text = { Text("编辑") }, onClick = { menu = false; nav.push(Route.TimePlanEditor(plan.name)) })
                                    DropdownMenuItem(text = { Text("复制") }, onClick = { menu = false; duplicate = plan })
                                    DropdownMenuItem(text = { Text("删除") }, onClick = { menu = false; delete = plan })
                                }
                            }
                        }
                    }
                    item { Spacer(Modifier.height(72.dp)) }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { nav.push(Route.TimePlanEditor(null)) },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("新建方案") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }

    duplicate?.let { plan ->
        TextInputDialog(
            title = "复制时间方案",
            initial = "${plan.name} 副本",
            onDismiss = { duplicate = null },
            validate = {
                when {
                    !ScheduleManager.isValidPlanName(it) -> "名称不能为空、不能超过 30 字或含特殊符号"
                    plans.any { p -> p.name == it } -> "已存在同名方案"
                    else -> null
                }
            },
            onConfirm = { manager.duplicateTimePlan(plan.name, it) }
        )
    }
    delete?.let { plan ->
        val used = usage[plan.name] ?: 0
        ConfirmDialog(
            title = "删除「${plan.name}」？",
            message = if (used > 0) "有 $used 张课表正在使用它，删除后这些课表需要重新选择时间方案。" else "删除后无法恢复。",
            onDismiss = { delete = null },
            onConfirm = { manager.deleteTimePlan(plan.name) },
            confirmText = "删除",
            destructive = true
        )
    }
}
