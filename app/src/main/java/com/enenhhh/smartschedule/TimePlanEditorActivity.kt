package com.enenhhh.smartschedule

import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.repository.AppDataRepository
import com.enenhhh.smartschedule.models.TimePeriod
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppTextField
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager

class TimePlanEditorActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)
        val repository = AppDataRepository(this)

        val name = intent.getStringExtra("name") ?: ""
        val currentPlan = repository.getTimePlans().find { it.name == name } ?: TimePlan(name)

        setContent {
            AppTheme {
                TimePlanEditorScreen(
                    initialPlan = currentPlan,
                    calculateAutoFill = { start, lesson, breakMin, count ->
                        repository.calculateAutoFill(start, lesson, breakMin, count)
                    },
                    onSave = { plan ->
                        repository.saveTimePlan(plan)
                        Toast.makeText(this, "已保存", Toast.LENGTH_SHORT).show()
                        finish()
                    },
                    onBack = { finish() },
                    pickTime = { current, onPicked -> showTimePicker(current, onPicked) },
                    showToast = { Toast.makeText(this, it, Toast.LENGTH_SHORT).show() }
                )
            }
        }
    }

    private fun showTimePicker(current: String, callback: (String) -> Unit) {
        val p = current.split(":")
        val hour = p.getOrNull(0)?.toIntOrNull() ?: 8
        val minute = p.getOrNull(1)?.toIntOrNull() ?: 0
        TimePickerDialog(
            ContextThemeWrapper(this, R.style.Theme_smartschedule),
            { _, h, m -> callback("%02d:%02d".format(h, m)) },
            hour, minute, true
        ).show()
    }
}

@Composable
private fun TimePlanEditorScreen(
    initialPlan: TimePlan,
    calculateAutoFill: (String, Int, Int, Int) -> List<TimePeriod>,
    onSave: (TimePlan) -> Unit,
    onBack: () -> Unit,
    pickTime: (String, (String) -> Unit) -> Unit,
    showToast: (String) -> Unit
) {
    var morningStart by remember { mutableStateOf(initialPlan.morningStart) }
    var morningEnd by remember { mutableStateOf(initialPlan.morningEnd) }
    var fastStart by remember { mutableStateOf("08:00") }
    var fastLesson by remember { mutableStateOf("45") }
    var fastBreak by remember { mutableStateOf("10") }
    var fastCount by remember { mutableStateOf("8") }
    var periods by remember { mutableStateOf(initialPlan.periods.map { it.copy() }) }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "编辑时间方案",
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
            Text(
                text = initialPlan.name,
                style = MaterialTheme.typography.titleLarge
            )

            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "早读时间",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TimeButton(morningStart) { pickTime(morningStart) { morningStart = it } }
                    Text(" - ", modifier = Modifier.padding(horizontal = 4.dp))
                    TimeButton(morningEnd) { pickTime(morningEnd) { morningEnd = it } }
                }
            }

            Card(modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text("快速生成", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "按固定的课长和课间时间批量生成课时",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Column(
                        modifier = Modifier.padding(top = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        AppTextField(
                            value = fastStart,
                            onValueChange = { fastStart = it },
                            label = "开始时间（如 08:00）",
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AppTextField(
                                value = fastLesson,
                                onValueChange = { fastLesson = it },
                                label = "每节分钟",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            AppTextField(
                                value = fastBreak,
                                onValueChange = { fastBreak = it },
                                label = "课间分钟",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                            AppTextField(
                                value = fastCount,
                                onValueChange = { fastCount = it },
                                label = "节数",
                                keyboardType = KeyboardType.Number,
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            val lesson = fastLesson.toIntOrNull() ?: 45
                            val breakMin = fastBreak.toIntOrNull() ?: 10
                            val count = fastCount.toIntOrNull()?.coerceIn(1, 20) ?: 8
                            if (fastStart.contains(":")) {
                                periods = calculateAutoFill(fastStart, lesson, breakMin, count)
                            } else {
                                showToast("请输入正确的开始时间（如 08:00）")
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 12.dp)
                    ) { Text("一键生成") }
                }
            }

            periods.forEachIndexed { index, period ->
                PeriodCard(
                    index = index + 1,
                    period = period,
                    onPickStart = {
                        pickTime(period.start) { newTime ->
                            periods = periods.toMutableList().also { it[index] = it[index].copy(start = newTime) }
                        }
                    },
                    onPickEnd = {
                        pickTime(period.end) { newTime ->
                            periods = periods.toMutableList().also { it[index] = it[index].copy(end = newTime) }
                        }
                    },
                    onDelete = {
                        periods = periods.toMutableList().also { it.removeAt(index) }
                    }
                )
            }

            OutlinedButton(
                onClick = {
                    val lastEnd = periods.lastOrNull()?.end ?: "08:00"
                    periods = periods + TimePeriod(lastEnd, lastEnd)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("添加一节课")
            }

            Button(
                onClick = {
                    onSave(
                        initialPlan.copy(
                            morningStart = morningStart,
                            morningEnd = morningEnd,
                            periods = periods.toMutableList()
                        )
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp, bottom = 24.dp)
            ) { Text("保存时间方案") }
        }
    }
}

@Composable
private fun PeriodCard(
    index: Int,
    period: TimePeriod,
    onPickStart: () -> Unit,
    onPickEnd: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = "第${index}节", modifier = Modifier.width(56.dp))
            TimeButton(period.start, onClick = onPickStart)
            Text(" - ", modifier = Modifier.padding(horizontal = 6.dp))
            TimeButton(period.end, onClick = onPickEnd)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onDelete) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "删除",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

@Composable
private fun TimeButton(text: String, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp)) {
        Icon(Icons.Default.AccessTime, contentDescription = null)
        Spacer(Modifier.width(6.dp))
        Text(text)
    }
}
