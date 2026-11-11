package com.enenhhh.smartschedule.ui.aiimport

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.ui.components.courseColor
import com.enenhhh.smartschedule.ui.motion.motionEnabled

/**
 * 引导动画：一台小手机里循环演示 4 步——
 *  1. 在智能课表里点「复制提示词」
 *  2. 打开 AI 助手，发一张照片 + 粘贴提示词
 *  3. AI 回复一段代码，点「复制」
 *  4. 回到智能课表粘贴，课表 / 作业填好
 * 右边的步骤列表和动画同步，点某一步可以跳过去。关闭动画（系统或应用里）时只显示静止画面。
 */
private const val SCENE = 3.2f
private const val LOOP = SCENE * 4

private fun clamp01(x: Float) = x.coerceIn(0f, 1f)
private fun ease(x: Float): Float { val t = clamp01(x); return 1 - (1 - t) * (1 - t) * (1 - t) }
/** t 在 [a, b] 之间的进度（带缓出） */
private fun p(t: Float, a: Float, b: Float) = ease((t - a) / (b - a))

val GUIDE_STEPS = listOf("复制提示词", "拍照发给 AI", "复制 AI 的回复", "粘贴，完成")

@Composable
fun AiGuideAnimation(timetable: Boolean, modifier: Modifier = Modifier) {
    val motion = motionEnabled()
    var t by remember { mutableFloatStateOf(if (motion) 0f else SCENE * 3 + SCENE - 0.01f) }
    LaunchedEffect(motion) {
        if (!motion) return@LaunchedEffect
        var last = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                t = (t + (now - last) / 1e9f) % LOOP
                last = now
            }
        }
    }
    val scene = (t / SCENE).toInt().coerceIn(0, 3)
    val lt = t - scene * SCENE

    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        MiniPhone(width = 132.dp) {
            when (scene) {
                0 -> SceneCopyPrompt(lt)
                1 -> SceneChatSend(lt, timetable)
                2 -> SceneChatReply(lt)
                else -> ScenePaste(lt, timetable)
            }
        }
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            GUIDE_STEPS.forEachIndexed { i, label ->
                val active = i == scene
                val cs = MaterialTheme.colorScheme
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (active) cs.primary.copy(alpha = 0.10f) else Color.Transparent)
                        .clickable { t = i * SCENE + if (motion) 0f else SCENE - 0.01f }
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        Modifier.size(24.dp).background(if (i <= scene) cs.primary else cs.outlineVariant, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        if (i < scene) Icon(Icons.Rounded.Check, null, tint = cs.onPrimary, modifier = Modifier.size(15.dp))
                        else Text("${i + 1}", color = if (i <= scene) cs.onPrimary else cs.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            label,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = if (active) FontWeight.SemiBold else null,
                            color = if (active) cs.onSurface else cs.onSurfaceVariant
                        )
                        if (active && motion) {
                            Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(3.dp).background(cs.primary.copy(alpha = 0.15f), CircleShape)) {
                                Box(Modifier.fillMaxWidth(clamp01(lt / SCENE)).fillMaxHeight().background(cs.primary, CircleShape))
                            }
                        }
                    }
                }
            }
        }
    }
}

// ------------------------------------------------------------------ 画面

@Composable
private fun MiniPhone(width: Dp, content: @Composable BoxScope.() -> Unit) {
    val h = width * 2.0f
    Box(
        Modifier
            .size(width, h)
            .background(Color(0xFF151A22), RoundedCornerShape(22.dp))
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerLowest)
        ) {
            content()
            Box(Modifier.align(Alignment.TopCenter).padding(top = 5.dp).size(7.dp).background(Color(0xFF0B0E13), CircleShape))
        }
    }
}

/** 灰色占位文字条 */
@Composable
private fun Line(w: Float, color: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.22f), h: Dp = 5.dp) {
    Box(Modifier.fillMaxWidth(w).height(h).background(color, CircleShape))
}

