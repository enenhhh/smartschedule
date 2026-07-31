package com.enenhhh.smartschedule

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.UpdateChecker
import java.io.File

class DebugActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)
        setContent {
            AppTheme {
                DebugScreen(onBack = { finish() }, activity = this)
            }
        }
    }
}

@Composable
private fun DebugScreen(onBack: () -> Unit, activity: Activity) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            AppTopBar(
                title = "调试选项",
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
            DebugCard(
                title = "发送明日课表通知",
                summary = "立即发送一次明日课表通知",
                onClick = {
                    NotificationHelper.triggerScheduleNow(context)
                    Toast.makeText(context, "已发送", Toast.LENGTH_SHORT).show()
                }
            )
            DebugCard(
                title = "发送明日天气通知",
                summary = "立即发送一次明日天气通知",
                onClick = {
                    NotificationHelper.triggerWeatherNow(context)
                    Toast.makeText(context, "已发送", Toast.LENGTH_SHORT).show()
                }
            )
            DebugCard(
                title = "强制更新所有通知",
                summary = "立即刷新常驻和提醒相关通知",
                onClick = {
                    NotificationHelper.refreshAll(context)
                    Toast.makeText(context, "已刷新", Toast.LENGTH_SHORT).show()
                }
            )
            DebugCard(
                title = "下载最新版本安装包",
                summary = "跳过版本比较，直接下载最新安装包",
                onClick = { UpdateChecker.downloadLatestApk(activity) }
            )
            DebugCard(
                title = "打开配置向导",
                summary = "重新打开首次配置引导",
                onClick = { context.startActivity(Intent(context, OnboardingActivity::class.java)) }
            )
            DebugCard(
                title = "查看上次崩溃日志",
                summary = "读取 last_crash.txt 并分享出去",
                buttonText = "分享",
                onClick = {
                    val dir = context.getExternalFilesDir(null) ?: context.filesDir
                    val crash = File(dir, "last_crash.txt")
                    if (!crash.exists()) {
                        Toast.makeText(context, "没有崩溃日志", Toast.LENGTH_SHORT).show()
                    } else {
                        runCatching {
                            val uri = androidx.core.content.FileProvider.getUriForFile(
                                context,
                                "${context.packageName}.fileprovider",
                                crash
                            )
                            context.startActivity(
                                Intent.createChooser(
                                    Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_STREAM, uri)
                                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                    },
                                    "分享崩溃日志"
                                )
                            )
                        }.onFailure {
                            Toast.makeText(context, "分享失败：${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }
}

@Composable
private fun DebugCard(
    title: String,
    summary: String,
    buttonText: String = "执行",
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall)
                Text(
                    summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            OutlinedButton(onClick = onClick) { Text(buttonText) }
        }
    }
}
