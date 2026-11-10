package com.enenhhh.smartschedule.ui.aiimport

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandLess
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.data.HomeworkRepository
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.CourseChip
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.components.courseColor
import com.enenhhh.smartschedule.ui.components.onCourseColor
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.util.AiImport
import com.enenhhh.smartschedule.util.AiImportParser
import com.enenhhh.smartschedule.util.AppClock
import com.enenhhh.smartschedule.util.DateUtils
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** 导入完成后弹「撤销」的提示条要比这个页面活得久（页面会立刻返回），所以用进程级的作用域 */
private val undoScope = MainScope()

/**
 * AI 快速导入：复制提示词 → 发给 AI 助手 → 复制回复 → 粘贴确认。
 * [mode] 为 "timetable" 或 "homework"，决定默认打开哪一种；粘贴的内容会自动识别类型。
 */
@Composable
fun AiImportScreen(mode: String) {
    val context = LocalContext.current
    val manager = LocalScheduleManager.current
    val nav = LocalNavigator.current
    val snackbar = LocalSnackbar.current
    val repo = remember { HomeworkRepository(context) }
    val today = AppClock.today
    val version = ScheduleManager.version

    var isTimetable by rememberSaveable { mutableStateOf(mode != "homework") }
    var promptCopied by rememberSaveable { mutableStateOf(false) }
    var showPrompt by remember { mutableStateOf(false) }
    var pasted by rememberSaveable { mutableStateOf("") }
    var autoFilled by remember { mutableStateOf(false) }

    val prompt = remember(isTimetable, version, today) {
        if (isTimetable) AiImport.timetablePrompt(manager) else AiImport.homeworkPrompt(manager, today)
    }
    val parsed: Result<AiImportParser.Parsed>? = remember(pasted, version, today) {
        if (pasted.isBlank()) null else runCatching { AiImport.parse(pasted, manager, today) }
    }
    // 粘贴的是另一种内容时，自动切过去
    LaunchedEffect(parsed) {
        when (parsed?.getOrNull()) {
            is AiImportParser.Parsed.Timetable -> isTimetable = true
            is AiImportParser.Parsed.Homework -> isTimetable = false
            null -> {}
        }
    }

    // 从 AI 助手切回来时（窗口重新获得焦点），如果剪贴板里正好是 AI 的回复，自动填进来。
    // 只在本页复制过提示词之后才读，避免无故读取剪贴板。
    val focused = LocalWindowInfo.current.isWindowFocused
    LaunchedEffect(focused) {
        if (!focused || !promptCopied || pasted.isNotBlank()) return@LaunchedEffect
        delay(250)
        val clip = AiImport.readClipboard(context) ?: return@LaunchedEffect
        if (clip == prompt) return@LaunchedEffect
        if (runCatching { AiImport.parse(clip, manager, today) }.isSuccess) { pasted = clip; autoFilled = true }
    }

    SubPage(title = "AI 快速导入", subtitle = "史上最简单的配置方法") { padding ->
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            PillTabs(
                options = listOf(true, false),
                selected = isTimetable,
                onSelect = { isTimetable = it; promptCopied = false },
                label = { if (it) "导入课表" else "导入作业" },
                modifier = Modifier.fillMaxWidth()
            )

            Column(Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
                AiGuideAnimation(timetable = isTimetable)
                Spacer(Modifier.height(12.dp))
                Text(
                    if (isTimetable) "拍一张课表，交给 DeepSeek 这类 AI 助手识别，复制回来粘贴，整张课表就填好了。"
                    else "拍黑板、截作业群，交给 AI 助手整理，复制回来粘贴，作业就一条条加好了。",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "不用申请 API、不用填密钥：下面的提示词就是规则，AI 照着它输出，App 照着它读取。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }

            // ---------- 1 复制提示词
            StepCard(1, "复制提示词", done = promptCopied) {
                Text(
                    if (isTimetable) "提示词里带着你现有的课程名和作息节数，AI 会尽量沿用你的写法。"
                    else "提示词里带着今天的日期（${DateUtils.prettyDate(today)}），AI 会把「明天」「周五前」换算成具体日期。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        AiImport.copy(context, "智能课表提示词", prompt)
                        promptCopied = true
                        undoScope.launch { snackbar.showSnackbar("提示词已复制") }
                    }) {
                        Icon(if (promptCopied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (promptCopied) "已复制，可再次复制" else "复制提示词")
                    }
                    Spacer(Modifier.width(4.dp))
                    TextButton(onClick = { showPrompt = !showPrompt }) {
                        Text(if (showPrompt) "收起" else "看看内容")
                        Icon(if (showPrompt) Icons.Rounded.ExpandLess else Icons.Rounded.ExpandMore, null, modifier = Modifier.size(18.dp))
                    }
                }
                AnimatedVisibility(showPrompt) {
                    Text(
                        prompt,
                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(14.dp))
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    )
                }
            }

            // ---------- 2 发给 AI
            StepCard(2, if (isTimetable) "打开 AI 助手，发照片和提示词" else "打开 AI 助手，发作业和提示词") {
                Text(
                    if (isTimetable) "先上传课表的照片或截图，再把提示词粘贴进输入框，一起发送。能识图的 AI 助手都可以。"
                    else "先上传黑板照片、作业群截图，或者直接粘贴作业文字，再把提示词粘贴进输入框，一起发送。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiImport.ASSISTANTS.forEach { a ->
                        val installed = remember(a) { AiImport.isInstalled(context, a) }
                        FilledTonalButton(onClick = {
                            if (!AiImport.open(context, a)) undoScope.launch { snackbar.showSnackbar("打不开 ${a.name}") }
                        }) {
                            Text(a.name)
                            if (!installed) {
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.AutoMirrored.Rounded.OpenInNew, contentDescription = "网页版", modifier = Modifier.size(15.dp))
                            }
                        }
                    }
                }
                Text(
                    "带箭头的表示手机上没装，会打开网页版。",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }

            // ---------- 3 复制回复
            StepCard(3, "复制 AI 的回复") {
                Text(
                    "AI 会回复一段代码。点代码块右上角的「复制」，或者点回复下面的复制按钮，复制整段回复也可以。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // ---------- 4 粘贴确认
            StepCard(4, "粘贴到这里，确认导入", done = parsed?.isSuccess == true) {
                OutlinedTextField(
                    value = pasted,
                    onValueChange = { pasted = it; autoFilled = false },
                    placeholder = { Text("粘贴 AI 的回复") },
                    textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace, fontSize = 12.sp),
                    minLines = 3,
                    maxLines = 6,
                    isError = parsed?.isFailure == true,
                    shape = MaterialTheme.shapes.medium,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = {
                        val clip = AiImport.readClipboard(context)
                        if (clip == null) undoScope.launch { snackbar.showSnackbar("剪贴板是空的") }
                        else if (clip == prompt) undoScope.launch { snackbar.showSnackbar("剪贴板里还是提示词，请先复制 AI 的回复") }
                        else { pasted = clip; autoFilled = false }
                    }) {
                        Icon(Icons.Rounded.ContentPaste, null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("从剪贴板粘贴")
                    }
                    if (pasted.isNotBlank()) {
                        Spacer(Modifier.width(4.dp))
                        TextButton(onClick = { pasted = ""; autoFilled = false }) { Text("清空") }
                    }
                }
                if (autoFilled) Hint(Icons.Rounded.Info, "已自动读取剪贴板里 AI 的回复。")
                parsed?.exceptionOrNull()?.let { e ->
                    Hint(
                        Icons.Rounded.ErrorOutline,
                        (e.message ?: "无法识别") + "\n也可以把这句话发给 AI，让它重新按提示词输出。",
                        error = true
                    )
                }
            }

            when (val r = parsed?.getOrNull()) {
                is AiImportParser.Parsed.Timetable -> TimetablePreview(r.result, onDone = { msg, undo ->
                    nav.pop()
                    undoScope.launch {
                        val res = snackbar.showSnackbar(msg, actionLabel = "撤销", duration = SnackbarDuration.Long)
                        if (res == SnackbarResult.ActionPerformed) { undo.run(); snackbar.showSnackbar("已撤销") }
                    }
                })
                is AiImportParser.Parsed.Homework -> HomeworkPreview(r.result, repo, onDone = { msg, undo ->
                    nav.pop()
                    undoScope.launch {
                        val res = snackbar.showSnackbar(msg, actionLabel = "撤销", duration = SnackbarDuration.Long)
                        if (res == SnackbarResult.ActionPerformed) { undo.run(); snackbar.showSnackbar("已撤销") }
                    }
                })
                null -> {}
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

// ------------------------------------------------------------------ 步骤卡片

@Composable
private fun StepCard(n: Int, title: String, done: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().glassCard().animateContentSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(28.dp).background(if (done) cs.primary else cs.primary.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
                if (done) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(17.dp))
                else Text("$n", color = cs.primary, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelLarge)
            }
            Spacer(Modifier.width(12.dp))
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        }
        Column(Modifier.padding(start = 40.dp, top = 6.dp), content = content)
    }
}

