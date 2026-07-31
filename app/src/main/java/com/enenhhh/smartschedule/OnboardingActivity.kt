package com.enenhhh.smartschedule

import android.app.DatePickerDialog
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.view.ContextThemeWrapper
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationManagerCompat
import com.enenhhh.smartschedule.notifications.NotificationHelper
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.ui.components.AppTextField
import com.enenhhh.smartschedule.ui.components.BrandTitle
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.BackupHelper
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.util.ShareCodeHelper
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class OnboardingActivity : ComponentActivity() {

    /** 用户去系统设置授权后回到这里，自增以触发权限状态重新读取（比定时轮询省电） */
    private var resumeTick by mutableIntStateOf(0)

    private val requestNotifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            resumeTick++
            if (!granted) {
                Toast.makeText(this, "没有通知权限，课表提醒将无法送达", Toast.LENGTH_SHORT).show()
            }
        }

    override fun onResume() {
        super.onResume()
        resumeTick++
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)

        setContent {
            AppTheme {
                OnboardingScreen(
                    resumeTick = resumeTick,
                    onAskNotification = {
                        NotificationHelper.maybeRequestPostNotifications(this, requestNotifPermission)
                    },
                    onAskBattery = { NotificationHelper.requestIgnoreBatteryOptimizations(this) },
                    onFinish = { openPlans ->
                        Prefs.setFirstRunDone(this, true)
                        startActivity(
                            Intent(this, MainActivity::class.java).apply {
                                addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
                            }
                        )
                        if (openPlans) {
                            startActivity(Intent(this, PlanListActivity::class.java))
                        }
                        finish()
                    }
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 步骤定义

private enum class OnboardStep(val label: String) {
    Welcome("欢迎"),
    DataSource("数据"),
    Semester("开学"),
    Permission("权限"),
    Done("完成");

    companion object {
        val ordered: List<OnboardStep> = OnboardStep.entries
    }
}

/** 用户在第 2 步选了哪种数据来源 */
private enum class DataChoice { None, Fresh, Backup, ShareCode }

// ---------------------------------------------------------------- 主流程

@Composable
private fun OnboardingScreen(
    resumeTick: Int,
    onAskNotification: () -> Unit,
    onAskBattery: () -> Unit,
    onFinish: (openPlans: Boolean) -> Unit
) {
    val context = LocalContext.current
    var stepIndex by remember { mutableIntStateOf(0) }
    var choice by remember { mutableStateOf(DataChoice.None) }
    var startDate by remember { mutableStateOf(smartDefaultStartDate(context)) }

    // 用户可能一路「下一步」不碰开学日期，这里保证智能默认值也会落盘
    LaunchedEffect(startDate) {
        Prefs.setStartDate(context, startDate)
    }

    val steps = OnboardStep.ordered
    val step = steps[stepIndex]

    fun goNext() {
        if (stepIndex < steps.lastIndex) stepIndex++
    }

    fun applyFreshStart() {
        choice = DataChoice.Fresh
        goNext()
    }

    // 返回键：回上一步，而不是直接退出应用
    BackHandler(enabled = stepIndex > 0) { stepIndex-- }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri ->
        if (uri != null) {
            runCatching {
                BackupHelper.importFromFolder(context, uri)
            }.onSuccess {
                choice = DataChoice.Backup
                startDate = Prefs.getStartDate(context)
                Toast.makeText(context, "恢复成功", Toast.LENGTH_SHORT).show()
                goNext()
            }.onFailure {
                Toast.makeText(context, "恢复失败：${it.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
        ) {
            StepHeader(
                steps = steps,
                currentIndex = stepIndex,
                modifier = Modifier.padding(top = 16.dp, bottom = 20.dp)
            )

            AnimatedContent(
                targetState = stepIndex,
                transitionSpec = {
                    val forward = targetState > initialState
                    val slide = if (forward) 1 else -1
                    (slideInHorizontally(tween(280)) { it * slide } + fadeIn(tween(280))) togetherWith
                        (slideOutHorizontally(tween(280)) { -it * slide } + fadeOut(tween(200)))
                },
                modifier = Modifier.weight(1f),
                label = "onboarding_step"
            ) { index ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    when (steps[index]) {
                        OnboardStep.Welcome -> WelcomeStep()

                        OnboardStep.DataSource -> DataSourceStep(
                            choice = choice,
                            onFresh = ::applyFreshStart,
                            onRestore = { importLauncher.launch(null) },
                            onShareCodeDone = {
                                choice = DataChoice.ShareCode
                                startDate = Prefs.getStartDate(context)
                                goNext()
                            }
                        )

                        OnboardStep.Semester -> SemesterStep(
                            startDate = startDate,
                            onPick = {
                                pickDate(context, startDate) { picked ->
                                    startDate = picked
                                    Prefs.setStartDate(context, picked)
                                }
                            },
                            onUseThisMonday = {
                                val monday = thisMonday()
                                startDate = monday
                                Prefs.setStartDate(context, monday)
                            }
                        )

                        OnboardStep.Permission -> PermissionStep(
                            resumeTick = resumeTick,
                            onAskNotification = onAskNotification,
                            onAskBattery = onAskBattery
                        )

                        OnboardStep.Done -> DoneStep(choice = choice)
                    }
                }
            }

            BottomBar(
                step = step,
                isFirst = stepIndex == 0,
                isLast = stepIndex == steps.lastIndex,
                canGoNext = step != OnboardStep.DataSource || choice != DataChoice.None,
                onBack = { stepIndex-- },
                onNext = ::goNext,
                onFinish = onFinish
            )
        }
    }

}

// ---------------------------------------------------------------- 顶部进度

@Composable
private fun StepHeader(
    steps: List<OnboardStep>,
    currentIndex: Int,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom
        ) {
            Text(
                text = steps[currentIndex].label,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "${currentIndex + 1} / ${steps.size}",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { (currentIndex + 1f) / steps.size },
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(CircleShape)
        )
    }
}

// ---------------------------------------------------------------- 底部按钮

@Composable
private fun BottomBar(
    step: OnboardStep,
    isFirst: Boolean,
    isLast: Boolean,
    canGoNext: Boolean,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onFinish: (Boolean) -> Unit
) {
    Column(modifier = Modifier.padding(vertical = 16.dp)) {
        if (step == OnboardStep.DataSource && !canGoNext) {
            Text(
                text = "请先选择一种数据来源",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp)
            )
        }

        if (isLast) {
            Button(
                onClick = { onFinish(true) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("去创建第一个课表") }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = { onFinish(false) },
                modifier = Modifier.fillMaxWidth()
            ) { Text("先看看首页") }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isFirst) {
                    TextButton(onClick = onBack) { Text("上一步") }
                } else {
                    Spacer(Modifier.width(1.dp))
                }
                Button(onClick = onNext, enabled = canGoNext) { Text("下一步") }
            }
        }
    }
}