/** 触点：白色圆点，按下时缩小并带一圈涟漪 */
@Composable
private fun BoxScope.Finger(x: Dp, y: Dp, show: Float, press: Float) {
    if (show <= 0.01f) return
    Box(
        Modifier
            .offset(x - 11.dp, y - 11.dp)
            .size(22.dp)
            .graphicsLayer { alpha = show; scaleX = 1f - 0.18f * press; scaleY = 1f - 0.18f * press }
            .background(Color.White.copy(alpha = 0.85f), CircleShape)
            .border(1.5.dp, Color.Black.copy(alpha = 0.12f), CircleShape)
    )
}

@Composable
private fun AppHeader(title: String, ai: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, top = 18.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(14.dp).background(if (ai) Color(0xFF4D6BFE) else cs.primary, CircleShape),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Rounded.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(9.dp)) }
        Spacer(Modifier.width(5.dp))
        Text(title, fontSize = 9.sp, fontWeight = FontWeight.SemiBold, color = cs.onSurface)
    }
}

/** 1：点「复制提示词」 */
@Composable
private fun SceneCopyPrompt(lt: Float) {
    val cs = MaterialTheme.colorScheme
    val copied = lt > 1.25f
    Box(Modifier.fillMaxSize()) {
        Column {
            AppHeader("AI 快速导入")
            Column(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().background(cs.surfaceContainerHigh, RoundedCornerShape(10.dp)).padding(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text("提示词", fontSize = 7.sp, color = cs.primary, fontWeight = FontWeight.SemiBold)
                Line(0.95f); Line(0.8f); Line(0.9f); Line(0.6f); Line(0.85f); Line(0.7f)
            }
            Spacer(Modifier.height(8.dp))
            Box(
                Modifier
                    .padding(horizontal = 8.dp)
                    .fillMaxWidth()
                    .height(24.dp)
                    .graphicsLayer { val s = 1f - 0.05f * pressCurve(lt, 1.05f); scaleX = s; scaleY = s }
                    .background(if (copied) cs.primaryContainer else cs.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, tint = if (copied) cs.onPrimaryContainer else cs.onPrimary, modifier = Modifier.size(10.dp))
                    Spacer(Modifier.width(3.dp))
                    Text(if (copied) "已复制" else "复制提示词", fontSize = 8.sp, color = if (copied) cs.onPrimaryContainer else cs.onPrimary, fontWeight = FontWeight.Medium)
                }
            }
        }
        // 底部提示条
        val toast = p(lt, 1.35f, 1.7f) * (1f - p(lt, 2.7f, 3.0f))
        if (toast > 0.01f) {
            Box(
                Modifier.align(Alignment.BottomCenter).padding(bottom = 12.dp)
                    .graphicsLayer { alpha = toast; translationY = (1f - toast) * 20f }
                    .background(Color(0xFF2E3135), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 5.dp)
            ) { Text("提示词已复制", fontSize = 7.sp, color = Color.White) }
        }
        Finger(x = 62.dp, y = 140.dp, show = p(lt, 0.4f, 0.8f) * (1f - p(lt, 1.6f, 1.9f)), press = pressCurve(lt, 1.05f))
    }
}

/** 2：在 AI 助手里发照片 + 提示词 */
@Composable
private fun SceneChatSend(lt: Float, timetable: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader("AI 助手", ai = true)
            Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(5.dp)) {
                val a = p(lt, 0.3f, 0.8f)
                Box(Modifier.graphicsLayer { alpha = a; translationY = (1f - a) * 24f }) { PhotoThumb(timetable) }
                val b = p(lt, 1.2f, 1.7f)
                Column(
                    Modifier.graphicsLayer { alpha = b; translationY = (1f - b) * 24f }
                        .width(84.dp).background(Color(0xFF4D6BFE), RoundedCornerShape(10.dp, 10.dp, 3.dp, 10.dp)).padding(7.dp),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    val w = Color.White.copy(alpha = 0.75f)
                    Line(0.95f, w, 4.dp); Line(0.8f, w, 4.dp); Line(0.9f, w, 4.dp); Line(0.5f, w, 4.dp)
                }
            }
        }
        // 输入栏
        Row(
            Modifier.align(Alignment.BottomCenter).padding(7.dp).fillMaxWidth().height(20.dp)
                .background(cs.surfaceContainerHigh, CircleShape).padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Image, null, tint = cs.onSurfaceVariant, modifier = Modifier.size(10.dp))
            Spacer(Modifier.width(4.dp))
            Text(if (lt < 1.2f) "粘贴提示词…" else "", fontSize = 7.sp, color = cs.onSurfaceVariant, modifier = Modifier.weight(1f))
            Box(Modifier.size(12.dp).background(Color(0xFF4D6BFE), CircleShape))
        }
        Finger(x = 105.dp, y = 239.dp, show = p(lt, 0.7f, 1.0f) * (1f - p(lt, 1.5f, 1.8f)), press = pressCurve(lt, 1.15f))
    }
}

