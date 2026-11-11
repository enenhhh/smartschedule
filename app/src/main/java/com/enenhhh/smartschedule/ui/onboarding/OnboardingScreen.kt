package com.enenhhh.smartschedule.ui.onboarding

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.EventRepeat
import androidx.compose.material.icons.rounded.Pin
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.DatePickerDialogM3
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.TimePickerDialogM3
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.nav.Route
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.ShareCodeHelper
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val STEPS = 5

/**
 * 首次启动引导：欢迎 → 学期周次 → 作息时间 → 课表来源 → 通知。
 * 每一步都可以跳过；全部完成后直接进入今日页（或课表编辑器）。
 */
@Composable
fun OnboardingScreen() {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val today = remember { LocalDate.now() }

    var step by rememberSaveable { mutableIntStateOf(0) }
    var forward by remember { mutableStateOf(true) }

    // 学期
    var startDate by remember { mutableStateOf(if (Prefs.isStartDateSet(context)) Prefs.getStartDate(context) else Prefs.guessSemesterStart(today)) }
    var datePicker by remember { mutableStateOf(false) }
    val week = DateUtils.weekNumber(startDate, today)

    // 作息
    val existingPlans = remember { manager.getTimePlans() }
    var genStart by rememberSaveable { mutableStateOf("08:00") }
    var genLesson by rememberSaveable { mutableIntStateOf(40) }
    var genBreak by rememberSaveable { mutableIntStateOf(10) }
    var genCount by rememberSaveable { mutableIntStateOf(8) }
    var genLunchAfter by rememberSaveable { mutableIntStateOf(4) }
    var startPicker by remember { mutableStateOf(false) }

    // 课表来源
    var choice by rememberSaveable { mutableStateOf("ai") } // ai / manual / code / later
    var code by rememberSaveable { mutableStateOf("") }
    var importing by remember { mutableStateOf(false) }
    var imported by remember { mutableStateOf(false) }

    // 通知
    var wantReminder by rememberSaveable { mutableStateOf(true) }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

    fun go(to: Int) { forward = to > step; step = to.coerceIn(0, STEPS - 1) }
    BackHandler(enabled = step > 0) { go(step - 1) }

    fun finish() {
        Prefs.setStartDate(context, DateUtils.mondayOf(startDate))
        val plan = if (existingPlans.isNotEmpty()) existingPlans.first() else {
            TimePlan(name = "默认作息", periods = TimePlan.generate(genStart, genLesson, genBreak, genCount, genLunchAfter, if (genLunchAfter > 0) 120 else 0))
                .also { manager.saveTimePlan(it) }
        }
        if (wantReminder) {
            Prefs.setTomorrowReminderEnabled(context, true)
            if (Build.VERSION.SDK_INT >= 33) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        Prefs.setFirstRunDone(context, true)
        runCatching { NotificationHelper.createChannels(context); NotificationHelper.refreshAll(context) }
        val existing = manager.getActiveTimetable()
        if (choice == "manual") {
            val target = existing ?: manager.createTimetable("我的课表", plan.name)
            nav.resetTo(Route.Today)
            nav.push(Route.TimetableEditor(target.id))
        } else if (choice == "ai") {
            // 先用刚配好的作息建一张空课表，AI 导入时会更新它
            if (existing == null) manager.createTimetable("我的课表", plan.name)
            nav.resetTo(Route.Timetable)
            nav.push(Route.AiImport("timetable"))
        } else {
            nav.resetTo(Route.Today)
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
        ) {
            // 顶部进度点 + 跳过
            Row(Modifier.fillMaxWidth().padding(top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    repeat(STEPS) { i ->
                        Box(
                            Modifier
                                .height(6.dp)
                                .width(if (i == step) 22.dp else 6.dp)
                                .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape)
                        )
                    }
                }
                if (step in 1 until STEPS - 1) {
                    TextButton(onClick = { go(step + 1) }) { Text("跳过") }
                }
            }

            AnimatedContent(
                targetState = step,
                transitionSpec = {
                    val dir = if (forward) 1 else -1
                    (slideInHorizontally { it / 4 * dir } + fadeIn()) togetherWith (slideOutHorizontally { -it / 4 * dir } + fadeOut())
                },
                modifier = Modifier.weight(1f),
                label = "onboarding"
            ) { s ->
                Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(vertical = 16.dp)) {
                    when (s) {
                        0 -> Welcome()
                        1 -> StepCard(
                            icon = Icons.Rounded.EventRepeat,
                            title = "现在是第几周？",
                            desc = "单双周靠它判断。不确定也没关系，之后可以在「设置 › 学期与周次」改。"
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
                                FilledTonalIconButton(onClick = { if (week > 1) startDate = DateUtils.startDateForCurrentWeek(week - 1, today) }, enabled = week > 1) {
                                    Icon(Icons.Rounded.Remove, contentDescription = "减一周")
                                }
                                Column(Modifier.padding(horizontal = 20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("第 ${week.coerceAtLeast(1)} 周", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                                    Text(if (DateUtils.isOddWeek(week)) "单周" else "双周", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                FilledTonalIconButton(onClick = { startDate = DateUtils.startDateForCurrentWeek(week.coerceAtLeast(1) + 1, today) }) {
                                    Icon(Icons.Rounded.Add, contentDescription = "加一周")
                                }
                            }
                            Spacer(Modifier.height(12.dp))
                            PillTabs(
                                options = listOf(true, false),
                                selected = DateUtils.isOddWeek(week),
                                onSelect = { wantOdd ->
                                    if (wantOdd != DateUtils.isOddWeek(week)) {
                                        val target = if (week > 1) week - 1 else week + 1
                                        startDate = DateUtils.startDateForCurrentWeek(target, today)
                                    }
                                },
                                label = { if (it) "本周是单周" else "本周是双周" },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(10.dp))
                            TextButton(onClick = { datePicker = true }, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                                Text("或直接选开学日期：${DateUtils.format(DateUtils.mondayOf(startDate))}")
                            }
                        }
                        2 -> StepCard(
                            icon = Icons.Rounded.Schedule,
                            title = "每天的作息",
                            desc = if (existingPlans.isNotEmpty()) "检测到你已有 ${existingPlans.size} 个时间方案，将继续沿用。" else "先大致填一下，之后每一节都可以单独微调。"
                        ) {
                            if (existingPlans.isEmpty()) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text("第一节开始", modifier = Modifier.weight(1f))
                                    FilledTonalButton(onClick = { startPicker = true }) { Text(genStart) }
                                }
                                MiniSlider("每节 ${genLesson} 分钟", genLesson, 30, 60, 5) { genLesson = it }
                                MiniSlider("课间 ${genBreak} 分钟", genBreak, 5, 20, 5) { genBreak = it }
                                MiniSlider("每天 ${genCount} 节", genCount, 4, 12, 1) { genCount = it; if (genLunchAfter > it) genLunchAfter = it }
                                MiniSlider(if (genLunchAfter == 0) "不设午休" else "午休在第 $genLunchAfter 节后", genLunchAfter, 0, genCount, 1) { genLunchAfter = it }
                                Spacer(Modifier.height(6.dp))
                                val preview = remember(genStart, genLesson, genBreak, genCount, genLunchAfter) {
                                    TimePlan.generate(genStart, genLesson, genBreak, genCount, genLunchAfter, if (genLunchAfter > 0) 120 else 0)
                                }
                                Text(
                                    "预览：" + preview.take(3).joinToString("  ") { "${it.start}-${it.end}" } + (if (preview.size > 3) "  …  ${preview.last().start}-${preview.last().end}" else ""),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                existingPlans.take(3).forEach { p ->
                                    Text("• ${p.name}：${p.periods.size} 节，${p.periods.firstOrNull()?.start} 开始", style = MaterialTheme.typography.bodyMedium)
                                }
                            }
                        }
                        3 -> StepCard(
                            icon = Icons.Rounded.CalendarViewWeek,
                            title = "课表从哪来？",
                            desc = "可以自己点格子填，可以拍照让 AI 识别，也可以输入同学的分享码一键导入。"
                        ) {
                            ChoiceRow("用 AI 拍照导入（推荐）", "拍一张课表交给 DeepSeek 等 AI 助手，不用配置 API", choice == "ai") { choice = "ai" }
                            ChoiceRow("自己填写", "完成引导后直接进入编辑器", choice == "manual") { choice = "manual" }
                            ChoiceRow("用分享码导入", "同学在 App 里生成的 6 位数字", choice == "code") { choice = "code" }
                            ChoiceRow("稍后再说", "先看看界面", choice == "later") { choice = "later" }
                            if (choice == "code") {
                                Spacer(Modifier.height(8.dp))
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    OutlinedTextField(
                                        value = code,
                                        onValueChange = { code = it.filter { c -> c.isDigit() }.take(6) },
                                        label = { Text("分享码") },
                                        singleLine = true,
                                        enabled = !imported,
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        shape = MaterialTheme.shapes.medium,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(Modifier.width(8.dp))
                                    Button(
                                        onClick = {
                                            importing = true
                                            scope.launch {
                                                val r = ShareCodeHelper.download(code)
                                                r.onSuccess { preview ->
                                                    ShareCodeHelper.applyPreview(context, manager, preview, replace = manager.getTimetables().isEmpty(), importStartDate = !Prefs.isStartDateSet(context))
                                                    preview.startDate?.let { startDate = it }
                                                    imported = true
                                                    snackbar.showSnackbar("已导入 ${preview.timetables.size} 张课表")
                                                }.onFailure { snackbar.showSnackbar(it.message ?: "导入失败") }
                                                importing = false
                                            }
                                        },
                                        enabled = ShareCodeHelper.isValidCode(code) && !importing && !imported
                                    ) { Text(if (imported) "已导入" else if (importing) "…" else "导入") }
                                }
                            }
                        }
                        else -> StepCard(
                            icon = Icons.Default.Notifications,
                            title = "要不要每天提醒明天的课？",
                            desc = "默认每晚 19:00 推送明天的课程（周日也会按下一周的单双周算）。可随时在「设置 › 提醒与通知」里改。"
                        ) {
                            ChoiceRow("开启提醒", "接下来会请求通知权限", wantReminder) { wantReminder = true }
                            ChoiceRow("暂时不用", "以后可以再开", !wantReminder) { wantReminder = false }
                        }
                    }
                }
            }

            // 底部按钮
            Row(Modifier.fillMaxWidth().padding(bottom = 20.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) {
                    FilledTonalButton(onClick = { go(step - 1) }, modifier = Modifier.weight(1f)) { Text("上一步") }
                }
                Button(
                    onClick = { if (step == STEPS - 1) finish() else go(step + 1) },
                    modifier = Modifier.weight(if (step > 0) 1.4f else 1f),
                    enabled = !(step == 3 && choice == "code" && !imported && importing)
                ) {
                    Text(
                        when {
                            step == 0 -> "开始"
                            step == STEPS - 1 -> "完成"
                            step == 3 && choice == "code" && !imported -> "下一步（不导入）"
                            else -> "下一步"
                        }
                    )
                }
            }
        }
    }

    if (datePicker) DatePickerDialogM3(initial = startDate, onDismiss = { datePicker = false }, onConfirm = { startDate = DateUtils.mondayOf(it) })
    if (startPicker) {
        val m = TimePlan.timeToMinutes(genStart)
        TimePickerDialogM3(initialHour = m / 60, initialMinute = m % 60, title = "第一节开始", onDismiss = { startPicker = false }) { h, mm -> genStart = TimePlan.minutesToTime(h * 60 + mm) }
    }
}

@Composable
private fun Welcome() {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier
                .size(96.dp)
                .background(Brush.linearGradient(listOf(Color(0xFF1B2A4A), Color(0xFF24395F))), RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.CalendarViewWeek, null, tint = Color(0xFF9FE1CB), modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("智能课表", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
        Text("单双周 · 作业 · 提醒，一眼看清今天", style = MaterialTheme.typography.titleMedium, color = cs.onSurfaceVariant, textAlign = TextAlign.Center)
        Spacer(Modifier.height(28.dp))
        Column(Modifier.fillMaxWidth().glassCard().padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Feature(Icons.Rounded.EventRepeat, "单双周自动切换", "周日晚上看「明天」也不会算错")
            Feature(Icons.Rounded.AutoAwesome, "液态玻璃界面", "Material 3 Expressive，玻璃强度可调、十多种主题色")
            Feature(Icons.Rounded.Pin, "6 位分享码", "课表一键传给同班同学，7 天有效")
        }
        Spacer(Modifier.height(12.dp))
        Text("接下来，按你的习惯设置课表", style = MaterialTheme.typography.labelLarge, color = cs.primary)
    }
}

@Composable
private fun Feature(icon: ImageVector, title: String, desc: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun StepCard(icon: ImageVector, title: String, desc: String, content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
        Box(Modifier.size(56.dp).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape), contentAlignment = Alignment.Center) {
            Icon(icon, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
        Text(desc, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(20.dp))
        Column(Modifier.fillMaxWidth().glassCard().padding(18.dp), content = content)
    }
}

@Composable
private fun ChoiceRow(title: String, desc: String, selected: Boolean, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .background(if (selected) cs.primary.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = if (selected) FontWeight.SemiBold else null)
            Text(desc, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Rounded.Check, null, tint = cs.primary)
    }
}

@Composable
private fun MiniSlider(label: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Slider(
            value = value.toFloat(),
            onValueChange = { onChange((it / step).toInt() * step) },
            valueRange = min.toFloat()..max.toFloat(),
            steps = if (max > min) ((max - min) / step - 1).coerceAtLeast(0) else 0
        )
    }
}