// ---------------------------------------------------------------- 第 1 步

@Composable
private fun WelcomeStep() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Image(
            painter = painterResource(R.drawable.ic_app_logo),
            contentDescription = null,
            modifier = Modifier
                .size(96.dp)
                .clip(RoundedCornerShape(24.dp))
        )

        Spacer(Modifier.height(20.dp))

        BrandTitle(
            titleSize = 30.sp,
            subtitleSize = 14.sp,
            horizontalAlignment = Alignment.CenterHorizontally
        )

        Spacer(Modifier.height(32.dp))

        FeatureRow("单双周课表", "一套方案绑定到具体的星期，单双周自动切换")
        FeatureRow("上课倒计时", "首页实时显示当前是第几节、还剩多久下课")
        FeatureRow("作业与提醒", "作业按周排布，明日课表和天气可以定时推送")

        Spacer(Modifier.height(24.dp))

        Text(
            text = "只需要 4 步就能配置好",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun FeatureRow(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary)
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- 第 2 步

@Composable
private fun DataSourceStep(
    choice: DataChoice,
    onFresh: () -> Unit,
    onRestore: () -> Unit,
    onShareCodeDone: () -> Unit
) {
    var showShareCodeInput by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StepTitle("选择数据来源", "第一次使用选「全新开始」就好")

        DataOptionCard(
            icon = Icons.Default.CalendarMonth,
            title = "全新开始",
            desc = "从零开始配置你的课表",
            selected = choice == DataChoice.Fresh,
            onClick = onFresh
        )

        DataOptionCard(
            icon = Icons.Default.Restore,
            title = "从备份恢复",
            desc = "选择之前导出的备份文件夹，恢复全部配置",
            selected = choice == DataChoice.Backup,
            onClick = onRestore
        )

        DataOptionCard(
            icon = Icons.Default.QrCode,
            title = "输入分享码",
            desc = "同学生成的 6 位分享码，一键导入他的课表",
            selected = choice == DataChoice.ShareCode,
            onClick = { showShareCodeInput = true }
        )
    }

    if (showShareCodeInput) {
        ShareCodeDialog(
            onDismiss = { showShareCodeInput = false },
            onSuccess = {
                showShareCodeInput = false
                onShareCodeDone()
            }
        )
    }
}

