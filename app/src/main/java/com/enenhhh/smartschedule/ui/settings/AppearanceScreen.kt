package com.enenhhh.smartschedule.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Animation
import androidx.compose.material.icons.rounded.BlurOn
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.TextFields
import androidx.compose.material.icons.rounded.WaterDrop
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.ui.components.GlassGroup
import com.enenhhh.smartschedule.ui.components.GlassSheet
import com.enenhhh.smartschedule.ui.components.PillTabs
import com.enenhhh.smartschedule.ui.components.RowDivider
import com.enenhhh.smartschedule.ui.components.SectionTitle
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.ui.components.SettingSliderRow
import com.enenhhh.smartschedule.ui.components.SettingSwitchRow
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.glass.LocalGlass
import com.enenhhh.smartschedule.ui.glass.glassAccentCard
import com.enenhhh.smartschedule.ui.glass.glassCard
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.theme.AccentPresets
import com.enenhhh.smartschedule.ui.theme.PaletteOptions
import com.enenhhh.smartschedule.ui.theme.ThemeState
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.Prefs
import kotlinx.coroutines.launch

@Composable
fun AppearanceScreen() {
    val context = LocalContext.current
    val snackbar = LocalSnackbar.current
    val scope = rememberCoroutineScope()
    val cs = MaterialTheme.colorScheme
    val supportsDynamic = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val supportsLiquid = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    var customDialog by remember { mutableStateOf(false) }
    var fontSheet by remember { mutableStateOf(false) }

    SubPage(title = "外观与主题", subtitle = "即时预览 · 默认湛蓝，保留蓝色相") { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ---- 实时预览
            Box(Modifier.fillMaxWidth().glassAccentCard().padding(18.dp)) {
                Column {
                    Text("预览", style = MaterialTheme.typography.labelLarge, color = cs.onPrimaryContainer.copy(alpha = 0.8f))
                    Text("数学 · 第 2 节 · 08:50 – 09:30", style = MaterialTheme.typography.titleLarge, color = cs.onPrimaryContainer, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(cs.primary, cs.secondary, cs.tertiary, cs.primaryContainer, cs.tertiaryContainer, cs.surfaceVariant).forEach { c ->
                            Box(Modifier.size(28.dp).background(c, CircleShape).border(1.dp, cs.onPrimaryContainer.copy(alpha = 0.15f), CircleShape))
                        }
                        Spacer(Modifier.weight(1f))
                        Box(
                            Modifier
                                .glassCard(CircleShape)
                                .padding(horizontal = 14.dp, vertical = 6.dp)
                        ) { Text("玻璃", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }

            // ---- 主题色
            SectionTitle("主题色")
            GlassGroup {
                if (supportsDynamic) {
                    SettingSwitchRow(
                        title = "跟随壁纸取色",
                        subtitle = "Android 12+ 动态颜色，开启后忽略下面的主题色",
                        checked = ThemeState.dynamicColor,
                        onCheckedChange = { ThemeState.setDynamicColor(context, it) }
                    )
                    RowDivider()
                }
                FlowRow(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    AccentPresets.forEach { preset ->
                        ColorDot(
                            color = preset.seed,
                            selected = !ThemeState.dynamicColor && ThemeState.seedKey == preset.key,
                            label = preset.label,
                            onClick = {
                                if (ThemeState.dynamicColor) ThemeState.setDynamicColor(context, false)
                                ThemeState.setSeed(context, preset.key)
                            }
                        )
                    }
                    ColorDot(
                        color = Color(ThemeState.customSeed),
                        selected = !ThemeState.dynamicColor && ThemeState.seedKey == "custom",
                        label = "自定义",
                        plus = true,
                        onClick = { customDialog = true }
                    )
                }
            }

            // ---- 配色风格
            SectionTitle("配色风格")
            GlassGroup {
                FlowRow(
                    Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    PaletteOptions.forEach { opt ->
                        FilterChip(
                            selected = ThemeState.paletteKey == opt.key,
                            onClick = { ThemeState.setPalette(context, opt.key) },
                            label = { Text(opt.label) },
                            leadingIcon = if (ThemeState.paletteKey == opt.key) ({ Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }) else null
                        )
                    }
                }
                Text(
                    PaletteOptions.firstOrNull { it.key == ThemeState.paletteKey }?.desc ?: "",
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                )
            }

            // ---- 深浅与对比度
            SectionTitle("深浅模式")
            GlassGroup {
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    PillTabs(
                        options = listOf(Prefs.DARK_MODE_SYSTEM, Prefs.DARK_MODE_OFF, Prefs.DARK_MODE_ON),
                        selected = ThemeState.darkMode,
                        onSelect = { ThemeState.setDarkMode(context, it) },
                        label = { when (it) { Prefs.DARK_MODE_ON -> "深色"; Prefs.DARK_MODE_OFF -> "浅色"; else -> "跟随系统" } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                RowDivider()
                SettingSwitchRow(
                    title = "纯黑背景（AMOLED）",
                    subtitle = "关闭背景光斑，使用纯黑底色",
                    icon = Icons.Rounded.DarkMode,
                    checked = ThemeState.amoled,
                    onCheckedChange = { ThemeState.setAmoled(context, it) },
                    enabled = ThemeState.isDark()
                )
                RowDivider()
                Column(Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    Text("对比度", style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    PillTabs(
                        options = listOf(0, 1, 2),
                        selected = ThemeState.contrast,
                        onSelect = { ThemeState.setContrast(context, it) },
                        label = { when (it) { 1 -> "中"; 2 -> "高"; else -> "标准" } },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            // ---- 玻璃
            SectionTitle("液态玻璃")
            GlassGroup {
                SettingSwitchRow(
                    title = "玻璃效果",
                    subtitle = "卡片、顶栏、底栏呈半透明磨砂质感",
                    icon = Icons.Rounded.BlurOn,
                    checked = ThemeState.glassEnabled,
                    onCheckedChange = { ThemeState.setGlassEnabled(context, it) }
                )
                RowDivider()
                SettingSliderRow(
                    title = "玻璃强度",
                    value = ThemeState.glassIntensity,
                    onValueChange = { ThemeState.setGlassIntensity(context, it) },
                    enabled = ThemeState.glassEnabled
                )
                Text(
                    when {
                        !ThemeState.glassEnabled -> "已关闭：使用不透明的实色卡片，性能最好。"
                        ThemeState.glassIntensity < 0.3f -> "轻：略带透明，背景几乎不透出来。"
                        ThemeState.glassIntensity < 0.7f -> "适中：有明显的磨砂和高光，兼顾可读性。"
                        else -> "强：大面积透出背景，模糊半径更大，低端机可能更耗电。"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                )
                RowDivider()
                SettingSwitchRow(
                    title = "液态折射",
                    subtitle = if (supportsLiquid) "底栏和浮层带边缘折射与高光（Android 13+）" else "需要 Android 13 及以上，当前设备不支持",
                    icon = Icons.Rounded.WaterDrop,
                    checked = ThemeState.liquidGlass && supportsLiquid,
                    onCheckedChange = { ThemeState.setLiquidGlass(context, it) },
                    enabled = ThemeState.glassEnabled && supportsLiquid
                )
                RowDivider()
                SettingSwitchRow(
                    title = "背景缓慢流动",
                    subtitle = "彩色光斑缓慢漂移，关闭可省电",
                    icon = Icons.Rounded.Animation,
                    checked = ThemeState.animatedBackground,
                    onCheckedChange = { ThemeState.setAnimatedBackground(context, it) },
                    enabled = ThemeState.glassEnabled
                )
            }

            SectionTitle("动效与交互")
            GlassGroup {
                SettingSwitchRow(
                    title = "减少动态效果",
                    subtitle = "关闭位移、按压缩放与持续流动；系统动画缩放为 0 时同样生效",
                    icon = Icons.Rounded.Animation,
                    checked = ThemeState.reduceMotion,
                    onCheckedChange = { ThemeState.setReduceMotion(context, it) }
                )
            }

            // ---- 高级
            SectionTitle("高级")
            GlassGroup {
                SettingRow(
                    title = "字体",
                    subtitle = if (FontManager.currentMode() == FontManager.MODE_CUSTOM) "自定义字体文件" else "系统默认",
                    icon = Icons.Rounded.TextFields,
                    onClick = { fontSheet = true }
                )
            }
            Spacer(Modifier.height(16.dp))
        }
    }

    if (customDialog) {
        CustomColorDialog(
            initial = Color(ThemeState.customSeed),
            onDismiss = { customDialog = false },
            onConfirm = { color ->
                if (ThemeState.dynamicColor) ThemeState.setDynamicColor(context, false)
                ThemeState.setCustomSeed(context, color.toArgb())
                ThemeState.setSeed(context, "custom")
            }
        )
    }

    if (fontSheet) {
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null) {
                val ok = FontManager.useCustomFontFromUri(context, uri)
                scope.launch { snackbar.showSnackbar(if (ok) "字体已应用" else "无法读取这个字体文件") }
                if (ok) fontSheet = false
            }
        }
        GlassSheet(onDismiss = { fontSheet = false }) {
            Text("字体", style = MaterialTheme.typography.titleLarge)
            Text("可选功能：导入 .ttf / .otf 字体文件替换全局字体。", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            SettingRow(
                title = "系统默认",
                onClick = { FontManager.useSystemDefault(); fontSheet = false },
                showChevron = false,
                trailing = if (FontManager.currentMode() == FontManager.MODE_SYSTEM) ({ Icon(Icons.Rounded.Check, null, tint = cs.primary) }) else null
            )
            SettingRow(
                title = "选择字体文件…",
                subtitle = "支持 ttf / otf",
                onClick = { picker.launch(arrayOf("font/*", "application/x-font-ttf", "application/octet-stream", "*/*")) },
                showChevron = false,
                trailing = if (FontManager.currentMode() == FontManager.MODE_CUSTOM) ({ Icon(Icons.Rounded.Check, null, tint = cs.primary) }) else null
            )
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ColorDot(color: Color, selected: Boolean, label: String, onClick: () -> Unit, plus: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.width(52.dp)) {
        Box(
            Modifier
                .size(40.dp)
                .background(color, CircleShape)
                .border(if (selected) 3.dp else 1.dp, if (selected) cs.onSurface else cs.outlineVariant.copy(alpha = 0.6f), CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center
        ) {
            if (selected) Icon(Icons.Rounded.Check, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(20.dp))
            else if (plus) Icon(Icons.Rounded.Add, null, tint = if (color.luminance() > 0.5f) Color.Black else Color.White, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant, maxLines = 1)
    }
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue

@Composable
private fun CustomColorDialog(initial: Color, onDismiss: () -> Unit, onConfirm: (Color) -> Unit) {
    var hex by remember { mutableStateOf("%06X".format(initial.toArgb() and 0xFFFFFF)) }
    val parsed = parseHex(hex)
    val swatches = listOf(
        0xFF1E88E5, 0xFF00ACC1, 0xFF2E7D32, 0xFFC0CA33, 0xFFFB8C00, 0xFFD81B60, 0xFF6A1B9A, 0xFF37474F,
        0xFF7C4DFF, 0xFF00BFA5, 0xFFFF5252, 0xFF795548
    ).map { Color(it) }
    GlassDialog(
        onDismiss = onDismiss,
        title = "自定义主题色",
        confirmEnabled = parsed != null,
        onConfirm = { parsed?.let { onConfirm(it) }; onDismiss() }
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            swatches.forEach { c ->
                Box(
                    Modifier
                        .size(34.dp)
                        .background(c, CircleShape)
                        .border(if (parsed == c) 3.dp else 0.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .clickable { hex = "%06X".format(c.toArgb() and 0xFFFFFF) }
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = hex,
            onValueChange = { hex = it.trimStart('#').uppercase().take(6) },
            label = { Text("十六进制颜色") },
            prefix = { Text("#") },
            singleLine = true,
            isError = parsed == null,
            supportingText = { Text(if (parsed == null) "请输入 6 位十六进制，例如 006BDF" else "整套配色会由这个颜色生成") },
            trailingIcon = { Box(Modifier.size(24.dp).background(parsed ?: Color.Transparent, CircleShape)) },
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

private fun parseHex(s: String): Color? {
    if (s.length != 6) return null
    val v = s.toLongOrNull(16) ?: return null
    return Color((0xFF000000L or v).toInt())
}