@Composable
private fun Hint(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, error: Boolean = false) {
    val c = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
    Row(Modifier.padding(top = 8.dp)) {
        Icon(icon, null, tint = c, modifier = Modifier.size(16.dp).padding(top = 1.dp))
        Spacer(Modifier.width(6.dp))
        Text(text, style = MaterialTheme.typography.bodySmall, color = c)
    }
}

@Composable
private fun AiNotes(notes: List<String>) {
    if (notes.isEmpty()) return
    Column(
        Modifier.padding(top = 10.dp).fillMaxWidth()
            .background(MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.6f), RoundedCornerShape(14.dp)).padding(12.dp)
    ) {
        Text("AI 的备注", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
        notes.forEach { Text("· $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onTertiaryContainer) }
    }
}

// ------------------------------------------------------------------ 课表预览

@Composable
private fun TimetablePreview(r: AiImportParser.TimetableResult, onDone: (String, AiImport.Undo) -> Unit) {
    val manager = LocalScheduleManager.current
    val version = ScheduleManager.version
    val active = remember(version) { manager.getActiveTimetable() }
    val plan = remember(version, active) { manager.resolveTimePlan(active) }
    var updateActive by remember(active?.id) { mutableStateOf(active != null) }
    var withTime by remember { mutableStateOf(r.periods.isNotEmpty() && (plan == null || plan.periods != r.periods)) }
    var showOdd by remember { mutableStateOf(true) }
    val cs = MaterialTheme.colorScheme

    Column(Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Text("识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text(
            buildString {
                append("${r.days.size} 天 · ${r.courseCount} 节课")
                if (r.alternatingDays > 0) append(" · ${r.alternatingDays} 天单双周不同")
                if (r.periods.isNotEmpty()) append(" · 含作息时间")
            },
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant
        )
        if (r.alternatingDays > 0) {
            Spacer(Modifier.height(8.dp))
            PillTabs(listOf(true, false), showOdd, { showOdd = it }, { if (it) "单周" else "双周" }, Modifier.fillMaxWidth())
        }
        Spacer(Modifier.height(10.dp))
        MiniGrid(r, showOdd, shortName = { manager.getShortName(it) })

        val planPeriods = if (withTime && r.periods.isNotEmpty()) r.periods.size else plan?.periods?.size ?: 0
        if (planPeriods in 1 until r.maxPeriods) {
            Hint(Icons.Rounded.Info, "课表里每天最多 ${r.maxPeriods} 节，但时间方案只有 $planPeriods 节，多出来的节次不会显示。导入后可以在「时间方案」里加节。")
        }
        AiNotes(r.notes)

        Spacer(Modifier.height(14.dp))
        if (active != null) {
            Text("导入到", style = MaterialTheme.typography.labelLarge)
            Spacer(Modifier.height(6.dp))
            PillTabs(listOf(true, false), updateActive, { updateActive = it }, { if (it) "更新「${active.name.take(8)}」" else "新建一张课表" }, Modifier.fillMaxWidth())
            Text(
                if (updateActive) "只替换识别到的那几天（${r.days.keys.joinToString("、") { DateUtils.dayName(it) }}），其它天不变。"
                else "新建「${r.name ?: "我的课表"}」并设为使用中，原来的课表保留。",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
        if (r.periods.isNotEmpty()) {
            Row(Modifier.padding(top = 10.dp).fillMaxWidth().clickable { withTime = !withTime }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("同时导入作息时间", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "${r.periods.size} 节，${r.periods.first().start} – ${r.periods.last().end}" +
                            (if (plan != null) "（现在：${plan.periods.size} 节，${plan.periods.firstOrNull()?.start ?: "-"} 开始）" else ""),
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant
                    )
                }
                Switch(checked = withTime, onCheckedChange = { withTime = it })
            }
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val target = if (updateActive) active else null
                val (tt, undo) = AiImport.applyTimetable(manager, r, target, r.name ?: "我的课表", withTime)
                onDone("已导入 ${r.courseCount} 节课到「${tt.name}」", undo)
            },
            enabled = !r.isEmpty,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("确认导入")
        }
    }
}

@Composable
private fun MiniGrid(r: AiImportParser.TimetableResult, odd: Boolean, shortName: (String) -> String) {
    val days = (1..7).filter { r.days.containsKey(it) || it <= 5 }
    val rows = r.maxPeriods.coerceAtLeast(1)
    val cs = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            Spacer(Modifier.width(22.dp))
            days.forEach { d ->
                Text(DateUtils.dayName(d, ""), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.width(44.dp), maxLines = 1)
            }
        }
        val anyMorning = r.days.values.any { it.morningReadFor(odd).isNotBlank() }
        if (anyMorning) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("晨", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.width(22.dp))
                days.forEach { d -> GridCell(r.days[d]?.morningReadFor(odd).orEmpty(), shortName, 22.dp) }
            }
        }
        for (p in 0 until rows) {
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("${p + 1}", style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, modifier = Modifier.width(22.dp))
                days.forEach { d -> GridCell(r.days[d]?.courseAt(odd, p).orEmpty(), shortName, 30.dp) }
            }
        }
    }
}

