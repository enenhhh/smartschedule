package com.enenhhh.smartschedule

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.TextFieldValue
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

private enum class PlanTab { Time, Course }

class PlanListActivity : ComponentActivity() {
    private lateinit var repository: AppDataRepository

    /** 从编辑页返回后自增，用来触发列表重新读取 */
    private var refreshTrigger by mutableIntStateOf(0)

    override fun onResume() {
        super.onResume()
        refreshTrigger++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)
        repository = AppDataRepository(this)

        setContent {
            AppTheme {
                PlanListScreen(
                    refreshKey = refreshTrigger,
                    loadTimePlans = { repository.getTimePlans() },
                    loadSchedulePlans = { repository.getSchedulePlans() },
                    createTimePlan = { name -> repository.saveTimePlan(TimePlan(name)) },
                    createSchedulePlan = { name, timePlanName ->
                        repository.saveSchedulePlan(SchedulePlan(name, timePlanName))
                    },
                    deleteTimePlan = { name -> repository.deleteTimePlan(name) },
                    deleteSchedulePlan = { name -> repository.deleteSchedulePlan(name) },
                    copyTimePlan = { source, newName -> repository.copyTimePlan(source, newName) },
                    copySchedulePlan = { source, newName -> repository.copySchedulePlan(source, newName) },
                    openTimePlan = { name ->
                        startActivity(Intent(this, TimePlanEditorActivity::class.java).apply {
                            putExtra("name", name)
                        })
                    },
                    openCoursePlan = { name ->
                        startActivity(Intent(this, CoursePlanEditorActivity::class.java).apply {
                            putExtra("name", name)
                        })
                    },
                    onBack = { finish() },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }
}

@Composable
private fun PlanListScreen(
    refreshKey: Int,
    loadTimePlans: () -> List<TimePlan>,
    loadSchedulePlans: () -> List<SchedulePlan>,
    createTimePlan: (String) -> Unit,
    createSchedulePlan: (String, String) -> Unit,
    deleteTimePlan: (String) -> Unit,
    deleteSchedulePlan: (String) -> Unit,
    copyTimePlan: (String, String) -> Unit,
    copySchedulePlan: (String, String) -> Unit,
    openTimePlan: (String) -> Unit,
    openCoursePlan: (String) -> Unit,
    onBack: () -> Unit,
    showToast: (String) -> Unit
) {
    var currentTab by remember { mutableStateOf(PlanTab.Time) }
    var localVersion by remember { mutableIntStateOf(0) }

    val trigger = refreshKey + localVersion
    val timePlans = remember(trigger) { loadTimePlans() }
    val schedulePlans = remember(trigger) { loadSchedulePlans() }

    var showCreateDialog by remember { mutableStateOf(false) }
    var pendingCoursePlanName by remember { mutableStateOf<String?>(null) }
    var copySourceName by remember { mutableStateOf<String?>(null) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "方案管理",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "新建")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = currentTab == PlanTab.Time,
                    onClick = { currentTab = PlanTab.Time },
                    label = { Text("时间方案") }
                )
                FilterChip(
                    selected = currentTab == PlanTab.Course,
                    onClick = { currentTab = PlanTab.Course },
                    label = { Text("课程方案") }
                )
            }

