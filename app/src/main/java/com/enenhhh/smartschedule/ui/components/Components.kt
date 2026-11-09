package com.enenhhh.smartschedule.ui.components

import androidx.compose.animation.animateColorAsState
import com.enenhhh.smartschedule.ui.motion.pressScale
import androidx.compose.ui.semantics.Role
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.fillMaxSize
import com.enenhhh.smartschedule.ui.app.LocalNavigator
import androidx.compose.runtime.remember
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.staticCompositionLocalOf
import com.enenhhh.smartschedule.ui.nav.Route
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enenhhh.smartschedule.ui.glass.LocalGlass
import com.enenhhh.smartschedule.ui.glass.glassBar
import com.enenhhh.smartschedule.ui.glass.glassCard
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

// --------------------------------------------------------------------------- 顶栏

/**
 * 子页面顶栏：玻璃背景，覆盖状态栏区域。内容需要自行预留 [TopBarHeight] + 状态栏的空间。
 */
@Composable
fun GlassTopBar(
    title: String,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .glassBar(shape = RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(TopBarHeight)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(TopBarHeight).padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回", tint = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Spacer(Modifier.width(16.dp))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
            actions()
            Spacer(Modifier.width(4.dp))
        }
    }
}

val TopBarHeight = 60.dp

/**
 * 顶栏槽位。
 *
 * Haze 规定：hazeBlur / hazeGlass 节点不能位于它所取样的 hazeSource 子树之内（会直接抛异常）。
 * 页面内容整体注册为 hazeSource，所以子页面的玻璃顶栏不能画在页面里，而是通过这里"投递"给
 * App 根布局，由根布局在 hazeSource 之外、底栏同一层级渲染。
 */
class TopBarRegistry {
    internal class Entry(val token: Any, val bar: @Composable () -> Unit)
    internal val bars = mutableStateMapOf<Route, Entry>()

    @Composable
    fun Render(route: Route) {
        bars[route]?.bar?.invoke()
    }
}

val LocalTopBarRegistry = staticCompositionLocalOf<TopBarRegistry?> { null }

/** 当前页面所属的路由；由 App 根布局在渲染每个路由时提供 */
val LocalRoute = staticCompositionLocalOf<Route?> { null }

/**
 * 子页面骨架：内容铺满全屏（自己用传入的 PaddingValues 让出顶栏），玻璃顶栏叠在最上层
 * （实际由根布局渲染，见 [TopBarRegistry]）。
 */
@Composable
fun SubPage(
    title: String,
    subtitle: String? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val nav = LocalNavigator.current
    val back: () -> Unit = onBack ?: { nav.pop() }
    val registry = LocalTopBarRegistry.current
    val route = LocalRoute.current
    val bar: @Composable () -> Unit = {
        GlassTopBar(title = title, subtitle = subtitle, onBack = back, actions = actions)
    }
    if (registry != null && route != null) {
        val token = remember { Any() }
        // 每次重组都刷新（标题 / 动作可能变化）；销毁时只移除自己登记的那一份
        SideEffect { registry.bars[route] = TopBarRegistry.Entry(token, bar) }
        DisposableEffect(registry, route) {
            onDispose { if (registry.bars[route]?.token === token) registry.bars.remove(route) }
        }
        Box(Modifier.fillMaxSize()) { content(pageContentPadding()) }
    } else {
        // 没有根布局槽位（例如独立预览）时退化为页面内渲染
        Box(Modifier.fillMaxSize()) {
            content(pageContentPadding())
            Box(Modifier.align(Alignment.TopCenter)) { bar() }
        }
    }
}

/** 子页面内容区统一的内边距：顶部让出顶栏，底部让出导航栏 */
@Composable
fun pageContentPadding(extraTop: Int = 12, extraBottom: Int = 32): PaddingValues {
    val status = WindowInsets.statusBars.asPaddingTop()
    val nav = WindowInsets.navigationBars.asPaddingBottom()
    return PaddingValues(
        start = 16.dp, end = 16.dp,
        top = status + TopBarHeight + extraTop.dp,
        bottom = nav + extraBottom.dp
    )
}

@Composable
private fun WindowInsets.asPaddingTop() =
    with(androidx.compose.ui.platform.LocalDensity.current) { getTop(this).toDp() }