@Composable
private fun GridCell(name: String, shortName: (String) -> String, h: androidx.compose.ui.unit.Dp) {
    val bg = if (name.isBlank()) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else courseColor(name)
    Box(Modifier.size(44.dp, h).background(bg, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
        if (name.isNotBlank()) Text(shortName(name), fontSize = 12.sp, color = onCourseColor(bg), maxLines = 1, overflow = TextOverflow.Clip)
    }
}

// ------------------------------------------------------------------ 作业预览

@Composable
private fun HomeworkPreview(r: AiImportParser.HomeworkResult, repo: HomeworkRepository, onDone: (String, AiImport.Undo) -> Unit) {
    val context = LocalContext.current
    val existing = remember(HomeworkRepository.version) { repo.getAll() }
    val selected = remember(r) { mutableStateListOf<Boolean>().apply { addAll(r.items.map { !AiImport.isDuplicate(existing, it) }) } }
    val today = AppClock.today
    val cs = MaterialTheme.colorScheme
    val count = selected.count { it }

    Column(Modifier.fillMaxWidth().glassCard().padding(16.dp)) {
        Text("识别结果", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
        Text("${r.items.size} 项作业 · 取消勾选的不会导入", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        r.items.forEachIndexed { i, hw ->
            val dup = AiImport.isDuplicate(existing, hw)
            Row(
                Modifier.fillMaxWidth().clickable { selected[i] = !selected[i] }.padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = selected[i], onCheckedChange = { selected[i] = it })
                Column(Modifier.weight(1f)) {
                    Text(hw.title, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(Modifier.padding(top = 3.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (hw.subject.isNotBlank()) CourseChip(text = hw.subject)
                        val days = java.time.temporal.ChronoUnit.DAYS.between(today, hw.deadline).toInt()
                        Text(
                            "截止 ${hw.deadline.monthValue}/${hw.deadline.dayOfMonth} ${DateUtils.dayName(DateUtils.dayIndex(hw.deadline))}" +
                                when { days < 0 -> "（已过）"; days == 0 -> "（今天）"; days == 1 -> "（明天）"; else -> "" } +
                                (if (hw.deadlineGuessed) " · 未写截止，按下一个上学日" else ""),
                            style = MaterialTheme.typography.labelMedium,
                            color = if (days < 0) cs.error else cs.onSurfaceVariant
                        )
                    }
                    if (hw.note.isNotBlank()) Text(hw.note, style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                    if (dup) Text("已经有一样的作业了", style = MaterialTheme.typography.labelSmall, color = cs.tertiary)
                }
            }
        }
        AiNotes(r.notes)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                val items = r.items.filterIndexed { i, _ -> selected[i] }
                val undo = AiImport.applyHomework(context, items, today)
                onDone("已添加 ${items.size} 项作业", undo)
            },
            enabled = count > 0,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Rounded.AutoAwesome, null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(if (count > 0) "添加 $count 项作业" else "没有勾选作业")
        }
    }
}
