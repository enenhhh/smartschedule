package com.enenhhh.smartschedule.ui.settings

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.enenhhh.smartschedule.ui.app.LocalSnackbar
import com.enenhhh.smartschedule.ui.components.GlassDialog
import com.enenhhh.smartschedule.ui.components.SettingRow
import com.enenhhh.smartschedule.util.Prefs
import com.enenhhh.smartschedule.weather.CityLocator
import com.enenhhh.smartschedule.weather.WeatherLocation
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun LocationCityRow(onCityChanged: (String) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = LocalSnackbar.current
    var explanation by remember { mutableStateOf(false) }
    var locating by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<CityLocator.City?>(null) }
    var denied by remember { mutableStateOf(false) }
    var failure by remember { mutableStateOf<String?>(null) }

    fun locate() {
        if (locating) return
        locating = true
        failure = null
        scope.launch {
            try { result = CityLocator.locate(context) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { failure = e.message ?: "定位失败，请手动选择城市" }
            finally { locating = false }
        }
    }
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        denied = !granted
        if (granted) locate() else failure = "未获得位置权限。你可以继续手动输入城市，或在系统设置中授权大致位置。"
    }
    SettingRow(
        title = if (locating) "正在获取当前城市…" else "定位当前城市",
        subtitle = "仅本次获取大致位置 · 确认后才使用",
        icon = Icons.Rounded.MyLocation,
        onClick = if (locating) null else ({ explanation = true }),
        showChevron = !locating,
        trailing = if (locating) ({ CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp) }) else null
    )
    if (explanation) GlassDialog(
        onDismiss = { explanation = false }, title = "使用一次大致位置？", confirmText = "继续", dismissText = "手动输入",
        onConfirm = {
            explanation = false
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) locate()
            else permission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    ) {
        Text("只在此刻定位，不在后台追踪，也不需要精确位置权限。位置会降低精度后，由本机直接发送给 BigDataCloud 城市接口识别城市。")
        Spacer(Modifier.height(12.dp))
        Text("该服务可能使用位置与来源 IP 的匿名配对改善其 IP 定位服务。结果需要你再次确认；拒绝不影响课表或手动选择城市。", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    result?.let { city ->
        GlassDialog(onDismiss = { result = null }, title = "使用这个城市？", confirmText = "使用 ${city.name}", onConfirm = {
            Prefs.setWeatherCity(context, city.name)
            WeatherLocation.save(context, city)
            onCityChanged(city.name)
            result = null
            scope.launch { snackbar.showSnackbar("天气城市已设为 ${city.name}") }
        }) {
            Text(city.label, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))
            Text("识别可能有误，特别是行政区边界附近。若不正确，请取消并手动输入。仅在本机保存城市及降低精度后的天气查询坐标。", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(8.dp))
            Text("城市识别 · BigDataCloud", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
    }
    failure?.let { message ->
        GlassDialog(onDismiss = { failure = null }, title = "暂时无法定位", confirmText = "知道了", onConfirm = { failure = null }, dismissText = null) {
            Text(message)
            TextButton(onClick = {
                runCatching {
                    context.startActivity(if (denied) Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")) else Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
                }
            }) { Text(if (denied) "打开应用权限设置" else "打开系统定位设置") }
            Text("也可以点上方「城市」手动输入。", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
