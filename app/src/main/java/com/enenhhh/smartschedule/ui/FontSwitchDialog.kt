package com.enenhhh.smartschedule.ui

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.enenhhh.smartschedule.ui.components.AppDialog
import com.enenhhh.smartschedule.util.FontManager

@Composable
fun FontSwitchDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            val ok = FontManager.useCustomFontFromUri(context, uri)
            Toast.makeText(
                context,
                if (ok) "字体已应用" else "字体文件读取失败",
                Toast.LENGTH_SHORT
            ).show()
            if (ok) onDismiss()
        }
    }

    AppDialog(
        title = "字体设置",
        summary = "可以选择本机的 ttf / otf 字体文件",
        onDismissRequest = onDismiss
    ) {
        Column {
            FontOption(
                label = "系统默认",
                selected = FontManager.currentMode() == 0
            ) {
                FontManager.useSystemDefault()
                onDismiss()
            }

            FontOption(
                label = "自定义字体",
                selected = FontManager.currentMode() == 1
            ) {
                picker.launch(
                    arrayOf(
                        "font/ttf",
                        "font/otf",
                        "application/x-font-ttf",
                        "application/x-font-otf",
                        "application/octet-stream"
                    )
                )
            }

            Spacer(Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDismiss) { Text("关闭") }
            }
        }
    }
}

@Composable
private fun FontOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
