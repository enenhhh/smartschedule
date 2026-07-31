package com.enenhhh.smartschedule

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
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
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.repository.AppDataRepository
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.ui.components.AppTextField
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager

class ShortNameActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)

        val repository = AppDataRepository(this)
        val allCourses = repository.getSchedulePlans()
            .flatMap { it.courses }
            .filter { it.isNotBlank() }
            .distinct()
            .sorted()

        setContent {
            AppTheme {
                ShortNameScreen(
                    allCourses = allCourses,
                    initialShortNames = repository.getCustomShortNames(),
                    onSave = { repository.setCustomShortNames(it) },
                    onBack = { finish() }
                )
            }
        }
    }
}

@Composable
private fun ShortNameScreen(
    allCourses: List<String>,
    initialShortNames: Map<String, String>,
    onSave: (Map<String, String>) -> Unit,
    onBack: () -> Unit
) {
    var shortNames by remember { mutableStateOf(initialShortNames) }
    var editingCourse by remember { mutableStateOf<String?>(null) }

    // 每次改动立刻落盘，避免用户按返回后丢失
    fun update(newMap: Map<String, String>) {
        shortNames = newMap
        onSave(newMap)
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "课程简写管理",
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
        ) {
            Text(
                text = "简写会显示在首页和课表格子里，留空则默认取前两个字",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )

            OutlinedButton(
                onClick = {
                    update(
                        shortNames + allCourses.associateWith { course ->
                            course.firstOrNull()?.toString().orEmpty()
                        }
                    )
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.AutoFixHigh, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("一键自动生成（取首字）")
            }

            Spacer(Modifier.height(8.dp))

            if (allCourses.isEmpty()) {
                Text(
                    text = "还没有课程，先去「方案管理」里添加课程方案",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(24.dp)
                )
            }

            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(allCourses, key = { it }) { course ->
                    ShortNameCard(
                        courseName = course,
                        shortName = shortNames[course].orEmpty(),
                        onClick = { editingCourse = course }
                    )
                }
            }
        }
    }

    editingCourse?.let { courseName ->
        EditShortNameDialog(
            courseName = courseName,
            initialValue = shortNames[courseName].orEmpty(),
            onDismiss = { editingCourse = null },
            onConfirm = { value ->
                update(shortNames + (courseName to value))
                editingCourse = null
            }
        )
    }
}

@Composable
private fun ShortNameCard(
    courseName: String,
    shortName: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = courseName, style = MaterialTheme.typography.bodyLarge)
                Text(
                    text = if (shortName.isBlank()) "未设置简写" else "简写：$shortName",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                Icons.Default.Edit,
                contentDescription = "编辑",
                tint = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun EditShortNameDialog(
    courseName: String,
    initialValue: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var text by remember { mutableStateOf(TextFieldValue(initialValue)) }

    AppDialog(
        title = "编辑简写",
        summary = courseName,
        onDismissRequest = onDismiss
    ) {
        AppTextField(
            value = text,
            onValueChange = { if (it.text.length <= 4) text = it },
            label = "简写（最多 4 个字）",
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TextButton(onClick = onDismiss) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(onClick = { onConfirm(text.text.trim()) }) { Text("确定") }
        }
    }
}