@Composable
private fun ShareCodeDialog(onDismiss: () -> Unit, onSuccess: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }

    AppDialog(
        title = "输入分享码",
        summary = "从同学那里拿到的 6 位数字",
        onDismissRequest = onDismiss
    ) {
        AppTextField(
            value = code,
            onValueChange = {
                if (it.length <= 6 && it.all { c -> c.isDigit() }) {
                    code = it
                    error = ""
                }
            },
            label = "分享码",
            keyboardType = KeyboardType.Number,
            isError = error.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        )
        if (error.isNotBlank()) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 6.dp)
            )
        }

        Spacer(Modifier.height(12.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            TextButton(onClick = onDismiss, enabled = !loading) { Text("取消") }
            Spacer(Modifier.width(8.dp))
            FilledTonalButton(
                enabled = !loading && code.length == 6,
                onClick = {
                    loading = true
                    error = ""
                    scope.launch {
                        val msg = ShareCodeHelper.downloadAndImport(context, code)
                        loading = false
                        if (msg == "导入成功") {
                            Toast.makeText(context, "导入成功", Toast.LENGTH_SHORT).show()
                            onSuccess()
                        } else {
                            error = msg
                        }
                    }
                }
            ) { Text(if (loading) "导入中…" else "导入") }
        }
    }
}

@Composable
private fun DataOptionCard(
    icon: ImageVector,
    title: String,
    desc: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    val container = if (selected) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceContainerHighest
    }
    val onContainer = if (selected) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = container)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, tint = onContainer)
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = onContainer
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = onContainer.copy(alpha = 0.75f)
                )
            }
            if (selected) {
                Spacer(Modifier.width(8.dp))
                Icon(
                    Icons.Default.CheckCircle,
                    contentDescription = "已选择",
                    tint = onContainer
                )
            }
        }
    }
}

// ---------------------------------------------------------------- 第 3 步

@Composable
private fun SemesterStep(
    startDate: String,
    onPick: () -> Unit,
    onUseThisMonday: () -> Unit
) {
    val week = remember(startDate) {
        DateUtils.getWeekNumberFrom(startDate, System.currentTimeMillis())
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StepTitle("开学日期", "单周 / 双周是从这一天开始算的，填错了课表会整体错开一周")

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = startDate,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "按这个日期算，本周是第 $week 周（${if (week % 2 == 1) "单周" else "双周"}）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(onClick = onUseThisMonday, modifier = Modifier.weight(1f)) {
                Text("就用本周一")
            }
            FilledTonalButton(onClick = onPick, modifier = Modifier.weight(1f)) {
                Text("选择日期")
            }
        }

        Text(
            text = "以后在「设置 → 开学日期」里随时能改。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ---------------------------------------------------------------- 第 4 步

@Composable
private fun PermissionStep(
    resumeTick: Int,
    onAskNotification: () -> Unit,
    onAskBattery: () -> Unit
) {
    val context = LocalContext.current

    // 不再每秒轮询三个系统 API，改成回到本页时读一次
    val notifEnabled = remember(resumeTick) {
        NotificationManagerCompat.from(context).areNotificationsEnabled()
    }
    val batteryIgnored = remember(resumeTick) { context.isIgnoringBatteryOptimizations() }
    val canInstall = remember(resumeTick) { context.canRequestPackageInstalls() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        StepTitle("权限", "都可以跳过，但不给通知权限就收不到课表提醒")

        PermissionCard(
            title = "通知权限",
            desc = "发送课表提醒、作业提醒和常驻通知",
            granted = notifEnabled,
            onClick = onAskNotification
        )
        PermissionCard(
            title = "忽略电池优化",
            desc = "防止后台被系统杀掉，导致定时提醒不准",
            granted = batteryIgnored,
            onClick = onAskBattery
        )
        PermissionCard(
            title = "安装未知应用",
            desc = "应用内更新时需要，用不到自动更新可以不给",
            granted = canInstall,
            onClick = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    runCatching {
                        context.startActivity(
                            Intent(
                                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                                android.net.Uri.parse("package:${context.packageName}")
                            ).apply { addFlags(Intent.FLAG_ACTIVITY_NEW_TASK) }
                        )
                    }
                }
            }
        )

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp)
            ) {
                Text("自启动权限", style = MaterialTheme.typography.titleSmall)
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "国产系统一般还要手动开，路径通常是：\n" +
                        "手机管家 → 自启动管理，或 设置 → 应用 → 权限管理",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun PermissionCard(
    title: String,
    desc: String,
    granted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(title, style = MaterialTheme.typography.titleSmall)
                    if (granted) {
                        Spacer(Modifier.width(6.dp))
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "已授权",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    text = desc,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text(
                    text = "已开启",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                OutlinedButton(onClick = onClick) { Text("去开启") }
            }
        }
    }
}

