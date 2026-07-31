package com.enenhhh.smartschedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.automirrored.filled.ShortText
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Update
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.AboutActivity
import com.enenhhh.smartschedule.DebugActivity
import com.enenhhh.smartschedule.PlanListActivity
import com.enenhhh.smartschedule.R
import com.enenhhh.smartschedule.ShortNameActivity
import com.enenhhh.smartschedule.UpdateActivity
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.ui.components.AppTextField
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.theme.ThemeState
import com.enenhhh.smartschedule.util.BackupHelper
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.ShareCodeHelper
import kotlinx.coroutines.launch

private data class ThemeOption(val key: String, val label: String, val color: Color)

private val themeOptions = listOf(
    ThemeOption(Prefs.THEME_INDIGO, "靛蓝", Color(0xFF005FB8)),
    ThemeOption(Prefs.THEME_TEAL, "蓝绿", Color(0xFF008272)),
    ThemeOption(Prefs.THEME_ORANGE, "橙红", Color(0xFFD83B01)),
    ThemeOption(Prefs.THEME_PURPLE, "紫罗兰", Color(0xFF8764B8))
)

@Composable
fun SettingsScreen(onBack: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var showFontDialog by remember { mutableStateOf(false) }
    var showDarkDialog by remember { mutableStateOf(false) }
    var showThemeDialog by remember { mutableStateOf(false) }
    var showImportConfirm by remember { mutableStateOf(false) }
    var pendingImportUri by remember { mutableStateOf<Uri?>(null) }

    var startDate by remember { mutableStateOf(Prefs.getStartDate(context)) }
    var reminderEnabled by remember { mutableStateOf(Prefs.isTomorrowReminderEnabled(context)) }
    var reminderTime by remember {
        mutableStateOf(formatTime(Prefs.getReminderHour(context), Prefs.getReminderMinute(context)))
    }
    var weatherEnabled by remember { mutableStateOf(Prefs.isWeatherReminderEnabled(context)) }
    var weatherTime by remember {
        mutableStateOf(formatTime(Prefs.getWeatherHour(context), Prefs.getWeatherMinute(context)))
    }
    var weatherCity by remember { mutableStateOf(Prefs.getWeatherCity(context)) }
    var showCityDialog by remember { mutableStateOf(false) }
    var keepAlive by remember { mutableStateOf(Prefs.isKeepAlive(context)) }
    var persistent by remember { mutableStateOf(Prefs.isPersistentEnabled(context)) }
    var autoCheckUpdate by remember { mutableStateOf(Prefs.isAutoCheckUpdate(context)) }

    var showShareGenDialog by remember { mutableStateOf(false) }
    var showShareImportDialog by remember { mutableStateOf(false) }
    var showShareRecordsDialog by remember { mutableStateOf(false) }
    var shareGenCode by remember { mutableStateOf("") }
    var shareGenLoading by remember { mutableStateOf(false) }
    var shareImportCode by remember { mutableStateOf("") }
    var shareImportLoading by remember { mutableStateOf(false) }
    var shareImportConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val name = BackupHelper.exportToFolder(context, uri)
                Toast.makeText(context, "已导出到 $name", Toast.LENGTH_LONG).show()
            }.onFailure {
                Toast.makeText(context, "导出失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            pendingImportUri = uri
            showImportConfirm = true
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "设置",
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
        ) {
            SettingsGroup("课表") {
                SettingRow(
                    title = "开学日期",
                    summary = "$startDate · 决定单双周的计算基准",
                    icon = Icons.Default.CalendarMonth,
                    trailing = {
                        OutlinedButton(onClick = {
                            pickDate(context, startDate) {
                                startDate = it
                                Prefs.setStartDate(context, it)
                            }
                        }) { Text("修改") }
                    }
                )
                SettingRow(
                    title = "方案管理",
                    summary = "管理课程方案和时间方案",
                    icon = Icons.Default.AccountTree,
                    onClick = { context.startActivity(Intent(context, PlanListActivity::class.java)) }
                )
                SettingRow(
                    title = "简写管理",
                    summary = "管理课程名称简写",
                    icon = Icons.AutoMirrored.Filled.ShortText,
                    onClick = { context.startActivity(Intent(context, ShortNameActivity::class.java)) }
                )
            }

            SettingsGroup("外观") {
                SettingRow(
                    title = "深色模式",
                    summary = when (ThemeState.darkMode) {
                        Prefs.DARK_MODE_ON -> "开启深色"
                        Prefs.DARK_MODE_OFF -> "开启浅色"
                        else -> "跟随系统"
                    },
                    icon = Icons.Default.DarkMode,
                    onClick = { showDarkDialog = true }
                )
                SettingRow(
                    title = "主题色",
                    summary = if (ThemeState.dynamicColor) {
                        "已开启动态取色，主题色不生效"
                    } else {
                        themeOptions.firstOrNull { it.key == ThemeState.themeColor }?.label ?: "靛蓝"
                    },
                    icon = Icons.Default.Palette,
                    enabled = !ThemeState.dynamicColor,
                    onClick = { showThemeDialog = true }
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    SettingSwitchRow(
                        title = "动态取色",
                        summary = "跟随系统壁纸自动配色（Android 12+）",
                        icon = Icons.Default.ColorLens,
                        checked = ThemeState.dynamicColor,
                        onCheckedChange = { ThemeState.setDynamicColor(context, it) }
                    )
                }
                SettingRow(
                    title = "字体设置",
                    summary = if (FontManager.currentMode() == 1) "自定义字体" else "系统默认",
                    icon = Icons.Default.TextFields,
                    onClick = { showFontDialog = true }
                )
            }

            SettingsGroup("提醒") {
                SettingSwitchRow(
                    title = "明日课程提醒",
                    summary = "每天 $reminderTime 推送明天的课",
                    icon = Icons.Default.Notifications,
                    checked = reminderEnabled,
                    onCheckedChange = {
                        reminderEnabled = it
                        Prefs.setTomorrowReminderEnabled(context, it)
                        if (it) NotificationHelper.scheduleTomorrowReminder(context)
                        else NotificationHelper.cancelTomorrowReminder(context)
                    }
                )
                if (reminderEnabled) {
                    SettingRow(
                        title = "提醒时间",
                        summary = reminderTime,
                        icon = Icons.Default.Schedule,
                        trailing = {
                            OutlinedButton(onClick = {
                                pickTime(context, reminderTime) { h, m ->
                                    Prefs.setReminderTime(context, h, m)
                                    reminderTime = formatTime(h, m)
                                    NotificationHelper.scheduleTomorrowReminder(context)
                                }
                            }) { Text("修改") }
                        }
                    )
                }

                SettingSwitchRow(
                    title = "天气提醒",
                    summary = "每天 $weatherTime 推送 $weatherCity 的天气",
                    icon = Icons.Default.WbSunny,
                    checked = weatherEnabled,
                    onCheckedChange = {
                        weatherEnabled = it
                        Prefs.setWeatherReminderEnabled(context, it)
                        if (it) NotificationHelper.scheduleWeatherReminder(context)
                        else NotificationHelper.cancelWeatherReminder(context)
                    }
                )
                if (weatherEnabled) {
                    SettingRow(
                        title = "天气提醒时间",
                        summary = weatherTime,
                        icon = Icons.Default.Schedule,
                        trailing = {
                            OutlinedButton(onClick = {
                                pickTime(context, weatherTime) { h, m ->
                                    Prefs.setWeatherTime(context, h, m)
                                    weatherTime = formatTime(h, m)
                                    NotificationHelper.scheduleWeatherReminder(context)
                                }
                            }) { Text("修改") }
                        }
                    )
                    SettingRow(
                        title = "所在城市",
                        summary = weatherCity,
                        icon = Icons.Default.WbSunny,
                        onClick = { showCityDialog = true }
                    )
                }

                SettingSwitchRow(
                    title = "常驻通知",
                    summary = "在通知栏常驻今日 / 明日课表",
                    icon = Icons.Default.Notifications,
                    checked = persistent,
                    onCheckedChange = {
                        persistent = it
                        Prefs.setPersistentEnabled(context, it)
                        NotificationHelper.updatePersistentNotification(context)
                    }
                )
                SettingSwitchRow(
                    title = "保活服务",
                    summary = "用前台服务减少被系统杀掉的概率，会更耗电",
                    icon = Icons.Default.Notifications,
                    checked = keepAlive,
                    onCheckedChange = {
                        keepAlive = it
                        Prefs.setKeepAlive(context, it)
                        if (it) NotificationHelper.startForegroundWork(context)
                        else NotificationHelper.stopForegroundWork(context)
                    }
                )
            }

            SettingsGroup("配置管理") {
                SettingRow(
                    title = "导出配置",
                    summary = "选择文件夹后导出当前配置",
                    icon = Icons.Default.FileDownload,
                    trailing = {
                        OutlinedButton(onClick = { exportLauncher.launch(null) }) { Text("导出") }
                    }
                )
                SettingRow(
                    title = "导入配置",
                    summary = "选择备份目录并覆盖当前配置",
                    icon = Icons.Default.FileUpload,
                    trailing = {
                        OutlinedButton(onClick = { importLauncher.launch(null) }) { Text("导入") }
                    }
                )
                SettingRow(
                    title = "生成分享码",
                    summary = "生成 6 位分享码，同学输入即可导入你的课表",
                    icon = Icons.Default.QrCode,
                    onClick = { showShareGenDialog = true }
                )
                SettingRow(
                    title = "导入分享码",
                    summary = "输入同学的分享码，导入课表配置",
                    icon = Icons.Default.QrCode,
                    onClick = { showShareImportDialog = true }
                )
                SettingRow(
                    title = "分享码管理",
                    summary = "查看已生成的分享码",
                    icon = Icons.Default.QrCode,
                    onClick = { showShareRecordsDialog = true }
                )
            }

            SettingsGroup("其他") {
                SettingSwitchRow(
                    title = "启动时检查更新",
                    summary = "打开应用时自动检查新版本",
                    icon = Icons.Default.Update,
                    checked = autoCheckUpdate,
                    onCheckedChange = {
                        autoCheckUpdate = it
                        Prefs.setAutoCheckUpdate(context, it)
                    }
                )
                SettingRow(
                    title = "检查更新",
                    summary = "查看当前版本并检查可用更新",
                    icon = Icons.Default.Update,
                    onClick = { context.startActivity(Intent(context, UpdateActivity::class.java)) }
                )
                SettingRow(
                    title = "权限管理",
                    summary = "通知、电池优化、安装应用等权限",
                    icon = Icons.Default.Tune,
                    onClick = {
                        runCatching {
                            context.startActivity(
                                Intent(AndroidSettings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }.onFailure {
                            Toast.makeText(context, "无法打开权限设置", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
                SettingRow(
                    title = "调试选项",
                    summary = "通知测试、强制刷新等开发者工具",
                    icon = Icons.Default.BugReport,
                    onClick = { context.startActivity(Intent(context, DebugActivity::class.java)) }
                )
                SettingRow(
                    title = "关于",
                    summary = "版本信息、开发者联系方式",
                    icon = Icons.Default.Info,
                    onClick = { context.startActivity(Intent(context, AboutActivity::class.java)) }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showFontDialog) {
        FontSwitchDialog { showFontDialog = false }
    }

    if (showDarkDialog) {
        AppDialog(title = "深色模式", onDismissRequest = { showDarkDialog = false }) {
            listOf(
                Prefs.DARK_MODE_SYSTEM to "跟随系统",
                Prefs.DARK_MODE_ON to "开启深色",
                Prefs.DARK_MODE_OFF to "开启浅色"
            ).forEach { (mode, label) ->
                RadioRow(label = label, selected = ThemeState.darkMode == mode) {
                    ThemeState.setDarkMode(context, mode)
                    showDarkDialog = false
                }
            }
        }
    }

    if (showThemeDialog) {
        AppDialog(
            title = "主题色",
            summary = "立即生效，无需重启",
            onDismissRequest = { showThemeDialog = false }
        ) {
            themeOptions.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            ThemeState.setThemeColor(context, option.key)
                            showThemeDialog = false
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    RadioButton(
                        selected = ThemeState.themeColor == option.key,
                        onClick = {
                            ThemeState.setThemeColor(context, option.key)
                            showThemeDialog = false
                        }
                    )
                    Spacer(Modifier.width(4.dp))
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(option.color)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(option.label)
                }
            }
        }
    }

    if (showCityDialog) {
        var draft by remember { mutableStateOf(weatherCity) }
        AppDialog(
            title = "所在城市",
            summary = "用于天气提醒，填写中文城市名即可",
            onDismissRequest = { showCityDialog = false }
        ) {
            AppTextField(
                value = draft,
                onValueChange = { draft = it },
                label = "城市",
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showCityDialog = false }) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    val city = draft.trim()
                    if (city.isNotEmpty()) {
                        weatherCity = city
                        Prefs.setWeatherCity(context, city)
                    }
                    showCityDialog = false
                }) { Text("保存") }
            }
        }
    }

    if (showImportConfirm) {
        AppDialog(
            title = "导入配置",
            summary = "会覆盖当前全部配置，确定继续吗？",
            onDismissRequest = {
                showImportConfirm = false
                pendingImportUri = null
            }
        ) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    showImportConfirm = false
                    pendingImportUri = null
                }) { Text("取消") }
                Spacer(Modifier.width(8.dp))
                FilledTonalButton(onClick = {
                    val uri = pendingImportUri
                    showImportConfirm = false
                    pendingImportUri = null
                    if (uri != null) {
                        runCatching {
                            BackupHelper.importFromFolder(context, uri)
                            Toast.makeText(context, "导入成功", Toast.LENGTH_LONG).show()
                        }.onFailure {
                            Toast.makeText(context, "导入失败：${it.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("确定") }
            }
        }
    }

    if (showShareGenDialog) {
        AppDialog(
            title = "生成分享码",
            summary = if (shareGenCode.isNotBlank()) "分享码已生成，有效期 7 天" else "将当前配置上传并生成 6 位分享码",
            onDismissRequest = {
                showShareGenDialog = false
                shareGenCode = ""
            }
        ) {
            if (shareGenCode.isNotBlank()) {
                Text(
                    text = shareGenCode,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "把这个码发给同学即可",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(12.dp))
            }
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = {
                    showShareGenDialog = false
                    shareGenCode = ""
                }) { Text("关闭") }
                if (shareGenCode.isBlank()) {
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = !shareGenLoading,
                        onClick = {
                            shareGenLoading = true
                            scope.launch {
                                val result = ShareCodeHelper.upload(context)
                                shareGenLoading = false
                                if (result.success) {
                                    shareGenCode = result.code
                                } else {
                                    Toast.makeText(context, result.error, Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    ) { Text(if (shareGenLoading) "生成中…" else "生成") }
                }
            }
        }
    }

    if (showShareRecordsDialog) {
        val records = remember { ShareCodeHelper.getShareCodeRecords(context) }
        AppDialog(
            title = "分享码管理",
            summary = "已生成的分享码（有效期 7 天）",
            onDismissRequest = { showShareRecordsDialog = false }
        ) {
            if (records.isEmpty()) {
                Text(
                    text = "暂无分享码记录",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            } else {
                records.forEach { record ->
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = record.code,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Text(
                                text = record.createdAt,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { showShareRecordsDialog = false }) { Text("关闭") }
            }
        }
    }

    if (showShareImportDialog) {
        AppDialog(
            title = "导入分享码",
            summary = "输入同学给你的 6 位分享码",
            onDismissRequest = {
                showShareImportDialog = false
                shareImportCode = ""
                shareImportConfirm = false
            }
        ) {
            if (!shareImportConfirm) {
                AppTextField(
                    value = shareImportCode,
                    onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) shareImportCode = it },
                    label = "分享码",
                    keyboardType = KeyboardType.Number,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = {
                        showShareImportDialog = false
                        shareImportCode = ""
                    }) { Text("取消") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = shareImportCode.length == 6,
                        onClick = { shareImportConfirm = true }
                    ) { Text("下一步") }
                }
            } else {
                Text(
                    text = "将会覆盖本地数据，是否继续？",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error
                )
                Spacer(Modifier.height(12.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = { shareImportConfirm = false }) { Text("返回") }
                    Spacer(Modifier.width(8.dp))
                    FilledTonalButton(
                        enabled = !shareImportLoading,
                        onClick = {
                            shareImportLoading = true
                            scope.launch {
                                val msg = ShareCodeHelper.downloadAndImport(context, shareImportCode)
                                shareImportLoading = false
                                Toast.makeText(context, msg, Toast.LENGTH_LONG).show()
                                if (msg == "导入成功") {
                                    showShareImportDialog = false
                                    shareImportCode = ""
                                    shareImportConfirm = false
                                }
                            }
                        }
                    ) { Text(if (shareImportLoading) "导入中…" else "确认导入") }
                }
            }
        }
    }
}

// ---------------------------------------------------------------- 通用行组件

@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
        )
        content()
    }
}

