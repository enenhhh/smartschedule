package com.enenhhh.smartschedule.ui.settings

import android.Manifest
import android.os.Build
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.WbSunny
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SettingSwitchRow
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.components.TimePickerDialogM3
import com.enenhhh.smartschedule.ui.glass.glassAccentCard
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.weather.WeatherFetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun RemindersScreen() {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()

    var tomorrow by remember { mutableStateOf(Prefs.isTomorrowReminderEnabled(context)) }
    var reminderH by remember { mutableStateOf(Prefs.getReminderHour(context)) }
    var reminderM by remember { mutableStateOf(Prefs.getReminderMinute(context)) }
    var weather by remember { mutableStateOf(Prefs.isWeatherReminderEnabled(context)) }
    var weatherH by remember { mutableStateOf(Prefs.getWeatherHour(context)) }
    var weatherM by remember { mutableStateOf(Prefs.getWeatherMinute(context)) }
    var city by remember { mutableStateOf(Prefs.getWeatherCity(context)) }
    var persistent by remember { mutableStateOf(Prefs.isPersistentEnabled(context)) }
    var keepAlive by remember { mutableStateOf(Prefs.isKeepAlive(context)) }
    var notificationsOn by remember { mutableStateOf(NotificationManagerCompat.from(context).areNotificationsEnabled()) }
    var exactOk by remember { mutableStateOf(NotificationHelper.canScheduleExact(context)) }

    var pickReminder by remember { mutableStateOf(false) }
    var pickWeather by remember { mutableStateOf(false) }
    var cityDialog by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }

    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    // 回到页面时刷新权限状态
    LaunchedEffect(Unit) {
        notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
        exactOk = NotificationHelper.canScheduleExact(context)
    }

    fun refresh() = NotificationHelper.refreshAll(context)
    fun ensurePermission() {
        if (Build.VERSION.SDK_INT >= 33 && !notificationsOn) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }
    fun two(h: Int, m: Int) = "%02d:%02d".format(h, m)

    SubPage(title = "提醒与通知") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (!notificationsOn || !exactOk) {
                Column(Modifier.fillMaxWidth().glassAccentCard().padding(18.dp)) {
                    Text(
                        if (!notificationsOn) "通知权限未开启" else "未允许精确闹钟",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (!notificationsOn) "没有通知权限，下面的提醒都发不出来。" else "Android 12+ 需要「闹钟和提醒」权限，否则提醒可能延迟。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row {
                        Button(onClick = {
                            if (!notificationsOn) {
                                if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
                                else NotificationHelper.openNotificationSettings(context)
                            } else NotificationHelper.requestExactAlarmPermission(context)
                        }) { Text("去开启") }
                        TextButton(onClick = {
                            notificationsOn = NotificationManagerCompat.from(context).areNotificationsEnabled()
                            exactOk = NotificationHelper.canScheduleExact(context)
                        }) { Text("我已开启") }
                    }
                }
            }

            SectionTitle("明日课程提醒")
            GlassGroup {
                SettingSwitchRow(
                    title = "每天提醒明天的课",
                    subtitle = "按明天的单双周计算，周日也准确",
                    icon = Icons.Rounded.NotificationsActive,
                    checked = tomorrow,
                    onCheckedChange = {
                        tomorrow = it
                        Prefs.setTomorrowReminderEnabled(context, it)
                        if (it) ensurePermission()
                        refresh()
                    }
                )
                RowDivider()
                SettingRow(
                    title = "提醒时间",
                    subtitle = two(reminderH, reminderM),
                    icon = Icons.Rounded.Alarm,
                    onClick = { pickReminder = true }
                )
                RowDivider()
                SettingRow(
                    title = "现在发一条试试",
                    icon = Icons.Default.Notifications,
                    onClick = {
                        ensurePermission()
                        NotificationHelper.createChannels(context)
                        NotificationHelper.triggerScheduleNow(context)
                        scope.launch { snackbar.showSnackbar("已发送测试通知") }
                    },
                    showChevron = false
                )
            }

            SectionTitle("明日天气")
            GlassGroup {
                SettingSwitchRow(
                    title = "天气提醒",
                    subtitle = "数据来自 Open-Meteo，无需注册",
                    icon = Icons.Rounded.WbSunny,
                    checked = weather,
                    onCheckedChange = {
                        weather = it
                        Prefs.setWeatherReminderEnabled(context, it)
                        if (it) ensurePermission()
                        refresh()
                    }
                )
                RowDivider()
                SettingRow(title = "城市", subtitle = city, onClick = { cityDialog = true })
                RowDivider()
                LocationCityRow(onCityChanged = { city = it })
                RowDivider()
                SettingRow(
                    title = "清除定位信息",
                    subtitle = "保留城市名，清除本机保存的定位坐标",
                    onClick = {
                        com.enenhhh.smartschedule.weather.WeatherLocation.clear(context)
                        scope.launch { snackbar.showSnackbar("已清除定位坐标，天气将按城市名查询") }
                    },
                    showChevron = false
                )
                RowDivider()
                SettingRow(title = "提醒时间", subtitle = two(weatherH, weatherM), icon = Icons.Rounded.Alarm, onClick = { pickWeather = true })
                RowDivider()
                SettingRow(
                    title = if (testing) "正在获取…" else "现在查一次明天天气",
                    subtitle = "结果会以通知形式显示",
                    onClick = {
                        if (testing) return@SettingRow
                        ensurePermission()
                        testing = true
                        scope.launch {
                            val text = withContext(Dispatchers.IO) {
                                runCatching { NotificationHelper.triggerWeatherNowBlocking(context) }
                                    .getOrElse { "获取失败：${it.message}" }
                            }
                            testing = false
                            snackbar.showSnackbar(text)
                        }
                    },
                    showChevron = false
                )
            }

            SectionTitle("常驻通知")
            GlassGroup {
                SettingSwitchRow(
                    title = "通知栏常驻今日 / 明日课程",
                    subtitle = "每天 0 点自动刷新",
                    icon = Icons.Rounded.PushPin,
                    checked = persistent,
                    onCheckedChange = {
                        persistent = it
                        Prefs.setPersistentEnabled(context, it)
                        if (it) ensurePermission()
                        refresh()
                    }
                )
                RowDivider()
                SettingSwitchRow(
                    title = "前台服务保活",
                    subtitle = "被系统频繁清理时再开；会多一条「正在运行」通知",
                    icon = Icons.Rounded.BatteryChargingFull,
                    checked = keepAlive,
                    onCheckedChange = {
                        keepAlive = it
                        Prefs.setKeepAlive(context, it)
                        if (it) ensurePermission()
                        refresh()
                    },
                    enabled = persistent
                )
                RowDivider()
                SettingRow(
                    title = "忽略电池优化",
                    subtitle = "部分国产系统需要，否则到点可能不提醒",
                    onClick = { NotificationHelper.requestIgnoreBatteryOptimizations(context) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (pickReminder) {
        TimePickerDialogM3(initialHour = reminderH, initialMinute = reminderM, title = "明日课程提醒时间", onDismiss = { pickReminder = false }) { h, m ->
            reminderH = h; reminderM = m
            Prefs.setReminderTime(context, h, m)
            refresh()
        }
    }
    if (pickWeather) {
        TimePickerDialogM3(initialHour = weatherH, initialMinute = weatherM, title = "天气提醒时间", onDismiss = { pickWeather = false }) { h, m ->
            weatherH = h; weatherM = m
            Prefs.setWeatherTime(context, h, m)
            refresh()
        }
    }
    if (cityDialog) {
        var text by remember { mutableStateOf(city) }
        var error by remember { mutableStateOf<String?>(null) }
        var checking by remember { mutableStateOf(false) }
        GlassDialog(
            onDismiss = { cityDialog = false },
            title = "天气城市",
            confirmText = if (checking) "检查中…" else "保存",
            confirmEnabled = text.isNotBlank() && !checking,
            onConfirm = {
                checking = true
                scope.launch {
                    val err = withContext(Dispatchers.IO) { runCatching { WeatherFetcher.checkCity(text) }.getOrElse { "网络错误：${it.message}" } }
                    checking = false
                    if (err == null) {
                        city = text.trim()
                        Prefs.setWeatherCity(context, city)
                        cityDialog = false
                        snackbar.showSnackbar("城市已更新为 $city")
                    } else error = err
                }
            }
        ) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it; error = null },
                label = { Text("城市名") },
                placeholder = { Text("例如 上海、广州、Chengdu") },
                singleLine = true,
                isError = error != null,
                supportingText = { Text(error ?: "保存时会联网确认城市是否存在") },
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
