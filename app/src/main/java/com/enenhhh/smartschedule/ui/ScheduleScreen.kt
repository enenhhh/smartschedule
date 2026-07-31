package com.enenhhh.smartschedule.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.data.repository.AppDataRepository
import com.enenhhh.smartschedule.util.DateUtils
import com.enenhhh.smartschedule.util.Prefs

private val WEEK_DAYS = listOf("周一", "周二", "周三", "周四", "周五")

/** 整周课表的展示数据，只在切换单/双周时计算一次 */
private data class WeekGrid(
    val morningReads: List<String>,
    val rows: List<List<String>>
)

@Composable
fun ScheduleScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val repository = remember { AppDataRepository(context) }

    val startDate = remember { Prefs.getStartDate(context) }
    val currentWeek = remember { DateUtils.getWeekNumberFrom(startDate, System.currentTimeMillis()) }

    var isOdd by remember { mutableStateOf(currentWeek % 2 == 1) }

    // 简写解析放在 remember 里，避免每次重组都重新读盘
    val grid = remember(isOdd) {
        val plans = WEEK_DAYS.indices.map { repository.getActiveSchedule(isOdd, it + 1) }
        val maxLessons = plans.maxOfOrNull { it?.courses?.size ?: 0 } ?: 0
        WeekGrid(
            morningReads = plans.map { plan ->
                plan?.morningRead?.takeIf { it.isNotBlank() }?.let { repository.getShortName(it) }.orEmpty()
            },
            rows = (0 until maxLessons).map { lessonIdx ->
                plans.map { plan ->
                    repository.getShortName(plan?.courses?.getOrNull(lessonIdx).orEmpty())
                }
            }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            FilterChip(
                selected = isOdd,
                onClick = { isOdd = true },
                label = { Text("单周") }
            )
            FilterChip(
                selected = !isOdd,
                onClick = { isOdd = false },
                label = { Text("双周") }
            )
        }

        Text(
            text = "当前：第 $currentWeek 周（${if (currentWeek % 2 == 1) "单周" else "双周"}）",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // 表头
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            HeaderCell("节次", Modifier.width(40.dp))
            WEEK_DAYS.forEach { day -> HeaderCell(day, Modifier.weight(1f)) }
        }

        HorizontalDivider()

        // 早读行
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp, vertical = 4.dp)
        ) {
            HeaderCell("早读", Modifier.width(40.dp))
            grid.morningReads.forEach { text ->
                GridCell(text = text, modifier = Modifier.weight(1f))
            }
        }

        HorizontalDivider()

        grid.rows.forEachIndexed { lessonIdx, row ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(60.dp)
                    .padding(horizontal = 4.dp, vertical = 2.dp)
            ) {
                HeaderCell("${lessonIdx + 1}", Modifier.width(40.dp))
                row.forEach { name ->
                    GridCell(text = name, modifier = Modifier.weight(1f))
                }
            }
            HorizontalDivider()
        }

        if (grid.rows.isEmpty()) {
            Text(
                text = "本周还没有绑定课程方案，去「设置 → 方案管理」创建一个吧",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(24.dp)
            )
        }
    }
}

@Composable
private fun HeaderCell(text: String, modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun GridCell(text: String, modifier: Modifier = Modifier) {
    Card(
        modifier = modifier.padding(2.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (text.isBlank()) {
                MaterialTheme.colorScheme.surfaceContainerLow
            } else {
                MaterialTheme.colorScheme.secondaryContainer
            }
        )
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
                color = if (text.isBlank()) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSecondaryContainer
                },
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(4.dp)
            )
        }
    }
}
