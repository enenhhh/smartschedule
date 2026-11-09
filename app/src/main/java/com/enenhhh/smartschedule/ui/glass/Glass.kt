package com.enenhhh.smartschedule.ui.glass

import android.os.Build
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeInput
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.blur.HazeBlurStyle
import dev.chrisbanes.haze.blur.HazeColorEffect
import dev.chrisbanes.haze.blur.hazeBlur
import dev.chrisbanes.haze.glass.GlassStyle
import dev.chrisbanes.haze.glass.hazeGlass
import kotlin.math.cos
import kotlin.math.sin

/**
 * 玻璃风格的全部参数。只有一个用户可调的「强度」，其它数值都由它推导，
 * 保证不同强度下界面依然协调、文字依然可读。
 */
@Immutable
data class GlassSpec(
    val enabled: Boolean,
    val intensity: Float,     // 0..1
    val liquid: Boolean,      // 折射 / 高光（Android 13+）
    val animated: Boolean,    // 背景缓慢流动
    val dark: Boolean,
    val amoled: Boolean = false
) {
    val active: Boolean get() = enabled && intensity > 0.01f
    private val t: Float get() = if (active) intensity else 0f

    /** 顶栏 / 底栏的真实背景模糊半径 */
    val blurRadius: Dp get() = (8f + 28f * t).dp

    /** 顶栏 / 底栏着色不透明度：强度越高越透 */
    val barTintAlpha: Float get() = if (dark) 0.92f - 0.20f * t else 0.94f - 0.28f * t

    /** 卡片底色不透明度 */
    val cardAlpha: Float get() = if (!active) 1f else if (dark) 0.94f - 0.18f * t else 0.94f - 0.28f * t

    /** 边缘高光强度 */
    val highlightAlpha: Float get() = if (!active) 0f else (if (dark) 0.10f else 0.35f) + 0.18f * t

    val noise: Float get() = 0.015f + 0.06f * t

    /** 背景色块的鲜艳度 */
    val blobAlpha: Float get() = if (!active || amoled) 0f else (if (dark) 0.18f else 0.34f) + 0.14f * t

    val useLiquid: Boolean get() = active && liquid && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    companion object {
        val Off = GlassSpec(enabled = false, intensity = 0f, liquid = false, animated = false, dark = false)
    }
}

val LocalGlass = staticCompositionLocalOf { GlassSpec.Off }

/** 由 App 根布局提供；为 null 表示当前不在玻璃容器里（例如独立对话框），自动退化为普通表面 */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

// --------------------------------------------------------------------------- 修饰符

/**
 * 顶栏 / 底栏 / 对话框：对背后内容做真实模糊（Android 12+），更低版本自动退化为半透明遮罩。
 */
@Composable
fun Modifier.glassBar(
    shape: Shape,
    tint: Color = MaterialTheme.colorScheme.surface,
    spec: GlassSpec = LocalGlass.current,
    haze: HazeState? = LocalHazeState.current
): Modifier {
    val solid = MaterialTheme.colorScheme.surfaceContainer
    if (!spec.active || haze == null) {
        return this.clip(shape).background(solid, shape)
    }
    val style = remember(spec, tint) {
        HazeBlurStyle {
            blurRadius(spec.blurRadius)
            noiseFactor(spec.noise)
            backgroundColor(tint)
            colorEffects(listOf(HazeColorEffect.tint(tint.copy(alpha = spec.barTintAlpha))))
            fallbackColorEffect(HazeColorEffect.tint(tint.copy(alpha = 0.94f)))
        }
    }
    return this
        .clip(shape)
        .hazeBlur(input = HazeInput.Sources(haze), style = style)
        .glassHighlight(shape, spec)
}

/**
 * 液态玻璃：带折射和高光的小面积表面（底部导航胶囊、悬浮按钮）。
 * Android 13 以下或用户关闭「液态效果」时退化为 [glassBar]。
 */
@Composable
fun Modifier.liquidGlass(
    shape: RoundedCornerShape,
    tint: Color = MaterialTheme.colorScheme.surface,
    spec: GlassSpec = LocalGlass.current,
    haze: HazeState? = LocalHazeState.current
): Modifier {
    if (!spec.useLiquid || haze == null) return this.glassBar(shape, tint, spec, haze)
    val style = remember(spec, tint, shape) {
        GlassStyle.regular.then {
            shape(shape)
            tint(tint.copy(alpha = if (spec.dark) 0.70f + 0.14f * (1f - spec.intensity) else 0.64f + 0.20f * (1f - spec.intensity)))
            specularIntensity(0.35f + 0.4f * spec.intensity)
        }
    }
    return this
        .hazeGlass(input = HazeInput.Sources(haze), style = style)
        .glassHighlight(shape, spec)
}

/**
 * 普通卡片：背景是缓慢流动的柔和色块，所以不需要真实模糊——半透明表面 + 边缘高光
 * 就有磨砂玻璃的观感，而且列表里几十张卡片也不会有性能压力。
 */
@Composable
fun Modifier.glassCard(
    shape: Shape = MaterialTheme.shapes.large,
    tint: Color = MaterialTheme.colorScheme.surface,
    spec: GlassSpec = LocalGlass.current
): Modifier {
    val base = if (spec.active) tint.copy(alpha = spec.cardAlpha) else MaterialTheme.colorScheme.surfaceContainerLow
    return this
        .clip(shape)
        .background(base, shape)
        .glassHighlight(shape, spec)
}