/** 照片缩略图：课表 = 彩色格子；作业 = 黑板上的几行字 */
@Composable
private fun PhotoThumb(timetable: Boolean) {
    if (timetable) {
        val names = listOf("数学", "语文", "英语", "物理", "化学", "历史", "体育", "道法")
        Column(
            Modifier.size(70.dp, 52.dp).background(Color(0xFFF4F1EA), RoundedCornerShape(6.dp)).padding(4.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            repeat(4) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(5) { c -> Box(Modifier.weight(1f).height(9.dp).background(courseColor(names[(r * 3 + c * 2) % names.size]), RoundedCornerShape(2.dp))) }
                }
            }
        }
    } else {
        Column(
            Modifier.size(70.dp, 52.dp).background(Color(0xFF2F4A3A), RoundedCornerShape(6.dp)).padding(6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            val chalk = Color.White.copy(alpha = 0.7f)
            Line(0.8f, chalk, 3.dp); Line(0.6f, chalk, 3.dp); Line(0.9f, chalk, 3.dp); Line(0.5f, chalk, 3.dp); Line(0.7f, chalk, 3.dp)
        }
    }
}

/** 3：AI 回复一段代码，点「复制」 */
@Composable
private fun SceneChatReply(lt: Float) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            AppHeader("AI 助手", ai = true)
            Column(Modifier.fillMaxWidth().padding(horizontal = 7.dp), horizontalAlignment = Alignment.End) {
                Box(Modifier.width(60.dp).height(16.dp).background(Color(0xFF4D6BFE).copy(alpha = 0.35f), RoundedCornerShape(8.dp)))
            }
            Spacer(Modifier.height(6.dp))
            val a = p(lt, 0.15f, 0.5f)
            Column(
                Modifier.padding(horizontal = 7.dp).fillMaxWidth()
                    .graphicsLayer { alpha = a }
                    .background(Color(0xFF1E2430), RoundedCornerShape(8.dp)).padding(6.dp),
                verticalArrangement = Arrangement.spacedBy(2.5.dp)
            ) {
                val copied = lt > 2.0f
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("json", fontSize = 6.sp, color = Color.White.copy(alpha = 0.5f), fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    Row(
                        Modifier
                            .graphicsLayer { val s = 1f - 0.1f * pressCurve(lt, 1.85f); scaleX = s; scaleY = s }
                            .background(if (copied) Color(0xFF2BB894) else Color.White.copy(alpha = 0.14f), CircleShape)
                            .padding(horizontal = 4.dp, vertical = 1.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(if (copied) Icons.Rounded.Check else Icons.Rounded.ContentCopy, null, tint = Color.White, modifier = Modifier.size(7.dp))
                        Spacer(Modifier.width(2.dp))
                        Text(if (copied) "已复制" else "复制", fontSize = 6.sp, color = Color.White)
                    }
                }
                val code = listOf(0.4f to Color(0xFFE6E6E6), 0.85f to Color(0xFF9FCAFF), 0.7f to Color(0xFF9FE1CB), 0.9f to Color(0xFFF2C38B), 0.75f to Color(0xFF9FE1CB), 0.6f to Color(0xFF9FCAFF), 0.3f to Color(0xFFE6E6E6))
                code.forEachIndexed { i, (w, c) ->
                    val k = p(lt, 0.4f + i * 0.13f, 0.55f + i * 0.13f)
                    Box(Modifier.fillMaxWidth(w * k).height(3.5.dp).background(c.copy(alpha = 0.8f), CircleShape))
                }
            }
        }
        Finger(x = 98.dp, y = 70.dp, show = p(lt, 1.35f, 1.65f) * (1f - p(lt, 2.4f, 2.7f)), press = pressCurve(lt, 1.85f))
    }
}

