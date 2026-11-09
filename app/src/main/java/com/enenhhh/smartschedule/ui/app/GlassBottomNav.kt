package com.enenhhh.smartschedule.ui.app

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.selection.selectable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.snap
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import com.enenhhh.smartschedule.ui.motion.motionEnabled
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewWeek
import androidx.compose.material.icons.outlined.Checklist
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material.icons.rounded.CalendarViewWeek
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.ui.glass.LocalGlass
import com.enenhhh.smartschedule.ui.glass.liquidGlass
import com.enenhhh.smartschedule.ui.nav.Route

private data class NavItem(val route: Route, val label: String, val icon: ImageVector, val iconSelected: ImageVector)

/** 底部导航胶囊的总高度（含外边距），顶层页面内容底部需要预留 */
val BottomNavReserve = 92.dp

@Composable
fun GlassBottomNav(current: Route, onSelect: (Route) -> Unit) {
    val items = remember {
        listOf(
            NavItem(Route.Today, "今日", Icons.Outlined.Today, Icons.Rounded.Today),
            NavItem(Route.Timetable, "课表", Icons.Outlined.CalendarViewWeek, Icons.Rounded.CalendarViewWeek),
            NavItem(Route.Homework, "作业", Icons.Outlined.Checklist, Icons.Rounded.Checklist),
            NavItem(Route.Settings, "设置", Icons.Outlined.Settings, Icons.Rounded.Settings),
        )
    }
    val glass = LocalGlass.current
    Row(
        modifier = Modifier
            .navigationBarsPadding()
            .padding(start = 20.dp, end = 20.dp, bottom = 12.dp)
            .fillMaxWidth()
            .height(68.dp)
            .liquidGlass(
                shape = CircleShape,
                tint = if (glass.dark) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { item ->
            NavEntry(item, selected = current == item.route, onClick = { onSelect(item.route) }, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun NavEntry(item: NavItem, selected: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val cs = MaterialTheme.colorScheme
    val pill by animateColorAsState(if (selected) cs.primaryContainer else Color.Transparent, label = "pill")
    val tint by animateColorAsState(if (selected) cs.onPrimaryContainer else cs.onSurfaceVariant, label = "tint")
    val motion = motionEnabled()
    val haptic = LocalHapticFeedback.current
    val scale by animateFloatAsState(if (selected) 1.05f else 0.94f,
        animationSpec = if (motion) spring(dampingRatio = 0.65f, stiffness = 430f) else snap(), label = "scale")
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(56.dp)
            .selectable(
                selected = selected,
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                role = Role.Tab,
                onClick = { if (!selected) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove); onClick() }
            ),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.graphicsLayer { scaleX = scale; scaleY = scale }
        ) {
            Box(
                Modifier
                    .width(52.dp)
                    .height(30.dp)
                    .background(pill, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (selected) item.iconSelected else item.icon,
                    contentDescription = item.label,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(Modifier.height(2.dp))
            Text(
                item.label,
                style = MaterialTheme.typography.labelSmall,
                color = tint
            )
        }
    }
}