@Composable
private fun WindowInsets.asPaddingBottom() =
    with(androidx.compose.ui.platform.LocalDensity.current) { getBottom(this).toDp() }

// --------------------------------------------------------------------------- 列表 / 设置项

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.padding(start = 12.dp, top = 8.dp, bottom = 6.dp)
    )
}

/** 一组设置项的玻璃容器 */
@Composable
fun GlassGroup(
    modifier: Modifier = Modifier,
    shape: Shape = MaterialTheme.shapes.large,
    content: @Composable ColumnScope.() -> Unit
) {
    CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) {
        Column(modifier = modifier.fillMaxWidth().glassCard(shape = shape), content = content)
    }
}

@Composable
fun SettingRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    onClick: (() -> Unit)? = null,
    showChevron: Boolean = onClick != null,
    trailing: (@Composable () -> Unit)? = null
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        modifier = modifier
            .fillMaxWidth()
            .pressScale(interaction)
            .let { if (onClick != null) it.clickable(interactionSource = interaction, indication = androidx.compose.material3.ripple(), onClick = onClick) else it }
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(iconTint.copy(alpha = 0.14f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(14.dp))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!subtitle.isNullOrBlank()) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            trailing()
        } else if (showChevron) {
            Icon(
                Icons.Default.ChevronRight, contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun SettingSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    icon: ImageVector? = null,
    iconTint: Color = MaterialTheme.colorScheme.primary,
    enabled: Boolean = true
) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        icon = icon,
        iconTint = iconTint,
        modifier = modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange),
        onClick = null,
        showChevron = false,
        trailing = { Switch(checked = checked, onCheckedChange = null, enabled = enabled) }
    )
}

@Composable
fun SettingSliderRow(
    title: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    valueLabel: String = "${(value * 100).toInt()}%",
    enabled: Boolean = true,
    steps: Int = 0
) {
    Column(modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Text(valueLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
        Slider(value = value, onValueChange = onValueChange, enabled = enabled, steps = steps)
    }
}

@Composable
fun RowDivider() {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 16.dp)
            .height(1.dp)
            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    )
}

// --------------------------------------------------------------------------- 分段选择

@Composable
fun <T> PillTabs(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = options.size),
                label = { Text(label(option), maxLines = 1) }
            )
        }
    }
}

// --------------------------------------------------------------------------- 对话框 / 底部面板