/** 强调卡片：底色带主题色（如今日课程、当前节次） */
@Composable
fun Modifier.glassAccentCard(
    shape: Shape = MaterialTheme.shapes.large,
    accent: Color = MaterialTheme.colorScheme.primaryContainer,
    spec: GlassSpec = LocalGlass.current
): Modifier {
    val surface = MaterialTheme.colorScheme.surface
    val base = if (spec.active) accent.copy(alpha = (spec.cardAlpha + 0.1f).coerceAtMost(1f))
    else accent
    return this
        .clip(shape)
        .background(base, shape)
        .drawWithContent {
            if (spec.active) {
                drawRect(
                    Brush.verticalGradient(
                        0f to surface.copy(alpha = 0.18f + 0.1f * spec.intensity),
                        0.6f to Color.Transparent
                    )
                )
            }
            drawContent()
        }
        .glassHighlight(shape, spec)
}

/** 边缘 1dp 渐变描边 + 顶部一抹反光，是「玻璃感」的关键 */
fun Modifier.glassHighlight(shape: Shape, spec: GlassSpec): Modifier {
    if (!spec.active) return this
    val hi = spec.highlightAlpha
    val edge = if (spec.dark) Color.White else Color.White
    val shadow = if (spec.dark) Color.White.copy(alpha = 0.04f) else Color.Black.copy(alpha = 0.05f)
    return this
        .border(
            width = 1.dp,
            brush = Brush.linearGradient(
                0f to edge.copy(alpha = hi),
                0.45f to edge.copy(alpha = hi * 0.25f),
                1f to shadow
            ),
            shape = shape
        )
        .drawWithContent {
            // 顶部内侧的柔和反光
            drawRect(
                brush = Brush.verticalGradient(
                    0f to Color.White.copy(alpha = (if (spec.dark) 0.05f else 0.22f) * hi),
                    1f to Color.Transparent,
                    endY = size.height * 0.35f
                )
            )
            drawContent()
        }
}

// --------------------------------------------------------------------------- 背景

/**
 * 全屏柔和色块背景：从当前配色取 4 个颜色，各自沿圆形轨迹缓慢漂移。
 * 玻璃关闭时只画纯色底。
 */
@Composable
fun MeshBackground(modifier: Modifier = Modifier, spec: GlassSpec = LocalGlass.current) {
    val cs = MaterialTheme.colorScheme
    val bg = cs.background
    val c1 = cs.primaryContainer
    val c2 = cs.tertiaryContainer
    val c3 = cs.secondaryContainer
    val c4 = cs.primary
    val phases: List<State<Float>> = if (spec.animated && spec.active && !spec.amoled) {
        val transition = rememberInfiniteTransition(label = "mesh")
        listOf(26000, 31000, 37000, 43000).mapIndexed { i, dur ->
            transition.animateFloat(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(dur, easing = LinearEasing), RepeatMode.Restart),
                label = "blob$i"
            )
        }
    } else {
        remember { listOf(0.13f, 0.42f, 0.71f, 0.88f).map { mutableFloatStateOf(it) } }
    }
    val alpha = spec.blobAlpha
    val dark = spec.dark
    Canvas(modifier = modifier) {
        drawRect(bg)
        if (!spec.active || spec.amoled) return@Canvas
        val w = size.width
        val h = size.height
        val r = maxOf(w, h)
        fun blob(color: Color, phase: Float, cx: Float, cy: Float, rx: Float, ry: Float, radius: Float, a: Float) {
            val ang = phase * 2f * Math.PI.toFloat()
            val center = Offset(cx + cos(ang) * rx, cy + sin(ang) * ry)
            val tinted = if (dark) lerp(color, bg, 0.35f) else color
            drawCircle(
                brush = Brush.radialGradient(
                    0f to tinted.copy(alpha = a),
                    0.55f to tinted.copy(alpha = a * 0.45f),
                    1f to tinted.copy(alpha = 0f),
                    center = center,
                    radius = radius
                ),
                radius = radius,
                center = center
            )
        }
        blob(c1, phases[0].value, w * 0.15f, h * 0.12f, w * 0.25f, h * 0.10f, r * 0.62f, alpha)
        blob(c2, phases[1].value, w * 0.90f, h * 0.30f, w * 0.20f, h * 0.14f, r * 0.55f, alpha * 0.9f)
        blob(c3, phases[2].value, w * 0.30f, h * 0.85f, w * 0.28f, h * 0.10f, r * 0.60f, alpha * 0.85f)
        blob(c4, phases[3].value, w * 0.85f, h * 0.95f, w * 0.18f, h * 0.08f, r * 0.42f, alpha * (if (dark) 0.35f else 0.22f))
    }
}

/** 文字放在玻璃上时用它取一个一定可读的前景色 */
@Composable
fun readableOn(background: Color): Color {
    val cs = MaterialTheme.colorScheme
    val composed = background.compositeOver(cs.surface)
    return if (composed.luminance() > 0.5f) Color(0xFF1A1C1E) else Color(0xFFF2F2F5)
}

private fun Color.luminance(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
