package com.enenhhh.smartschedule.ui.timetable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.models.TimePlan
import com.enenhhh.smartschedule.models.Timetable
import com.enenhhh.smartschedule.ui.components.courseColor
import com.enenhhh.smartschedule.ui.components.onCourseColor
import com.enenhhh.smartschedule.util.DateUtils

private val TimeColWidth = 44.dp
private val CellHeight = 60.dp

/**
 * 整周课表网格。查看页和编辑页共用：
 *  - [isOdd] 当前显示单周还是双周
 *  - [days] 要显示的星期列表（1..7）
 *  - [todayIndex] / [currentPeriod] 高亮今天和正在上的节次
 *  - [shortName] 把课程名变成格子里显示的简写
 *  - [editing] 编辑模式下空格子画虚框，并显示「单双周不同」标记
 */
@Composable
fun WeekGrid(
    timetable: Timetable,
    timePlan: TimePlan?,
    isOdd: Boolean,
    days: List<Int>,
    todayIndex: Int?,
    currentPeriod: Int?,
    shortName: (String) -> String,
    modifier: Modifier = Modifier,
    editing: Boolean = false,
    onCellClick: ((dayIndex: Int, period: Int) -> Unit)? = null,
    onDayClick: ((dayIndex: Int) -> Unit)? = null,
    onMorningClick: ((dayIndex: Int) -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val periods = timePlan?.periods ?: emptyList()
    val showMorning = days.any { d -> timetable.day(d).let { it.morningRead.isNotBlank() || !it.evenMorningRead.isNullOrBlank() } } || editing

    Column(modifier.fillMaxWidth().padding(8.dp)) {
        // 表头
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.width(TimeColWidth))
            days.forEach { d ->
                val isToday = d == todayIndex
                val alternates = timetable.day(d).alternates
                Column(
                    Modifier
                        .weight(1f)
                        .padding(horizontal = 2.dp)
                        .let { m -> if (onDayClick != null) m.clickable { onDayClick(d) } else m }
                        .background(if (isToday) cs.primary else Color.Transparent, RoundedCornerShape(12.dp))
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        DateUtils.dayName(d, prefix = ""),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isToday) cs.onPrimary else cs.onSurface
                    )
                    if (editing && alternates) {
                        Text(
                            "单/双",
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isToday) cs.onPrimary.copy(alpha = 0.85f) else cs.tertiary
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))

        // 晨读行
        if (showMorning) {
            Row(Modifier.fillMaxWidth().height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.width(TimeColWidth), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.MenuBook, contentDescription = "晨读", tint = cs.tertiary, modifier = Modifier.width(18.dp))
                }
                days.forEach { d ->
                    val text = timetable.day(d).morningReadFor(isOdd)
                    Box(
                        Modifier
                            .weight(1f)
                            .padding(2.dp)
                            .height(36.dp)
                            .background(
                                if (text.isNotBlank()) cs.tertiaryContainer.copy(alpha = 0.8f) else Color.Transparent,
                                RoundedCornerShape(10.dp)
                            )
                            .let { m ->
                                if (editing && text.isBlank()) m.border(1.dp, cs.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(10.dp)) else m
                            }
                            .let { m -> if (onMorningClick != null) m.clickable { onMorningClick(d) } else m },
                        contentAlignment = Alignment.Center
                    ) {
                        if (text.isNotBlank()) {
                            Text(
                                shortName(text),
                                style = MaterialTheme.typography.labelMedium,
                                color = cs.onTertiaryContainer,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }

        // 节次行
        periods.forEachIndexed { pi, p ->
            val isCurrentRow = pi == currentPeriod
            Row(Modifier.fillMaxWidth().height(CellHeight), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    Modifier
                        .width(TimeColWidth)
                        .height(CellHeight - 4.dp)
                        .background(if (isCurrentRow) cs.primary.copy(alpha = 0.12f) else Color.Transparent, RoundedCornerShape(10.dp)),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text("${pi + 1}", style = MaterialTheme.typography.titleSmall, color = cs.onSurface)
                    Text(p.start, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
                }
                days.forEach { d ->
                    val name = timetable.day(d).courseAt(isOdd, pi)
                    val isToday = d == todayIndex
                    GridCell(
                        name = name,
                        display = if (name.isBlank()) "" else shortName(name),
                        highlight = isToday && isCurrentRow,
                        today = isToday,
                        editing = editing,
                        modifier = Modifier.weight(1f),
                        onClick = if (onCellClick != null) ({ onCellClick(d, pi) }) else null
                    )
                }
            }
        }
    }
}

@Composable
private fun GridCell(
    name: String,
    display: String,
    highlight: Boolean,
    today: Boolean,
    editing: Boolean,
    modifier: Modifier,
    onClick: (() -> Unit)?
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(12.dp)
    val color = if (name.isBlank()) Color.Transparent else courseColor(name)
    Box(
        modifier
            .padding(2.dp)
            .height(CellHeight - 4.dp)
            .background(color, shape)
            .let { m ->
                when {
                    highlight -> m.border(2.dp, cs.primary, shape)
                    name.isBlank() && editing -> m.border(1.dp, cs.outlineVariant.copy(alpha = 0.55f), shape)
                    name.isBlank() && today -> m.background(cs.primary.copy(alpha = 0.05f), shape)
                    else -> m
                }
            }
            .let { m -> if (onClick != null) m.clickable(onClick = onClick) else m },
        contentAlignment = Alignment.Center
    ) {
        if (display.isNotBlank()) {
            Text(
                display,
                style = if (display.length <= 2) MaterialTheme.typography.titleSmall else MaterialTheme.typography.labelMedium,
                color = onCourseColor(color),
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 3.dp)
            )
        } else if (editing) {
            Box(Modifier.width(6.dp).height(6.dp).background(cs.outlineVariant.copy(alpha = 0.6f), CircleShape))
        }
    }
}