/** 玻璃风格对话框。标题 + 任意内容 + 底部按钮 */
@Composable
fun GlassDialog(
    onDismiss: () -> Unit,
    title: String?,
    modifier: Modifier = Modifier,
    confirmText: String? = "确定",
    onConfirm: (() -> Unit)? = null,
    dismissText: String? = "取消",
    confirmEnabled: Boolean = true,
    destructive: Boolean = false,
    dismissOnClickOutside: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, dismissOnClickOutside = dismissOnClickOutside)
    ) {
        val shape = MaterialTheme.shapes.extraLarge
        Column(
            modifier = modifier
                .padding(horizontal = 28.dp, vertical = 24.dp)
                .fillMaxWidth()
                .glassBar(shape = shape, tint = MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 24.dp, vertical = 20.dp)
        ) {
            if (title != null) {
                Text(title, style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(14.dp))
            }
            content()
            if (confirmText != null || dismissText != null) {
                Spacer(Modifier.height(18.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (dismissText != null) {
                        TextButton(onClick = onDismiss) { Text(dismissText) }
                    }
                    if (confirmText != null) {
                        Spacer(Modifier.width(8.dp))
                        if (destructive) {
                            FilledTonalButton(onClick = { onConfirm?.invoke() }, enabled = confirmEnabled) { Text(confirmText) }
                        } else {
                            Button(onClick = { onConfirm?.invoke() }, enabled = confirmEnabled) { Text(confirmText) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmText: String = "确定",
    destructive: Boolean = false
) {
    GlassDialog(
        onDismiss = onDismiss,
        title = title,
        confirmText = confirmText,
        onConfirm = { onConfirm(); onDismiss() },
        destructive = destructive
    ) {
        Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun TextInputDialog(
    title: String,
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    label: String = "名称",
    validate: (String) -> String? = { null },
    placeholder: String = ""
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    val error = validate(text)
    GlassDialog(
        onDismiss = onDismiss,
        title = title,
        confirmText = "确定",
        confirmEnabled = error == null && text.isNotBlank(),
        onConfirm = { onConfirm(text.trim()); onDismiss() }
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            label = { Text(label) },
            placeholder = { Text(placeholder) },
            singleLine = true,
            isError = error != null,
            supportingText = if (error != null) ({ Text(error) }) else null,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** 玻璃底部面板 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlassSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onSurface,
        tonalElevation = 0.dp,
        dragHandle = null,
        scrimColor = Color.Black.copy(alpha = 0.32f)
    ) {
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            modifier = modifier
                .fillMaxWidth()
                .glassBar(shape = shape, tint = MaterialTheme.colorScheme.surfaceContainerHigh)
                .padding(horizontal = 20.dp)
                .padding(bottom = 12.dp)
                .windowInsetsPadding(WindowInsets.navigationBars)
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 12.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .width(36.dp)
                        .height(4.dp)
                        .background(MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f), CircleShape)
                )
            }
            content()
        }
    }
}

// --------------------------------------------------------------------------- 时间 / 日期选择

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimePickerDialogM3(
    initialHour: Int,
    initialMinute: Int,
    title: String = "选择时间",
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit
) {
    val state = rememberTimePickerState(initialHour = initialHour, initialMinute = initialMinute, is24Hour = true)
    GlassDialog(
        onDismiss = onDismiss,
        title = title,
        onConfirm = { onConfirm(state.hour, state.minute); onDismiss() }
    ) {
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            TimePicker(state = state)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DatePickerDialogM3(
    initial: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit
) {
    val state = rememberDatePickerState(
        initialSelectedDateMillis = initial.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = {
                val millis = state.selectedDateMillis
                if (millis != null) {
                    onConfirm(Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate())
                }
                onDismiss()
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
        shape = MaterialTheme.shapes.extraLarge
    ) {
        DatePicker(state = state, showModeToggle = false)
    }
}

// --------------------------------------------------------------------------- 空状态 / 其它

@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    actionText: String? = null,
    onAction: (() -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(34.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(6.dp))
        Text(
            description,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )
        if (actionText != null && onAction != null) {
            Spacer(Modifier.height(18.dp))
            Button(onClick = onAction) { Text(actionText) }
        }
    }
}

/** 课程名 → 稳定且和主题协调的颜色 */
@Composable
fun courseColor(name: String): Color {
    val cs = MaterialTheme.colorScheme
    val dark = LocalGlass.current.dark
    if (name.isBlank()) return cs.surfaceVariant
    val hash = name.fold(7) { acc, c -> acc * 31 + c.code }
    val hue = ((hash % 360) + 360) % 360
    val sat = if (dark) 0.42f else 0.55f
    val light = if (dark) 0.34f else 0.80f
    return Color.hsl(hue.toFloat(), sat, light)
}

@Composable
fun onCourseColor(bg: Color): Color {
    val dark = LocalGlass.current.dark
    return if (dark) Color(0xFFF3F3F6) else Color(0xFF1C1B1F)
}

@Composable
fun CourseChip(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = courseColor(text),
    selected: Boolean = false,
    onClick: (() -> Unit)? = null
) {
    val bg by animateColorAsState(if (selected) MaterialTheme.colorScheme.primary else color, label = "chipBg")
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary else onCourseColor(color)
    Box(
        modifier
            .background(bg, RoundedCornerShape(10.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(text, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Medium, maxLines = 1)
    }
}

/** 无涟漪的点击，用于整块卡片 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = this.clickable(
    interactionSource = MutableInteractionSource(),
    indication = null,
    onClick = onClick
)

@Composable
fun Dimmed(alpha: Float = 0.6f, content: @Composable () -> Unit) {
    CompositionLocalProvider(LocalContentColor provides LocalContentColor.current.copy(alpha = alpha), content = content)
}

@Composable
fun FadeAlpha(visible: Boolean): Float {
    val a by animateFloatAsState(if (visible) 1f else 0f, label = "fade")
    return a
}