// ---------------------------------------------------------------- 第 5 步

@Composable
private fun DoneStep(choice: DataChoice) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(24.dp))

        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(72.dp)
        )

        Spacer(Modifier.height(20.dp))

        Text(
            text = "配置完成",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(24.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            )
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                if (choice == DataChoice.Fresh) {
                    Text("接下来要做的", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    NumberedRow(1, "建一个「时间方案」", "把每节课的上下课时间填进去")
                    NumberedRow(2, "建一个「课程方案」", "填课程名，并勾选它在单/双周的哪几天生效")
                    NumberedRow(3, "回到首页", "就能看到今天的课和上课倒计时了")
                } else {
                    Text("已经导入了课表数据", style = MaterialTheme.typography.titleSmall)
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = "直接回首页就能看到今天的课。\n" +
                            "如果发现单双周对不上，去「设置 → 开学日期」调一下。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Text(
            text = "长按首页的课程名可以临时换课",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun NumberedRow(index: Int, title: String, desc: String) {
    Row(modifier = Modifier.padding(vertical = 6.dp)) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "$index",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onPrimary
            )
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                text = desc,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ---------------------------------------------------------------- 公共小组件 / 工具

@Composable
private fun StepTitle(title: String, desc: String) {
    Column {
        Text(text = title, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(
            text = desc,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
    }
}

private fun Context.isIgnoringBatteryOptimizations(): Boolean = runCatching {
    (getSystemService(Context.POWER_SERVICE) as PowerManager)
        .isIgnoringBatteryOptimizations(packageName)
}.getOrDefault(false)

private fun Context.canRequestPackageInstalls(): Boolean =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        runCatching { packageManager.canRequestPackageInstalls() }.getOrDefault(false)
    } else {
        true
    }

private val dateFormat get() = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

private fun thisMonday(): String {
    val cal = Calendar.getInstance().apply {
        firstDayOfWeek = Calendar.MONDAY
        set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }
    return dateFormat.format(cal.time)
}

/**
 * 首次进来时给一个合理的默认开学日期：
 * 用户从没设置过（还是出厂默认）就用本周一，否则沿用已有值。
 */
private fun smartDefaultStartDate(context: Context): String {
    val saved = Prefs.getStartDate(context)
    return if (saved == "2025-09-01") thisMonday() else saved
}

private fun pickDate(context: Context, current: String, onPicked: (String) -> Unit) {
    val cal = Calendar.getInstance()
    runCatching {
        val parts = current.split("-")
        cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
    }
    DatePickerDialog(
        ContextThemeWrapper(context, R.style.Theme_smartschedule),
        { _, y, m, d -> onPicked("%04d-%02d-%02d".format(y, m + 1, d)) },
        cal.get(Calendar.YEAR),
        cal.get(Calendar.MONTH),
        cal.get(Calendar.DAY_OF_MONTH)
    ).show()
}
