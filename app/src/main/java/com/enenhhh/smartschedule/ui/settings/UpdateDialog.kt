package com.enenhhh.smartschedule.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.UpdateChecker
import kotlinx.coroutines.launch
import java.io.File

/**
 * 发现新版本的对话框：显示更新说明，支持下载进度、跳过此版本。
 */
@Composable
fun UpdateDialog(info: UpdateChecker.UpdateInfo, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    var indeterminate by remember { mutableStateOf(false) }
    var downloaded by remember { mutableStateOf<File?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    fun startDownload() {
        if (downloading) return
        downloading = true; error = null
        scope.launch {
            try {
                val file = UpdateChecker.download(context, info) { p ->
                    if (p < 0f) indeterminate = true else { indeterminate = false; progress = p }
                }
                downloaded = file
                downloading = false
                val ok = UpdateChecker.install(context, file)
                if (!ok) error = "请先允许「安装未知应用」，然后回来点「安装」"
            } catch (e: Exception) {
                downloading = false
                error = "下载失败：${e.message ?: "网络错误"}"
            }
        }
    }

    GlassDialog(
        onDismiss = { if (!downloading) onDismiss() },
        title = "发现新版本 ${info.latest}",
        confirmText = when {
            downloaded != null -> "安装"
            downloading -> "下载中…"
            else -> "下载更新"
        },
        confirmEnabled = !downloading,
        onConfirm = {
            val f = downloaded
            if (f != null) {
                if (!UpdateChecker.install(context, f)) error = "请先允许「安装未知应用」，然后回来点「安装」"
            } else startDownload()
        },
        dismissText = if (downloading) null else "稍后",
        dismissOnClickOutside = !downloading
    ) {
        Text(
            "当前版本 ${UpdateChecker.currentVersion(context)} · 来源：${info.source.name}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        Column(Modifier.heightIn(max = 260.dp).verticalScroll(rememberScrollState())) {
            Text(
                info.changelog.ifBlank { "暂无更新说明" }
                    .lines().joinToString("\n") { it.trimStart('#', ' ').let { l -> if (it.trimStart().startsWith("-") || it.trimStart().startsWith("*")) "• " + l.trimStart('-', '*', ' ') else l } },
                style = MaterialTheme.typography.bodyMedium
            )
        }
        if (downloading || downloaded != null) {
            Spacer(Modifier.height(14.dp))
            if (indeterminate && downloaded == null) {
                LinearWavyProgressIndicator(modifier = Modifier.fillMaxWidth())
            } else {
                LinearWavyProgressIndicator(progress = { if (downloaded != null) 1f else progress }, modifier = Modifier.fillMaxWidth())
            }
            Text(
                if (downloaded != null) "下载完成，正在打开安装程序…" else if (indeterminate) "正在下载…" else "正在下载 ${(progress * 100).toInt()}%",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
        if (!downloading && downloaded == null) {
            TextButton(onClick = { Prefs.setSkippedVersion(context, info.latest); onDismiss() }) { Text("跳过这个版本") }
        }
    }
}
