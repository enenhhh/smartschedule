package com.enenhhh.smartschedule.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.ui.components.DatePickerDialogM3
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SettingSwitchRow
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.glass.glassAccentCard
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import java.time.LocalDate

/**
 * 学期与周次。两种设置方式等价：
 *  - 直接选开学日期（会自动对齐到那一周的周一）
 *  - 或者告诉 App「今天是第几周」，反推开学日期
 */
@Composable
fun SemesterScreen() {
    val context = LocalContext.current
    var startDate by remember { mutableStateOf(Prefs.getStartDate(context)) }
    var showWeekend by remember { mutableStateOf(Prefs.isShowWeekend(context)) }
    var picker by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val week = DateUtils.weekNumber(startDate, today)
    val odd = DateUtils.isOddWeek(week)

    fun apply(d: LocalDate) {
        val monday = DateUtils.mondayOf(d)
        startDate = monday
        Prefs.setStartDate(context, monday)
        ScheduleManager.invalidateCache()
    }

    SubPage(title = "学期与周次", subtitle = "决定单双周怎么算") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(Modifier.fillMaxWidth().glassAccentCard().padding(18.dp)) {
                Text(
                    if (week >= 1) "今天是第 $week 周 · ${if (odd) "单周" else "双周"}" else "还没开学（${-week + 1} 周后开学）",
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "开学日期：${DateUtils.prettyDate(startDate)}（周一）",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                )
                Text(
                    "单双周按「从开学那周算起的第几周」判断：第 1、3、5… 周为单周。周日属于当周，所以周日晚上看「明天」会自动切到下一周的单双周。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.75f),
                    modifier = Modifier.padding(top = 8.dp)
                )
            }

            SectionTitle("方式一：直接选开学日期")
            GlassGroup {
                SettingRow(
                    title = "开学日期",
                    subtitle = DateUtils.format(startDate) + "，选任意一天都会自动对齐到周一",
                    icon = Icons.Rounded.CalendarMonth,
                    onClick = { picker = true }
                )
            }

            SectionTitle("方式二：告诉我现在是第几周")
            GlassGroup {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("本周是第几周", style = MaterialTheme.typography.bodyLarge)
                        Text("加减后开学日期会自动反推", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    FilledTonalIconButton(onClick = { if (week > 1) apply(DateUtils.startDateForCurrentWeek(week - 1, today)) }, enabled = week > 1) {
                        Icon(Icons.Rounded.Remove, contentDescription = "减一周")
                    }
                    Text(
                        "${week.coerceAtLeast(1)}",
                        style = MaterialTheme.typography.headlineSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 14.dp)
                    )
                    FilledTonalIconButton(onClick = { apply(DateUtils.startDateForCurrentWeek(week.coerceAtLeast(1) + 1, today)) }, enabled = week < 60) {
                        Icon(Icons.Rounded.Add, contentDescription = "加一周")
                    }
                }
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("本周是", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    PillTabs(
                        options = listOf(true, false),
                        selected = odd,
                        onSelect = { wantOdd ->
                            if (wantOdd != odd) {
                                // 保持「第几周」的感觉不变，只把开学日期挪一周来翻转奇偶
                                val target = if (week > 1) week - 1 else week + 1
                                apply(DateUtils.startDateForCurrentWeek(target, today))
                            }
                        },
                        label = { if (it) "单周" else "双周" },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            SectionTitle("显示")
            GlassGroup {
                SettingSwitchRow(
                    title = "课表页显示周末",
                    subtitle = "周六、周日有课时会自动显示",
                    checked = showWeekend,
                    onCheckedChange = { showWeekend = it; Prefs.setShowWeekend(context, it) }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (picker) {
        DatePickerDialogM3(initial = startDate, onDismiss = { picker = false }, onConfirm = { apply(it) })
    }
}
