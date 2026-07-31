package com.enenhhh.smartschedule.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.PlanListActivity
import com.enenhhh.smartschedule.data.Homework
import com.enenhhh.smartschedule.data.repository.AppDataRepository
import com.enenhhh.smartschedule.models.SchedulePlan
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

/** 每天只需要读一次的「静态」数据：当天/明天的方案、课程名、作业 */
private data class HomeDayData(
    val todayDate: String = "",
    val plan: SchedulePlan? = null,
    val timePlan: TimePlan? = null,
    val courseNames: List<String> = emptyList(),
    val shortNames: List<String> = emptyList(),
    val tomorrowCourses: String = "",
    val homework: List<Homework> = emptyList(),
    val allCourseOptions: List<String> = emptyList()
)

/** 每秒都会变的「动态」数据 */
private data class HomeTickState(
    val currentIndex: Int = -1,
    val isInClass: Boolean = false,
    val statusText: String = "",
    val countdownText: String = "",
    val progress: Float = 0f
)

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenSchedule: () -> Unit = {},
    onOpenHomework: () -> Unit = {}
) {
    val context = LocalContext.current
    val repository = remember { AppDataRepository(context) }

    // dataVersion 递增即触发一次磁盘重读（换课 / 回到前台跨天）
    var dataVersion by remember { mutableStateOf(0) }
    var day by remember { mutableStateOf(HomeDayData()) }
    var tick by remember { mutableStateOf(HomeTickState()) }
    var swapTarget by remember { mutableStateOf<Int?>(null) }

    LaunchedEffect(dataVersion) {
        repository.clearExpiredSwaps()
        day = loadDayData(context, repository)
    }

    // 只有倒计时每秒刷新，方案/作业不再每秒读盘
    LaunchedEffect(day) {
        while (true) {
            tick = computeTick(day)
            delay(1000)
            // 跨天了就重新加载一次当天数据
            if (day.todayDate.isNotEmpty() && day.todayDate != todayDate()) {
                dataVersion++
                break
            }
        }
    }

    Column(
        modifier = modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // 今日课表 + 进度卡片
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("今日课表", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(12.dp))

                if (day.courseNames.none { it.isNotBlank() && it != SEPARATOR }) {
                    Text(
                        text = day.statusOrDefault(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    // 课程横排：已过灰色，当前高亮，未来普通，"|" 作为分隔符
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        day.courseNames.forEachIndexed { index, name ->
                            if (name == SEPARATOR) {
                                Text(
                                    text = "｜",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                    fontSize = 16.sp,
                                    modifier = Modifier.padding(vertical = 6.dp)
                                )
                            } else {
                                val isCurrent = index == tick.currentIndex
                                val color = when {
                                    index < tick.currentIndex -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                                    isCurrent -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.onSurface
                                }
                                val realPeriodIndex = day.realPeriodIndexOf(index)

                                Box(
                                    modifier = (if (realPeriodIndex >= 0) {
                                        Modifier.combinedClickable(
                                            onClick = {},
                                            onLongClick = { swapTarget = realPeriodIndex }
                                        )
                                    } else Modifier)
                                        .padding(horizontal = 2.dp, vertical = 6.dp)
                                        .widthIn(min = 28.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = day.shortNames.getOrElse(index) { name },
                                        color = color,
                                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = if (isCurrent) 20.sp else 16.sp
                                    )
                                }
                            }
                        }
                    }

                    Text(
                        text = "长按课程可快速换课",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(top = 4.dp)
                    )

                    Spacer(Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = tick.statusText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (tick.isInClass) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            }
                        )
                        Text(
                            text = tick.countdownText,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { tick.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        // 明日课程
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Text("明日课程", style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(8.dp))
                Text(
                    text = day.tomorrowCourses.ifBlank { "明天暂无课" },
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // 待办作业
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(20.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("待办作业", style = MaterialTheme.typography.titleMedium)
                    TextButton(onClick = onOpenHomework) { Text("查看全部") }
                }
                Spacer(Modifier.height(8.dp))
                if (day.homework.isEmpty()) {
                    Text(
                        text = "暂无待办作业",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    day.homework.forEach { hw ->
                        val urgent = hw.getDaysRemaining() <= 1
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "${hw.subject}：${hw.title}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "剩${hw.getDaysRemaining()}天",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (urgent) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(onClick = onOpenSchedule, modifier = Modifier.weight(1f)) { Text("课表") }
            OutlinedButton(
                onClick = { context.startActivity(Intent(context, PlanListActivity::class.java)) },
                modifier = Modifier.weight(1f)
            ) { Text("方案") }
        }

        Spacer(Modifier.height(16.dp))
    }

    // 换课弹窗
    swapTarget?.let { periodIdx ->
        AppDialog(
            title = "临时换课",
            summary = "第${periodIdx + 1}节 · 仅今天生效",
            onDismissRequest = { swapTarget = null }
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                day.allCourseOptions.forEach { course ->
                    OutlinedButton(
                        onClick = {
                            repository.addTempSwap(day.todayDate, periodIdx, course)
                            swapTarget = null
                            dataVersion++
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text(course) }
                }

                Spacer(Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    TextButton(onClick = {
                        repository.removeTempSwap(day.todayDate, periodIdx)
                        swapTarget = null
                        dataVersion++
                    }) { Text("恢复原课") }
                    Spacer(Modifier.width(8.dp))
                    TextButton(onClick = { swapTarget = null }) { Text("取消") }
                }
            }
        }
    }
}

private const val SEPARATOR = "|"

private fun HomeDayData.statusOrDefault(): String =
    if (plan == null) "今天尚未绑定方案" else "今天没有课，休息一下吧"

/** courseNames 里的下标 → 真实节次下标（早读和分隔符不参与换课） */
private fun HomeDayData.realPeriodIndexOf(index: Int): Int {
    val separatorIndex = courseNames.indexOf(SEPARATOR)
    if (separatorIndex < 0) return index
    return if (index > separatorIndex) index - separatorIndex - 1 else -1
}

private fun loadDayData(context: Context, repository: AppDataRepository): HomeDayData {
    val today = todayDate()
    val dayIndex = dayIndex(todayLabel())
    val startDate = Prefs.getStartDate(context)
    val isOdd = DateUtils.getWeekNumberFrom(startDate, System.currentTimeMillis()) % 2 == 1
    val activePlan = repository.getActiveSchedule(isOdd, dayIndex)

    val allCourses = repository.getSchedulePlans()
        .flatMap { it.courses }
        .filter { it.isNotBlank() }
        .distinct()
        .sorted()

    // 明日
    val tomorrowCal = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val nextDayIndex = when (tomorrowCal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 1; Calendar.TUESDAY -> 2; Calendar.WEDNESDAY -> 3
        Calendar.THURSDAY -> 4; Calendar.FRIDAY -> 5; Calendar.SATURDAY -> 6
        else -> 7
    }
    val nextDayIsOdd = DateUtils.getWeekNumberFrom(startDate, tomorrowCal.timeInMillis) % 2 == 1
    val tomorrowPlan = repository.getActiveSchedule(nextDayIsOdd, nextDayIndex)
    val tomorrowCourses = tomorrowPlan?.let { p ->
        val morningPart = if (p.isMorningReadEnabled && p.morningRead.isNotBlank()) {
            repository.getShortName(p.morningRead) + " ｜ "
        } else ""
        morningPart + p.courses.filter { it.isNotBlank() }.joinToString(" ") { repository.getShortName(it) }
    }.orEmpty()

    val homework = repository.getPendingHomework().take(3)

    if (activePlan == null) {
        return HomeDayData(
            todayDate = today,
            tomorrowCourses = tomorrowCourses,
            homework = homework,
            allCourseOptions = allCourses
        )
    }

    val rawCourses = repository.getCoursesWithSwaps(activePlan, today)
    val courseNames = if (activePlan.isMorningReadEnabled && activePlan.morningRead.isNotBlank()) {
        listOf(activePlan.morningRead, SEPARATOR) + rawCourses
    } else {
        rawCourses
    }

    return HomeDayData(
        todayDate = today,
        plan = activePlan,
        timePlan = repository.getTimePlans().find { it.name == activePlan.timePlanName },
        courseNames = courseNames,
        shortNames = courseNames.map { if (it == SEPARATOR) it else repository.getShortName(it).take(2) },
        tomorrowCourses = tomorrowCourses,
        homework = homework,
        allCourseOptions = allCourses
    )
}

private fun computeTick(day: HomeDayData): HomeTickState {
    val plan = day.plan ?: return HomeTickState(statusText = "今天尚未绑定方案")
    val timePlan = day.timePlan ?: return HomeTickState(statusText = "时间方案已丢失")

    val now = Calendar.getInstance()
    val currentMinutes = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)
    val currentSec = now.get(Calendar.SECOND)

    val hasMorning = plan.isMorningReadEnabled && plan.morningRead.isNotBlank()
    // 有早读时 courseNames 是 [早读, "|", 课1, ...]，正式课程整体后移 2
    val morningOffset = if (hasMorning) 2 else 0

    fun countdown(totalSeconds: Int): String {
        val s = totalSeconds.coerceAtLeast(0)
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%02d:%02d".format(m, sec)
    }

    if (hasMorning) {
        val start = timeToMinutes(timePlan.morningStart)
        val end = timeToMinutes(timePlan.morningEnd)
        if (currentMinutes in start until end) {
            return HomeTickState(
                currentIndex = 0,
                isInClass = true,
                statusText = "早读中",
                countdownText = countdown((end - currentMinutes) * 60 - currentSec),
                progress = if (end > start) (currentMinutes - start).toFloat() / (end - start) else 0f
            )
        }
        if (currentMinutes < start) {
            return HomeTickState(
                currentIndex = 0,
                statusText = "即将早读",
                countdownText = countdown((start - currentMinutes) * 60 - currentSec)
            )
        }
    }

    timePlan.periods.forEachIndexed { index, period ->
        val start = timeToMinutes(period.start)
        val end = timeToMinutes(period.end)

        if (currentMinutes in start until end) {
            return HomeTickState(
                currentIndex = index + morningOffset,
                isInClass = true,
                statusText = "上课中",
                countdownText = countdown((end - currentMinutes) * 60 - currentSec),
                progress = if (end > start) (currentMinutes - start).toFloat() / (end - start) else 0f
            )
        }

        if (currentMinutes < start) {
            val prevEnd = if (index > 0) {
                timeToMinutes(timePlan.periods[index - 1].end)
            } else if (hasMorning) {
                timeToMinutes(timePlan.morningEnd)
            } else {
                -1
            }

            if (prevEnd >= 0 && currentMinutes >= prevEnd) {
                val breakDuration = start - prevEnd
                return HomeTickState(
                    currentIndex = index + morningOffset,
                    statusText = if (breakDuration > 30) "休息中" else "课间",
                    countdownText = countdown((start - currentMinutes) * 60 - currentSec),
                    progress = if (breakDuration > 0) {
                        (currentMinutes - prevEnd).toFloat() / breakDuration
                    } else 0f
                )
            }
            if (index == 0 && prevEnd == -1) {
                return HomeTickState(
                    currentIndex = morningOffset,
                    statusText = "等待上课",
                    countdownText = countdown((start - currentMinutes) * 60 - currentSec)
                )
            }
        }
    }

    // 所有课都结束了
    val lastEnd = timePlan.periods.lastOrNull()?.let { timeToMinutes(it.end) } ?: 0
    if (currentMinutes >= lastEnd && day.courseNames.any { it.isNotBlank() }) {
        return HomeTickState(
            currentIndex = day.courseNames.size,
            statusText = "今天的课都结束啦",
            progress = 1f
        )
    }
    return HomeTickState()
}

private fun timeToMinutes(time: String): Int = runCatching {
    val parts = time.split(":")
    parts[0].toInt() * 60 + parts[1].toInt()
}.getOrDefault(0)

private fun todayDate(): String =
    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date())

private fun todayLabel(): String = when (Calendar.getInstance().get(Calendar.DAY_OF_WEEK)) {
    Calendar.MONDAY -> "周一"
    Calendar.TUESDAY -> "周二"
    Calendar.WEDNESDAY -> "周三"
    Calendar.THURSDAY -> "周四"
    Calendar.FRIDAY -> "周五"
    Calendar.SATURDAY -> "周六"
    else -> "周日"
}

private fun dayIndex(label: String): Int = when (label) {
    "周一" -> 1
    "周二" -> 2
    "周三" -> 3
    "周四" -> 4
    "周五" -> 5
    "周六" -> 6
    "周日" -> 7
    else -> 1
}
