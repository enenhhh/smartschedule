package com.enenhhh.smartschedule.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material3.Button
import androidx.compose.material3.CircularWavyProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.ConfirmDialog
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.GlassSheet
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SettingSwitchRow
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.util.BackupHelper
import com.enenhhh.smartschedule.util.ShareCodeHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun DataScreen() {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val version = ScheduleManager.version
    val timetables = remember(version) { manager.getTimetables() }
    val active = remember(version) { manager.getActiveTimetable() }

    var shareAll by remember { mutableStateOf(false) }
    var uploading by remember { mutableStateOf(false) }
    var shareResult by remember { mutableStateOf<String?>(null) }
    var codeInput by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<BackupHelper.BackupPreview?>(null) }
    var importSource by remember { mutableStateOf("") }
    var confirmWipe by remember { mutableStateOf(false) }
    var records by remember { mutableStateOf(ShareCodeHelper.getShareCodeRecords(context)) }

    fun toast(msg: String) = scope.launch { snackbar.showSnackbar(msg) }

    val createDoc = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            busy = "正在导出…"
            val r = withContext(Dispatchers.IO) { runCatching { BackupHelper.writeBackup(context, manager, uri) } }
            busy = null
            toast(if (r.isSuccess) "备份已保存" else "导出失败：${r.exceptionOrNull()?.message}")
        }
    }
    val openDoc = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            busy = "正在读取…"
            val r = withContext(Dispatchers.IO) { runCatching { BackupHelper.readBackupFile(context, uri) } }
            busy = null
            r.onSuccess { pendingImport = it; importSource = "备份文件" }.onFailure { toast("读取失败：${it.message}") }
        }
    }
    val openTree = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) scope.launch {
            busy = "正在读取…"
            val r = withContext(Dispatchers.IO) { runCatching { BackupHelper.readLegacyFolder(context, uri) } }
            busy = null
            r.onSuccess {
                if (it.schedule.isEmpty && it.homework.isEmpty()) toast("这个文件夹里没有找到课表备份")
                else { pendingImport = it; importSource = "旧版备份文件夹" }
            }.onFailure { toast("读取失败：${it.message}") }
        }
    }

    val payloadSize = remember(version, shareAll, active) {
        runCatching { ShareCodeHelper.payloadSize(ShareCodeHelper.buildPayload(context, manager, if (shareAll) null else listOfNotNull(active?.id))) }.getOrDefault(0)
    }

    SubPage(title = "备份、恢复与分享码") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            SectionTitle("分享码 · 把课表传给同学")
            GlassGroup {
                if (timetables.size > 1) {
                    SettingSwitchRow(
                        title = "分享全部课表",
                        subtitle = if (shareAll) "包含 ${timetables.size} 张课表" else "只分享当前课表「${active?.name ?: ""}」",
                        checked = shareAll,
                        onCheckedChange = { shareAll = it }
                    )
                    RowDivider()
                }
                SettingRow(
                    title = if (uploading) "正在生成…" else "生成分享码",
                    subtitle = "只上传课表、时间方案、简称和开学日期（约 ${(payloadSize / 1024f).let { "%.1f".format(it) }} KB）· ${ShareCodeHelper.EXPIRE_DAYS} 天内有效",
                    icon = Icons.Default.Share,
                    onClick = {
                        if (uploading) return@SettingRow
                        if (active == null) { toast("还没有课表可以分享"); return@SettingRow }
                        uploading = true
                        scope.launch {
                            val payload = ShareCodeHelper.buildPayload(context, manager, if (shareAll) null else listOf(active.id))
                            val r = ShareCodeHelper.upload(context, payload)
                            uploading = false
                            if (r.ok) { shareResult = r.code; records = ShareCodeHelper.getShareCodeRecords(context) }
                            else toast(r.error ?: "生成失败")
                        }
                    },
                    showChevron = false,
                    trailing = if (uploading) ({ CircularWavyProgressIndicator(modifier = Modifier.height(28.dp)) }) else null
                )
                RowDivider()
                SettingRow(
                    title = "输入分享码导入",
                    subtitle = "导入前可以预览，不会覆盖你的设置和作业",
                    icon = Icons.Rounded.Pin,
                    onClick = { codeInput = true }
                )
                if (records.isNotEmpty()) {
                    RowDivider()
                    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                        Text("最近生成的分享码", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        records.take(5).forEach { r ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(r.code, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(end = 12.dp))
                                Text(r.createdAt, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                                IconButton(onClick = { copyToClipboard(context, r.code); toast("已复制 ${r.code}") }) {
                                    Icon(Icons.Rounded.ContentCopy, contentDescription = "复制", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            SectionTitle("本地备份")
            GlassGroup {
                SettingRow(
                    title = "导出备份文件",
                    subtitle = "课表 + 时间方案 + 作业 + 设置，保存为一个 .json 文件",
                    icon = Icons.Rounded.Upload,
                    onClick = { createDoc.launch(BackupHelper.suggestedFileName()) },
                    showChevron = false
                )
                RowDivider()
                SettingRow(
                    title = "从备份文件恢复",
                    icon = Icons.Rounded.Download,
                    onClick = { openDoc.launch(arrayOf("application/json", "text/plain", "application/octet-stream", "*/*")) },
                    showChevron = false
                )
                RowDivider()
                SettingRow(
                    title = "从旧版备份文件夹导入",
                    subtitle = "4.x 及更早版本导出的 backup_xxx 文件夹",
                    icon = Icons.Rounded.FolderOpen,
                    onClick = { openTree.launch(null) },
                    showChevron = false
                )
            }

            SectionTitle("危险操作")
            GlassGroup {
                SettingRow(
                    title = "清空全部课表和时间方案",
                    subtitle = "作业和设置会保留；建议先导出备份",
                    icon = Icons.Rounded.Restore,
                    iconTint = MaterialTheme.colorScheme.error,
                    onClick = { confirmWipe = true },
                    showChevron = false
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    busy?.let { msg ->
        GlassDialog(onDismiss = {}, title = null, confirmText = null, dismissText = null, dismissOnClickOutside = false) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularWavyProgressIndicator(modifier = Modifier.height(36.dp))
                Spacer(Modifier.padding(8.dp))
                Text(msg)
            }
        }
    }

    shareResult?.let { code ->
        GlassDialog(
            onDismiss = { shareResult = null },
            title = "分享码已生成",
            confirmText = "复制",
            onConfirm = { copyToClipboard(context, code); toast("已复制 $code") },
            dismissText = "关闭"
        ) {
            Text(
                code.chunked(3).joinToString("  "),
                style = MaterialTheme.typography.displayMedium.copy(letterSpacing = 4.sp),
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "同学在「设置 › 备份、恢复与分享码 › 输入分享码导入」里输入即可。${ShareCodeHelper.EXPIRE_DAYS} 天后自动失效。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TextButton(onClick = {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "我的智能课表分享码：$code（${ShareCodeHelper.EXPIRE_DAYS} 天内有效）。在智能课表 App 的「设置 › 备份、恢复与分享码」里输入即可导入。")
                }
                runCatching { context.startActivity(Intent.createChooser(send, "发送分享码")) }
            }) { Text("通过其它应用发送") }
        }
    }

    if (codeInput) {
        var code by remember { mutableStateOf("") }
        var loading by remember { mutableStateOf(false) }
        var error by remember { mutableStateOf<String?>(null) }
        GlassDialog(
            onDismiss = { if (!loading) codeInput = false },
            title = "输入分享码",
            confirmText = if (loading) "获取中…" else "获取",
            confirmEnabled = ShareCodeHelper.isValidCode(code) && !loading,
            onConfirm = {
                loading = true; error = null
                scope.launch {
                    val r = ShareCodeHelper.download(code)
                    loading = false
                    r.onSuccess {
                        codeInput = false
                        pendingImport = BackupHelper.BackupPreview(it, emptyList(), null, it.created)
                        importSource = "分享码 $code"
                    }.onFailure { error = it.message }
                }
            }
        ) {
            OutlinedTextField(
                value = code,
                onValueChange = { code = it.filter { c -> c.isDigit() }.take(6); error = null },
                label = { Text("6 位数字") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "由分享者在 App 里生成") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = MaterialTheme.typography.headlineSmall.copy(letterSpacing = 6.sp, textAlign = TextAlign.Center),
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    pendingImport?.let { preview ->
        ImportPreviewSheet(
            preview = preview,
            source = importSource,
            hasExisting = timetables.isNotEmpty(),
            onDismiss = { pendingImport = null },
            onConfirm = { replace, includeHomework, includeSettings ->
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        runCatching { BackupHelper.restore(context, manager, preview, replace, includeHomework, includeSettings) }
                    }
                    pendingImport = null
                    toast(if (r.isSuccess) "导入完成" else "导入失败：${r.exceptionOrNull()?.message}")
                }
            }
        )
    }

    if (confirmWipe) {
        ConfirmDialog(
            title = "清空全部课表和时间方案？",
            message = "将删除 ${timetables.size} 张课表和所有时间方案，不可恢复。作业、简称和设置会保留。",
            onDismiss = { confirmWipe = false },
            onConfirm = {
                manager.importData(emptyList(), emptyList(), emptyMap(), replace = true)
                toast("已清空")
            },
            confirmText = "清空",
            destructive = true
        )
    }
}

@Composable
private fun ImportPreviewSheet(
    preview: BackupHelper.BackupPreview,
    source: String,
    hasExisting: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (replace: Boolean, includeHomework: Boolean, includeSettings: Boolean) -> Unit
) {
    var replace by remember { mutableStateOf(!hasExisting) }
    var includeHomework by remember { mutableStateOf(preview.homework.isNotEmpty()) }
    var includeSettings by remember { mutableStateOf(false) }
    val s = preview.schedule
    GlassSheet(onDismiss = onDismiss) {
        Text("导入预览", style = MaterialTheme.typography.titleLarge)
        Text("来源：$source${if (s.legacy) "（旧版格式，已自动转换）" else ""}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            PreviewLine("课表", if (s.timetables.isEmpty()) "无" else s.timetables.joinToString("、") { it.name } + "（共 ${s.courseCount} 节课）")
            PreviewLine("时间方案", if (s.timePlans.isEmpty()) "无" else s.timePlans.joinToString("、") { "${it.name}（${it.periods.size} 节）" })
            PreviewLine("课程简称", if (s.shortNames.isEmpty()) "无" else "${s.shortNames.size} 条")
            PreviewLine("开学日期", s.startDate?.toString() ?: "无")
            if (preview.homework.isNotEmpty()) PreviewLine("作业", "${preview.homework.size} 项")
            if (preview.settings != null) PreviewLine("设置", "主题、提醒等")
        }
        Spacer(Modifier.height(14.dp))
        if (hasExisting && !s.isEmpty) {
            Text("导入方式", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            PillTabs(
                options = listOf(false, true),
                selected = replace,
                onSelect = { replace = it },
                label = { if (it) "替换现有课表" else "合并（推荐）" },
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                if (replace) "现有课表和时间方案会被删除，换成导入的内容。" else "导入的课表会追加进来，同名时间方案会被覆盖，其它保持不变。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (preview.homework.isNotEmpty()) {
            Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("同时导入作业（${preview.homework.size} 项）", modifier = Modifier.weight(1f))
                Switch(checked = includeHomework, onCheckedChange = { includeHomework = it })
            }
        }
        if (preview.settings != null) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("同时恢复设置（主题、提醒等）", modifier = Modifier.weight(1f))
                Switch(checked = includeSettings, onCheckedChange = { includeSettings = it })
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilledTonalButton(onClick = onDismiss, modifier = Modifier.weight(1f)) { Text("取消") }
            Button(onClick = { onConfirm(replace, includeHomework, includeSettings) }, modifier = Modifier.weight(1f), enabled = !s.isEmpty || includeHomework || includeSettings) { Text("导入") }
        }
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun PreviewLine(label: String, value: String) {
    Row {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(end = 12.dp))
        Text(value, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
    }
}

private fun copyToClipboard(context: Context, text: String) {
    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    cm.setPrimaryClip(ClipData.newPlainText("share_code", text))
}