/** 4：回到智能课表，粘贴 → 预览 → 完成 */
@Composable
private fun ScenePaste(lt: Float, timetable: Boolean) {
    val cs = MaterialTheme.colorScheme
    Box(Modifier.fillMaxSize()) {
        Column {
            AppHeader("AI 快速导入")
            val filled = lt > 0.55f
            Box(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(22.dp)
                    .border(1.dp, if (filled) cs.primary else cs.outline, RoundedCornerShape(6.dp)).padding(horizontal = 6.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (filled) Text("{ \"type\": \"smartschedule…", fontSize = 6.sp, color = cs.onSurface, fontFamily = FontFamily.Monospace, maxLines = 1)
                else Text("粘贴 AI 的回复", fontSize = 7.sp, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(7.dp))
            if (timetable) {
                val names = listOf("数学", "语文", "英语", "物理", "化学", "历史", "体育", "道法")
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    repeat(6) { r ->
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            repeat(5) { c ->
                                val k = p(lt, 0.8f + (r + c) * 0.07f, 1.05f + (r + c) * 0.07f)
                                Box(
                                    Modifier.weight(1f).height(13.dp)
                                        .graphicsLayer { scaleX = 0.5f + 0.5f * k; scaleY = 0.5f + 0.5f * k; alpha = k }
                                        .background(courseColor(names[(r * 3 + c * 2) % names.size]), RoundedCornerShape(3.dp))
                                )
                            }
                        }
                    }
                }
            } else {
                val subjects = listOf("数学", "语文", "英语", "物理")
                Column(Modifier.padding(horizontal = 8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    subjects.forEachIndexed { i, s ->
                        val k = p(lt, 0.8f + i * 0.15f, 1.1f + i * 0.15f)
                        Row(
                            Modifier.fillMaxWidth().graphicsLayer { alpha = k; translationX = (1f - k) * 30f }
                                .background(cs.surfaceContainerHigh, RoundedCornerShape(6.dp)).padding(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(Modifier.size(width = 16.dp, height = 9.dp).background(courseColor(s), RoundedCornerShape(3.dp)))
                            Spacer(Modifier.width(4.dp))
                            Line(0.7f)
                        }
                    }
                }
            }
            Spacer(Modifier.height(7.dp))
            val done = lt > 2.05f
            Box(
                Modifier.padding(horizontal = 8.dp).fillMaxWidth().height(20.dp)
                    .graphicsLayer { val s = 1f - 0.05f * pressCurve(lt, 1.9f); scaleX = s; scaleY = s }
                    .background(if (done) Color(0xFF2BB894) else cs.primary, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (done) { Icon(Icons.Rounded.Check, null, tint = Color.White, modifier = Modifier.size(9.dp)); Spacer(Modifier.width(2.dp)) }
                    Text(if (done) "导入完成" else "导入", fontSize = 8.sp, color = Color.White, fontWeight = FontWeight.Medium)
                }
            }
        }
        Finger(
            x = 62.dp,
            y = if (lt < 1.2f) 52.dp else (if (timetable) 174.dp else 160.dp),
            show = p(lt, 0.1f, 0.35f) * (1f - p(lt, 2.4f, 2.7f)),
            press = maxOf(pressCurve(lt, 0.45f), pressCurve(lt, 1.9f))
        )
    }
}

/** 在 [at] 附近 0 → 1 → 0 的按压曲线 */
private fun pressCurve(t: Float, at: Float): Float {
    val d = t - at
    return if (d < -0.12f || d > 0.22f) 0f else clamp01(1f - kotlin.math.abs((d - 0.02f) / 0.14f))
}
