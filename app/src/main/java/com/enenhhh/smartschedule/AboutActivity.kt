package com.enenhhh.smartschedule

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.enenhhh.smartschedule.ui.Text
import com.enenhhh.smartschedule.ui.components.AppTopBar
import com.enenhhh.smartschedule.ui.components.BrandTitle
import com.enenhhh.smartschedule.ui.theme.AppTheme
import com.enenhhh.smartschedule.util.FontManager
import com.enenhhh.smartschedule.util.UpdateChecker

class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        FontManager.init(this)

        setContent {
            AppTheme {
                AboutScreen(
                    onBack = { finish() },
                    onCheckUpdate = { UpdateChecker.checkAndPrompt(this, manual = true) }
                )
            }
        }
    }
}

@Composable
private fun AboutScreen(
    onBack: () -> Unit,
    onCheckUpdate: () -> Unit
) {
    val context = LocalContext.current
    val versionName = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "未知"

    fun openUrl(url: String) {
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }.onFailure {
            Toast.makeText(context, "没有可以打开链接的应用", Toast.LENGTH_SHORT).show()
        }
    }

    Scaffold(
        topBar = {
            AppTopBar(
                title = "关于",
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(R.drawable.ic_app_logo),
                contentDescription = "智能课表",
                modifier = Modifier
                    .size(96.dp)
                    .clip(RoundedCornerShape(20.dp))
            )

            Spacer(Modifier.height(16.dp))

            BrandTitle(
                titleSize = 28.sp,
                subtitleSize = 14.sp,
                horizontalAlignment = Alignment.CenterHorizontally
            )

            Spacer(Modifier.height(8.dp))

            Text(
                text = "版本 $versionName",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(Modifier.height(32.dp))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text("开发者", style = MaterialTheme.typography.titleMedium)
                    Spacer(Modifier.height(12.dp))

                    InfoRow(label = "作者", value = "恩恩hhh")
                    InfoRow(label = "B站", value = "恩恩hhh") { openUrl("https://b23.tv/FZ4S1Ls") }
                    InfoRow(label = "GitHub", value = "smartschedule") {
                        openUrl("https://github.com/enenhhh/smartschedule")
                    }
                    InfoRow(label = "QQ", value = "6944341")
                    InfoRow(label = "邮箱", value = "enenhhh@enhub.top")
                }
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = onCheckUpdate, modifier = Modifier.fillMaxWidth()) {
                Text("检查更新")
            }

            Spacer(Modifier.height(24.dp))

            Text(
                text = "感谢使用智能课表",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun InfoRow(
    label: String,
    value: String,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        if (onClick != null) {
            TextButton(onClick = onClick) { Text(value) }
        } else {
            Text(text = value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
