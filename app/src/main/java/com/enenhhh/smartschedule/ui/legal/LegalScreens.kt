package com.enenhhh.smartschedule.ui.legal

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.PrivacyTip
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.legal.*
import com.enenhhh.smartschedule.ui.components.SubPage
import com.enenhhh.smartschedule.ui.glass.MeshBackground

/** Mandatory entry gate, before the navigator and any network work are created. */
@Composable
fun LegalConsentScreen(onAccepted: () -> Unit) {
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme
    var step by rememberSaveable { mutableIntStateOf(0) }
    var firstRead by rememberSaveable { mutableStateOf(false) }
    var secondRead by rememberSaveable { mutableStateOf(false) }
    var firstChecked by rememberSaveable { mutableStateOf(false) }
    var secondChecked by rememberSaveable { mutableStateOf(false) }
    val read = if (step == 0) firstRead else secondRead
    val checked = if (step == 0) firstChecked else secondChecked
    val doc = if (step == 0) LegalContent.disclaimer else LegalContent.notice
    BackHandler(enabled = step > 0) { step = 0 }

    Box(Modifier.fillMaxSize()) {
        MeshBackground(Modifier.fillMaxSize())
        Column(Modifier.fillMaxSize().safeDrawingPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Rounded.PrivacyTip, null, tint = cs.primary)
                Text("开始之前", style = MaterialTheme.typography.labelLarge, color = cs.primary, modifier = Modifier.weight(1f))
                Text("${step + 1} / 2", style = MaterialTheme.typography.labelLarge, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(12.dp))
            Text(doc.title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.SemiBold)
            Text("请阅读完整内容，滚动到底后勾选确认。", style = MaterialTheme.typography.bodyMedium, color = cs.onSurfaceVariant)
            Spacer(Modifier.height(12.dp))
            // key() prevents the first document's scroll position from unlocking the second.
            key(doc.id) {
                LegalReader(doc, Modifier.weight(1f).fillMaxWidth()) {
                    if (step == 0) firstRead = true else secondRead = true
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.medium)
                    .toggleable(value = checked, enabled = read, role = Role.Checkbox, onValueChange = {
                        if (step == 0) firstChecked = it else secondChecked = it
                    }).testTag("legal-checkbox").padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(checked = checked, onCheckedChange = null, enabled = read)
                Text(if (read) "我已阅读并同意《${doc.title}》" else "请先滚动至全文末尾",
                    color = if (read) cs.onSurface else cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (step > 0) OutlinedButton(onClick = { step = 0 }) { Text("上一步") }
                Button(
                    enabled = ReadingGate.canContinue(read, checked),
                    modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("legal-continue"),
                    onClick = {
                        if (step == 0) step = 1
                        else if (ReadingGate.canContinue(firstRead, firstChecked) && ReadingGate.canContinue(secondRead, secondChecked)) {
                            LegalConsent.accept(context)
                            onAccepted()
                        }
                    }
                ) { Text(if (step == 0) "下一步 · 用户须知" else "同意并继续") }
            }
            Text("不同意可直接退出。系统权限将在使用相关功能时另行申请。",
                style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp))
        }
    }
}

@Composable
fun LegalReader(document: LegalDocument, modifier: Modifier = Modifier, onReachedEnd: () -> Unit = {}) {
    val scroll = rememberScrollState()
    val callback by rememberUpdatedState(onReachedEnd)
    val cs = MaterialTheme.colorScheme
    LaunchedEffect(scroll, document.id) {
        snapshotFlow { Triple(scroll.value, scroll.maxValue, scroll.viewportSize) }.collect { (v, max, viewport) ->
            if (ReadingGate.reachedEnd(v, max, viewport)) callback()
        }
    }
    Column(modifier.clip(MaterialTheme.shapes.large).background(cs.surfaceContainerLow)
        .verticalScroll(scroll).testTag("legal-reader").padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)) {
        Text("更新于 ${LegalContent.DATE} · ${LegalContent.REVISION}", style = MaterialTheme.typography.labelSmall, color = cs.primary)
        Text(document.summary, style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
        document.sections.forEach { section ->
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(section.title, style = MaterialTheme.typography.titleMedium, color = cs.onSurface, fontWeight = FontWeight.SemiBold)
                Text(section.body, style = MaterialTheme.typography.bodyLarge, color = cs.onSurfaceVariant)
            }
        }
        HorizontalDivider()
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(20.dp))
            Text("已到达全文末尾", style = MaterialTheme.typography.labelLarge, color = cs.primary)
        }
    }
}

@Composable
fun LegalDocumentScreen(id: String) {
    val doc = LegalContent.document(id)
    SubPage(title = doc.title, subtitle = "可随时重新阅读 · ${LegalContent.DATE}") { padding ->
        LegalReader(doc, Modifier.fillMaxSize().padding(padding))
    }
}

@Composable
fun PermissionsScreen() {
    // Same source text as first-run consent; never maintain a divergent permission table.
    val doc = LegalContent.notice.copy(
        id = "permissions",
        title = "权限与数据说明",
        summary = "联网、位置、通知、闹钟、保活、安装与文件访问：各自只用于相应功能。",
        sections = LegalContent.notice.sections.drop(1).take(7)
    )
    SubPage(title = doc.title, subtitle = "位置可拒绝 · 无后台定位") { padding ->
        LegalReader(doc, Modifier.fillMaxSize().padding(padding))
    }
}
