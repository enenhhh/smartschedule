package com.enenhhh.smartschedule

import android.os.Bundle
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.UpdateChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class UpdateActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)

        setContent {
            AppTheme {
                UpdateScreen(
                    onBack = { finish() },
                    onCheckUpdate = { UpdateChecker.checkAndPrompt(this, manual = true) },
                    onDownload = { UpdateChecker.downloadLatestApk(this) }
                )
            }
        }
    }
}

@Composable
private fun UpdateScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit,
    onDownload: () -> Unit
) {
    var testResults by remember { mutableStateOf<List<UpdateChecker.SourceTestResult>>(emptyList()) }
    var testing by remember { mutableStateOf(true) }
    var selectedIndex by remember { mutableIntStateOf(UpdateChecker.getSelectedSourceIndex()) }
    val scope = rememberCoroutineScope()

    suspend fun runSpeedTest() {
        testing = true
        val results = withContext(Dispatchers.IO) { UpdateChecker.testAllSources() }
        testResults = results
        results.filter { it.error == null }.minByOrNull { it.latency }?.let { fastest ->
            selectedIndex = UpdateChecker.sources.indexOf(fastest.source)
            UpdateChecker.setSelectedSource(selectedIndex)
        }
        testing = false
    }

    LaunchedEffect(Unit) { runSpeedTest() }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "检查更新",
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
                .padding(24.dp)
        ) {
            Text("更新源", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            if (testing) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    CircularProgressIndicator(modifier = Modifier.height(18.dp).width(18.dp))
                    Spacer(Modifier.width(12.dp))
                    Text("正在测速…", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            } else {
                testResults.forEachIndexed { index, result ->
                    val isSelected = index == selectedIndex
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        onClick = {
                            selectedIndex = index
                            UpdateChecker.setSelectedSource(index)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) {
                                MaterialTheme.colorScheme.secondaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceContainerHighest
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Default.Star,
                                        contentDescription = "已选中",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.height(18.dp).width(18.dp)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                }
                                Column {
                                    Text(
                                        text = result.source.name,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                    )
                                    if (result.error != null) {
                                        Text(
                                            text = "连接失败",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.error
                                        )
                                    } else {
                                        Text(
                                            text = "最新版 ${result.latestVersion}",
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                            Text(
                                text = if (result.error == null) "${result.latency}ms" else "--",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium,
                                color = when {
                                    result.error != null -> MaterialTheme.colorScheme.error
                                    result.latency < 500 -> MaterialTheme.colorScheme.primary
                                    result.latency < 1500 -> MaterialTheme.colorScheme.onSurface
                                    else -> MaterialTheme.colorScheme.error
                                }
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            Button(
                onClick = onCheckUpdate,
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("检查更新") }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = onDownload,
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("下载最新版本") }

            Spacer(Modifier.height(12.dp))

            OutlinedButton(
                onClick = { scope.launch { runSpeedTest() } },
                enabled = !testing,
                modifier = Modifier.fillMaxWidth()
            ) { Text("重新测速") }
        }
    }
}
