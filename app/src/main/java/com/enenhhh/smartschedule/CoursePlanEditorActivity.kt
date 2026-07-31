package com.enenhhh.smartschedule

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.repository.AppDataRepository
import com.enenhhh.smartschedule.models.SchedulePlan
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.ui.components.AppTextField
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager

private val WEEK_DAYS = listOf("周一", "周二", "周三", "周四", "周五")

class CoursePlanEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)
        val repository = AppDataRepository(this)

        val name = intent.getStringExtra("name") ?: ""
        val currentPlan = repository.getSchedulePlans().find { it.name == name }
            ?: SchedulePlan(name, "")
        val boundTimePlan = repository.getTimePlans().find { it.name == currentPlan.timePlanName }

        setContent {
            AppTheme {
                CoursePlanEditorScreen(
                    initialPlan = currentPlan,
                    initialBoundTimePlan = boundTimePlan,
                    allTimePlans = { repository.getTimePlans() },
                    onSave = { plan ->
                        repository.saveSchedulePlan(plan)
                        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun CoursePlanEditorScreen(
    initialPlan: SchedulePlan,
    initialBoundTimePlan: TimePlan?,
    allTimePlans: () -> List<TimePlan>,
    onSave: (SchedulePlan) -> Unit,
    onBack: () -> Unit
) {
    val timePlans = remember { allTimePlans() }
    var showTimePlanDialog by remember { mutableStateOf(false) }

    var selectedTimePlanName by remember { mutableStateOf(initialPlan.timePlanName) }
    var boundTimePlan by remember { mutableStateOf(initialBoundTimePlan) }
    var morningRead by remember { mutableStateOf(initialPlan.morningRead) }
    var morningReadEnabled by remember { mutableStateOf(initialPlan.isMorningReadEnabled) }
    var bindingDays by remember { mutableStateOf(initialPlan.bindingDays.toSet()) }
    var courses by remember {
        val count = initialBoundTimePlan?.periods?.size ?: 8
        mutableStateOf(List(count) { initialPlan.courses.getOrNull(it).orEmpty() })
    }

    LaunchedEffect(selectedTimePlanName) {
        boundTimePlan = timePlans.find { it.name == selectedTimePlanName }
        val newCount = boundTimePlan?.periods?.size ?: 8
        if (newCount != courses.size) {
            courses = List(newCount) { courses.getOrNull(it).orEmpty() }
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "编辑课程方案",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
        ) {
            Text(text = initialPlan.name, style = MaterialTheme.typography.titleLarge)

            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "绑定的时间方案", modifier = Modifier.weight(1f))
                        OutlinedButton(onClick = { showTimePlanDialog = true }) {
                            Icon(Icons.Default.Schedule, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text(selectedTimePlanName.ifBlank { "未选择" })
                        }
                    }

                    Text(
                        text = "生效日期",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        text = "勾选后，对应的单/双周星期会使用这套课程",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    WeekdaySection(
                        title = "单周",
                        prefix = "odd",
                        selectedKeys = bindingDays,
                        onToggle = { key ->
                            bindingDays = if (key in bindingDays) bindingDays - key else bindingDays + key
                        }
                    )
                    WeekdaySection(
                        title = "双周",
                        prefix = "even",
                        selectedKeys = bindingDays,
                        onToggle = { key ->
                            bindingDays = if (key in bindingDays) bindingDays - key else bindingDays + key
                        }
                    )
                }
            }

            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = "包含早读", modifier = Modifier.weight(1f))
                        Switch(
                            checked = morningReadEnabled,
                            onCheckedChange = { morningReadEnabled = it }
                        )
                    }

                    if (morningReadEnabled) {
                        Spacer(Modifier.height(8.dp))
                        AppTextField(
                            value = morningRead,
                            onValueChange = { morningRead = it },
                            label = "早读内容",
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Spacer(Modifier.height(16.dp))

                    courses.forEachIndexed { index, value ->
                        AppTextField(
                            value = value,
                            onValueChange = { newValue ->
                                courses = courses.toMutableList().also { it[index] = newValue }
                            },
                            label = "第 ${index + 1} 节课程",
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 10.dp)
                        )
                    }

                    if (courses.isEmpty()) {
                        Text(
                            text = "关联的时间方案还没有课时，先去时间方案里添加",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Button(
                onClick = {
                    onSave(
                        initialPlan.copy(
                            timePlanName = selectedTimePlanName,
                            morningRead = morningRead,
                            isMorningReadEnabled = morningReadEnabled,
                            courses = courses.toMutableList(),
                            bindingDays = bindingDays.toMutableList()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp)
            ) { Text("保存课程方案") }
        }
    }

    if (showTimePlanDialog) {
        AppDialog(
            title = "更改关联的时间方案",
            summary = "切换后课时数量会跟着变",
            onDismissRequest = { showTimePlanDialog = false }
        ) {
            if (timePlans.isEmpty()) {
                Text("暂无可用时间方案")
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    timePlans.forEach { plan ->
                        OutlinedButton(
                            onClick = {
                                selectedTimePlanName = plan.name
                                showTimePlanDialog = false
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("${plan.name}（${plan.periods.size} 节）") }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showTimePlanDialog = false }) { Text("取消") }
            }
        }
    }
}

@Composable
private fun WeekdaySection(
    title: String,
    prefix: String,
    selectedKeys: Set<String>,
    onToggle: (String) -> Unit
) {
    Text(
        text = title,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
    )
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        WEEK_DAYS.forEachIndexed { index, day ->
            val key = "${prefix}_${index + 1}"
            FilterChip(
                selected = key in selectedKeys,
                onClick = { onToggle(key) },
                label = { Text(day) }
            )
        }
    }
}
