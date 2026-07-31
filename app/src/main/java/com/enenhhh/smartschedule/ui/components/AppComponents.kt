package com.enenhhh.smartschedule.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.theme.BrandGradientEnd
import com.enenhhh.smartschedule.ui.theme.BrandGradientStart

/**
 * 通用弹窗：标题 + 可选副标题 + 自定义内容。
 * 内容区自带滚动，长列表也不会顶出屏幕。
 */
@Composable
fun AppDialog(
    title: String,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    summary: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = modifier
                .fillMaxWidth(0.92f)
                .padding(vertical = 24.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(24.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (!summary.isNullOrBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = summary,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.height(16.dp))
                content()
            }
        }
    }
}

/** 顶栏：标题为纯文本的常规页面用这个 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppTopBar(
    title: String,
    modifier: Modifier = Modifier,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Text(
                text = title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.titleLarge
            )
        },
        modifier = modifier,
        navigationIcon = navigationIcon,
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = Color.Transparent,
            scrolledContainerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
            navigationIconContentColor = MaterialTheme.colorScheme.onSurface,
            actionIconContentColor = MaterialTheme.colorScheme.onSurface
        )
    )
}

/** 带字符串 label 的输入框，替代原先 Miuix 的 TextField */
@Composable
fun AppTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small
    )
}

@Composable
fun AppTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    enabled: Boolean = true,
    isError: Boolean = false,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        singleLine = singleLine,
        enabled = enabled,
        isError = isError,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        shape = MaterialTheme.shapes.small
    )
}

/**
 * 「智能课表 / Smart Schedule」品牌标题，首页、关于页、引导页共用。
 */
@Composable
fun BrandTitle(
    modifier: Modifier = Modifier,
    titleSize: TextUnit = 28.sp,
    subtitleSize: TextUnit = 14.sp,
    horizontalAlignment: Alignment.Horizontal = Alignment.Start,
    suffix: String = ""
) {
    val gradient = Brush.linearGradient(
        colors = listOf(BrandGradientStart, BrandGradientEnd),
        start = Offset.Zero,
        end = Offset(200f, 0f)
    )
    val onSurface = MaterialTheme.colorScheme.onSurface

    Column(
        modifier = modifier,
        horizontalAlignment = horizontalAlignment,
        verticalArrangement = Arrangement.Top
    ) {
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(brush = gradient, fontWeight = FontWeight.Bold)) { append("智能") }
                withStyle(SpanStyle(color = onSurface)) { append("课表") }
                if (suffix.isNotEmpty()) {
                    withStyle(SpanStyle(color = onSurface)) { append(suffix) }
                }
            },
            fontSize = titleSize
        )
        Text(
            text = buildAnnotatedString {
                withStyle(SpanStyle(brush = gradient, fontWeight = FontWeight.Medium)) { append("Smart") }
                withStyle(SpanStyle(color = onSurface.copy(alpha = 0.6f))) { append(" Schedule") }
            },
            fontSize = subtitleSize
        )
    }
}
