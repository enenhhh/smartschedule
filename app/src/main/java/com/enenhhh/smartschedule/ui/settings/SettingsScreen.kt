package com.enenhhh.smartschedule.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Policy
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material.icons.rounded.VerifiedUser
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.ShortText
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.ScheduleManager
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import com.enenhhh.smartschedule.ui.app.LocalScheduleManager
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.glass.glassAccentCard
import com.enenhhh.smartschedule.ui.nav.Route
import com.enenhhh.smartschedule.ui.theme.AccentPresets
import com.enenhhh.smartschedule.ui.theme.ThemeState
import com.enenhhh.smartschedule.ui.today.topLevelContentPadding
import com.enenhhh.smartschedule.util.AppClock
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs
import java.time.LocalDate

const val APP_VERSION_NAME = "5.2"

@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val nav = LocalNavigator.current
    val manager = LocalScheduleManager.current
    val version = ScheduleManager.version
    val timetable = remember(version) { manager.getActiveTimetable() }
    val timetableCount = remember(version) { manager.getTimetables().size }
    val planCount = remember(version) { manager.getTimePlans().size }
    val startSet = Prefs.isStartDateSet(context)
    val week = DateUtils.weekNumber(Prefs.getStartDate(context), AppClock.today)

    LazyColumn(contentPadding = topLevelContentPadding(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Text("设置", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp))
        }
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .glassAccentCard()
                    .clickable { nav.push(Route.Semester) }
                    .padding(18.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        timetable?.name ?: "还没有课表",
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        if (startSet && week >= 1) "本周第 $week 周 · ${if (DateUtils.isOddWeek(week)) "单周" else "双周"} · 开学 ${DateUtils.prettyDate(Prefs.getStartDate(context))}"
                        else "点这里设置开学日期，单双周才会准确",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f)
                    )
                }
                Spacer(Modifier.width(8.dp))
                Column(horizontalAlignment = Alignment.End) {
                    Text("$timetableCount", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text("张课表", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f))
                }
            }
        }

        item { SectionTitle("课程") }
        item {
            GlassGroup {
                SettingRow("课表管理", subtitle = "$timetableCount 张课表 · 新建、切换、复制", icon = Icons.Rounded.CalendarViewWeek, onClick = { nav.push(Route.Timetables) })
                RowDivider()
                SettingRow("时间方案", subtitle = "$planCount 个方案 · 每天几节、几点上课", icon = Icons.Rounded.Schedule, onClick = { nav.push(Route.TimePlans) })
                RowDivider()
                SettingRow("学期与周次", subtitle = if (startSet) "开学 ${DateUtils.format(Prefs.getStartDate(context))}" else "未设置开学日期", icon = Icons.Rounded.CalendarMonth, onClick = { nav.push(Route.Semester) })
                RowDivider()
                SettingRow("课程简称", subtitle = "课表格子里显示的短名字", icon = Icons.Rounded.ShortText, onClick = { nav.push(Route.ShortNames) })
            }
        }

        item { SectionTitle("个性化") }
        item {
            GlassGroup {
                SettingRow(
                    "外观与主题",
                    subtitle = buildString {
                        append(if (ThemeState.dynamicColor) "动态取色" else AccentPresets.firstOrNull { it.key == ThemeState.seedKey }?.label ?: "自定义色")
                        append(" · ")
                        append(if (ThemeState.glassEnabled) "玻璃 ${(ThemeState.glassIntensity * 100).toInt()}%" else "玻璃已关闭")
                    },
                    icon = Icons.Rounded.Palette,
                    onClick = { nav.push(Route.Appearance) }
                )
                RowDivider()
                SettingRow(
                    "提醒与通知",
                    subtitle = buildString {
                        val parts = mutableListOf<String>()
                        if (Prefs.isTomorrowReminderEnabled(context)) parts += "明日课程"
                        if (Prefs.isWeatherReminderEnabled(context)) parts += "天气"
                        if (Prefs.isPersistentEnabled(context)) parts += "常驻通知"
                        append(if (parts.isEmpty()) "全部关闭" else parts.joinToString(" · "))
                    },
                    icon = Icons.Rounded.Notifications,
                    onClick = { nav.push(Route.Reminders) }
                )
            }
        }

        item { SectionTitle("数据") }
        item {
            GlassGroup {
                SettingRow("备份、恢复与分享码", subtitle = "导出备份文件，或用 6 位分享码传给同学", icon = Icons.Rounded.CloudSync, onClick = { nav.push(Route.DataManagement) })
            }
        }

        item { SectionTitle("条款与权限") }
        item {
            GlassGroup {
                SettingRow("阅读免责条款", subtitle = "应用边界、提醒与备份风险", icon = Icons.Rounded.Policy, onClick = { nav.push(Route.LegalDocument("disclaimer")) })
                RowDivider()
                SettingRow("阅读用户须知", subtitle = "数据去向、分享及第三方服务", icon = Icons.Rounded.VerifiedUser, onClick = { nav.push(Route.LegalDocument("notice")) })
                RowDivider()
                SettingRow("权限与数据说明", subtitle = "联网、位置、通知、安装与文件访问", icon = Icons.Rounded.PrivacyTip, onClick = { nav.push(Route.Permissions) })
            }
        }

        item { SectionTitle("关于") }
        item {
            GlassGroup {
                SettingRow("关于与更新", subtitle = "版本 $APP_VERSION_NAME", icon = Icons.Default.Info, onClick = { nav.push(Route.About) })
            }
        }
        item { Spacer(Modifier.height(8.dp)) }
    }
}
