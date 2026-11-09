package com.enenhhh.smartschedule.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.rounded.BugReport
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Language
import androidx.compose.material.icons.rounded.OpenInNew
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SettingSwitchRow
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.glass.glassAccentCard
import com.enenhhh.smartschedule.util.CrashLogger
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.UpdateChecker
import kotlinx.coroutines.launch

const val SITE_URL = "https://blog.enhub.top/smartschedule/"
const val REPO_URL = "https://github.com/enenhhh/smartschedule"

@Composable
fun AboutScreen() {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val version = remember { UpdateChecker.currentVersion(context) }

    var checking by remember { mutableStateOf(false) }
    var updateInfo by remember { mutableStateOf<UpdateChecker.UpdateInfo?>(null) }
    var autoCheck by remember { mutableStateOf(Prefs.isAutoCheckUpdate(context)) }
    var source by remember { mutableStateOf(Prefs.getUpdateSource(context).coerceIn(UpdateChecker.sources.indices)) }
    var speedResults by remember { mutableStateOf<List<UpdateChecker.SourceTestResult>?>(null) }
    var testing by remember { mutableStateOf(false) }
    var crashDialog by remember { mutableStateOf(false) }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { scope.launch { snackbar.showSnackbar("没有可打开链接的应用") } }
    }
    fun copy(label: String, text: String) {
        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        cm.setPrimaryClip(ClipData.newPlainText(label, text))
        scope.launch { snackbar.showSnackbar("已复制 $text") }
    }

    SubPage(title = "关于与更新") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(Modifier.fillMaxWidth().glassAccentCard().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(56.dp)
                        .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF24395F))), RoundedCornerShape(16.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Rounded.CalendarViewWeek, null, tint = Color(0xFF9FE1CB), modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text("智能课表", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, color = cs.onPrimaryContainer)
                    Text("版本 $version · Material 3 Expressive · 液态玻璃", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                    Text("by enenhhh", style = MaterialTheme.typography.bodySmall, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }

            SectionTitle("更新")
            GlassGroup {
                SettingRow(
                    title = if (checking) "正在检查…" else "检查更新",
                    subtitle = Prefs.getLastUpdateCheck(context).takeIf { it > 0 }?.let { "上次检查：${java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date(it))}" } ?: "还没检查过",
                    icon = Icons.Rounded.SystemUpdate,
                    onClick = {
                        if (checking) return@SettingRow
                        checking = true
                        scope.launch {
                            when (val r = UpdateChecker.check(context, source)) {
                                is UpdateChecker.CheckResult.UpdateAvailable -> updateInfo = r.info
                                is UpdateChecker.CheckResult.UpToDate -> snackbar.showSnackbar("已是最新版本（${r.latest}）")
                                is UpdateChecker.CheckResult.Failed -> snackbar.showSnackbar("检查失败：${r.message}")
                            }
                            checking = false
                        }
                    },
                    showChevron = false
                )
                RowDivider()
                SettingSwitchRow(
                    title = "启动时自动检查",
                    subtitle = "每 12 小时最多检查一次，仅使用 HTTPS",
                    checked = autoCheck,
                    onCheckedChange = { autoCheck = it; Prefs.setAutoCheckUpdate(context, it) }
                )
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("更新源", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    PillTabs(
                        options = UpdateChecker.sources.indices.toList(),
                        selected = source,
                        onSelect = { source = it; Prefs.setUpdateSource(context, it) },
                        label = { UpdateChecker.sources[it].name },
                        modifier = Modifier.fillMaxWidth()
                    )
                    speedResults?.let { list ->
                        Spacer(Modifier.height(8.dp))
                        list.forEach { r ->
                            Text(
                                "${r.source.name}：" + (if (r.latencyMs != null) "${r.latencyMs} ms · 最新 ${r.latest}" else "失败（${r.error}）"),
                                style = MaterialTheme.typography.bodySmall,
                                color = if (r.latencyMs != null) cs.onSurfaceVariant else cs.error
                            )
                        }
                    }
                }
                RowDivider()
                SettingRow(
                    title = if (testing) "正在测速…" else "测速并自动选择最快的源",
                    onClick = {
                        if (testing) return@SettingRow
                        testing = true
                        scope.launch {
                            val results = UpdateChecker.testAllSources()
                            speedResults = results
                            val best = results.withIndex().filter { it.value.latencyMs != null }.minByOrNull { it.value.latencyMs!! }
                            if (best != null) { source = best.index; Prefs.setUpdateSource(context, best.index) }
                            testing = false
                        }
                    },
                    showChevron = false
                )
            }

            SectionTitle("链接")
            GlassGroup {
                SettingRow(title = "官方网站", subtitle = SITE_URL, icon = Icons.Rounded.Language, onClick = { open(SITE_URL) }, trailing = { Icon(Icons.Rounded.OpenInNew, null, tint = cs.onSurfaceVariant) }, showChevron = false)
                RowDivider()
                SettingRow(title = "GitHub 源码", subtitle = "enenhhh/smartschedule", icon = Icons.Rounded.Code, onClick = { open(REPO_URL) }, trailing = { Icon(Icons.Rounded.OpenInNew, null, tint = cs.onSurfaceVariant) }, showChevron = false)
                RowDivider()
                SettingRow(title = "邮箱", subtitle = "enenhhh@enhub.top（点击复制）", icon = Icons.Default.Email, onClick = { copy("email", "enenhhh@enhub.top") }, showChevron = false)
                RowDivider()
                SettingRow(title = "QQ", subtitle = "6944341（点击复制）", onClick = { copy("qq", "6944341") }, showChevron = false)
                RowDivider()
                SettingRow(title = "Bilibili", subtitle = "UID 1318063928", onClick = { open("https://space.bilibili.com/1318063928") }, showChevron = false)
            }

            SectionTitle("诊断")
            GlassGroup {
                val hasLog = remember { CrashLogger.hasLog(context) }
                SettingRow(
                    title = "崩溃日志",
                    subtitle = if (hasLog) "有上次崩溃的记录，点击查看 / 复制" else "没有崩溃记录",
                    icon = Icons.Rounded.BugReport,
                    onClick = { if (hasLog) crashDialog = true else scope.launch { snackbar.showSnackbar("没有崩溃记录") } },
                    showChevron = hasLog
                )
                RowDivider()
                SettingRow(
                    title = "开源组件",
                    subtitle = "Jetpack Compose · Material 3 · Haze（模糊/玻璃）· MaterialKolor（配色）· Open-Meteo（天气）",
                    icon = Icons.Rounded.Verified
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    updateInfo?.let { info -> UpdateDialog(info = info, onDismiss = { updateInfo = null }) }

    if (crashDialog) {
        val log = remember { CrashLogger.read(context) }
        GlassDialog(
            onDismiss = { crashDialog = false },
            title = "崩溃日志",
            confirmText = "复制",
            onConfirm = { copy("crash", log); crashDialog = false },
            dismissText = "关闭"
        ) {
            Column(Modifier.height(240.dp).verticalScroll(rememberScrollState())) {
                Text(log, style = MaterialTheme.typography.bodySmall)
            }
            androidx.compose.material3.TextButton(onClick = { CrashLogger.clear(context); crashDialog = false }) { Text("清除日志") }
        }
    }
}