            val isEmpty = if (currentTab == PlanTab.Time) timePlans.isEmpty() else schedulePlans.isEmpty()
            if (isEmpty) {
                Text(
                    text = if (currentTab == PlanTab.Time) {
                        "还没有时间方案，点右上角 + 创建一个"
                    } else {
                        "还没有课程方案，点右上角 + 创建一个"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                if (currentTab == PlanTab.Time) {
                    items(timePlans, key = { it.name }) { item ->
                        PlanCard(
                            name = item.name,
                            info = "包含 ${item.periods.size} 个课时节点",
                            onEdit = { openTimePlan(item.name) },
                            onDelete = { pendingDelete = item.name },
                            onCopy = { copySourceName = item.name }
                        )
                    }
                } else {
                    items(schedulePlans, key = { it.name }) { item ->
                        PlanCard(
                            name = item.name,
                            info = "时间方案：${item.timePlanName} · 绑定 ${item.bindingDays.size} 天",
                            onEdit = { openCoursePlan(item.name) },
                            onDelete = { pendingDelete = item.name },
                            onCopy = { copySourceName = item.name }
                        )
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        PlanNameDialog(
            title = if (currentTab == PlanTab.Time) "新建时间方案" else "新建课程方案",
            existingNames = if (currentTab == PlanTab.Time) {
                timePlans.map { it.name }
            } else {
                schedulePlans.map { it.name }
            },
            onDismiss = { showCreateDialog = false },
            onConfirm = { name ->
                showCreateDialog = false
                if (currentTab == PlanTab.Time) {
                    createTimePlan(name)
                    localVersion++
                    openTimePlan(name)
                } else {
                    if (timePlans.isEmpty()) {
                        showToast("请先创建一个时间方案！")
                    } else {
                        pendingCoursePlanName = name
                    }
                }
            }
        )
    }

    pendingCoursePlanName?.let { coursePlanName ->
        SelectPlanDialog(
            title = "选择关联的时间方案",
            plans = timePlans.map { it.name },
            onDismiss = { pendingCoursePlanName = null },
            onSelect = { selectedTimePlan ->
                createSchedulePlan(coursePlanName, selectedTimePlan)
                pendingCoursePlanName = null
                localVersion++
                openCoursePlan(coursePlanName)
            }
        )
    }

    copySourceName?.let { sourceName ->
        PlanNameDialog(
            title = "复制方案",
            existingNames = if (currentTab == PlanTab.Time) {
                timePlans.map { it.name }
            } else {
                schedulePlans.map { it.name }
            },
            onDismiss = { copySourceName = null },
            onConfirm = { newName ->
                if (currentTab == PlanTab.Time) copyTimePlan(sourceName, newName)
                else copySchedulePlan(sourceName, newName)
                copySourceName = null
                localVersion++
            }
        )
    }

    pendingDelete?.let { name ->
        AppDialog(
            title = "删除方案",
            summary = "确定要删除「$name」吗？删除后无法恢复。",
            onDismissRequest = { pendingDelete = null }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { pendingDelete = null }) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    if (currentTab == PlanTab.Time) deleteTimePlan(name) else deleteSchedulePlan(name)
                    pendingDelete = null
                    localVersion++
                }) { Text("删除") }
            }
        }
    }
}

@Composable
private fun PlanCard(
    name: String,
    info: String,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCopy: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(text = name, style = MaterialTheme.typography.titleMedium)
            Text(
                text = info,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )

            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("编辑")
                }
                OutlinedButton(onClick = onCopy) {
                    Icon(Icons.Default.ContentCopy, contentDescription = null)
                    Spacer(Modifier.width(6.dp))
                    Text("复制")
                }
                OutlinedButton(onClick = onDelete) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanNameDialog(
    title: String,
    existingNames: List<String>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue("")) }
    val trimmed = text.text.trim()
    val duplicated = trimmed.isNotEmpty() && existingNames.contains(trimmed)
    // 方案名会直接当作文件名使用，禁止路径字符
    val invalid = trimmed.any { it in "/\\:*?\"<>|" }

    AppDialog(title = title, onDismissRequest = onDismiss) {
        AppTextField(
            value = text,
            onValueChange = { text = it },
            label = "方案名称",
            isError = duplicated || invalid,
            modifier = Modifier.fillMaxWidth()
        )
        if (duplicated) {
            Text(
                text = "已存在同名方案",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        } else if (invalid) {
            Text(
                text = "名称不能包含 / \\ : * ? \" < > |",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                enabled = trimmed.isNotEmpty() && !duplicated && !invalid,
                onClick = { onConfirm(trimmed) }
            ) { Text("确定") }
        }
    }
}

@Composable
private fun SelectPlanDialog(
    title: String,
    plans: List<String>,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit
) {
    AppDialog(title = title, onDismissRequest = onDismiss) {
        if (plans.isEmpty()) {
            Text("暂无可用时间方案")
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                plans.forEach { planName ->
                    OutlinedButton(
                        onClick = { onSelect(planName) },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(planName) }
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
        }
    }
}