@Composable
private fun SettingRow(
    title: String,
    summary: String,
    icon: ImageVector,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    trailing: @Composable (() -> Unit)? = null
) {
    val alpha = if (enabled) 1f else 0.45f
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp)
            .then(
                if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier
            ),
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
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary.copy(alpha = alpha)
            )
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
                )
                if (summary.isNotBlank()) {
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = alpha)
                    )
                }
            }
            if (trailing != null) {
                Spacer(Modifier.width(12.dp))
                trailing()
            }
        }
    }
}

@Composable
private fun SettingSwitchRow(
    title: String,
    summary: String,
    icon: ImageVector,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    SettingRow(
        title = title,
        summary = summary,
        icon = icon,
        onClick = { onCheckedChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = onCheckedChange) }
    )
}

@Composable
private fun RadioRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label)
    }
}

// ---------------------------------------------------------------- 工具

private fun formatTime(hour: Int, minute: Int): String = "%02d:%02d".format(hour, minute)

private fun pickTime(context: Context, current: String, onPicked: (Int, Int) -> Unit) {
    val parts = current.split(":")
    val hour = parts.getOrNull(0)?.toIntOrNull() ?: 8
    val minute = parts.getOrNull(1)?.toIntOrNull() ?: 0
    TimePickerDialog(
        ContextThemeWrapper(context, R.style.Theme_smartschedule),
        { _, h, m -> onPicked(h, m) },
        hour, minute, true
    ).show()
}

private fun pickDate(context: Context, current: String, onPicked: (String) -> Unit) {
    val cal = java.util.Calendar.getInstance()
    runCatching {
        val parts = current.split("-")
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }
    DatePickerDialog(
        ContextThemeWrapper(context, R.style.Theme_smartschedule),
        { _, y, m, d -> onPicked("%04d-%02d-%02d".format(y, m + 1, d)) },
        cal.get(java.util.Calendar.YEAR),
        cal.get(java.util.Calendar.MONTH),
        cal.get(java.util.Calendar.DAY_OF_MONTH)
    ).show()
}
