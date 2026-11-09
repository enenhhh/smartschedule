package com.enenhhh.smartschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.CourseChip
import com.enenhhh.smartschedule.ui.components.EmptyState
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SubPage

@Composable
fun ShortNamesScreen() {
    val manager = LocalScheduleManager.current
    val version = ScheduleManager.version
    val names = remember(version) { manager.knownCourseNames() }
    val custom = remember(version) { manager.getCustomShortNames() }
    var editing by remember { mutableStateOf<String?>(null) }
    var adding by remember { mutableStateOf(false) }

    fun save(full: String, short: String) {
        val map = LinkedHashMap(custom)
        if (short.isBlank()) map.remove(full) else map[full] = short.trim()
        manager.setCustomShortNames(map)
    }

    SubPage(title = "课程简称", subtitle = "课表格子和通知里显示的短名字") { padding ->
        Box(Modifier.fillMaxSize()) {
            if (names.isEmpty()) {
                Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                    EmptyState(icon = Icons.Rounded.ShortText, title = "还没有课程", description = "先在课表里填入课程，这里就会列出来。默认取前两个字作为简称。")
                }
            } else {
                LazyColumn(contentPadding = padding) {
                    item {
                        Text(
                            "默认取课程名前两个字；点一项可以改成任意简称（建议 1–3 个字）。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                        )
                    }
                    item {
                        GlassGroup {
                            names.forEachIndexed { i, full ->
                                val short = manager.getShortName(full)
                                SettingRow(
                                    title = full,
                                    subtitle = if (custom[full].isNullOrBlank()) "自动简称" else "自定义",
                                    onClick = { editing = full },
                                    showChevron = false,
                                    trailing = { CourseChip(text = short, color = androidx.compose.material3.MaterialTheme.colorScheme.secondaryContainer) }
                                )
                                if (i != names.lastIndex) RowDivider()
                            }
                        }
                    }
                    item { Spacer(Modifier.height(80.dp)) }
                }
            }
            ExtendedFloatingActionButton(
                onClick = { adding = true },
                icon = { Icon(Icons.Rounded.Add, null) },
                text = { Text("手动添加") },
                modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
            )
        }
    }

    editing?.let { full ->
        var text by remember(full) { mutableStateOf(custom[full] ?: "") }
        GlassDialog(
            onDismiss = { editing = null },
            title = full,
            confirmText = "保存",
            onConfirm = { save(full, text); editing = null }
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.take(6) },
                label = { Text("简称") },
                placeholder = { Text(manager.getShortName(full)) },
                singleLine = true,
                supportingText = { Text("留空恢复为自动简称（${if (full.length > 2) full.substring(0, 2) else full}）") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
            if (!custom[full].isNullOrBlank()) {
                TextButton(onClick = { save(full, ""); editing = null }) { Text("恢复自动简称") }
            }
        }
    }

    if (adding) {
        var full by remember { mutableStateOf("") }
        var short by remember { mutableStateOf("") }
        GlassDialog(
            onDismiss = { adding = false },
            title = "添加简称规则",
            confirmText = "保存",
            confirmEnabled = full.isNotBlank() && short.isNotBlank(),
            onConfirm = { save(full.trim(), short); adding = false }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = full, onValueChange = { full = it }, label = { Text("课程全名") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = short, onValueChange = { short = it.take(6) }, label = { Text("简称") }, singleLine = true, shape = MaterialTheme.shapes.medium, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}
