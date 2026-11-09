package com.enenhhh.smartschedule.weather

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import android.os.Looper
import android.os.SystemClock
import androidx.core.content.ContextCompat
import com.enenhhh.smartschedule.legal.LegalConsent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import kotlin.coroutines.resume
import kotlin.math.round

/** No SDK / API key. One foreground, coarse fix; never starts a service or background tracking. */
object CityLocator {
    const val ENDPOINT = "https://api.bigdatacloud.net/data/reverse-geocode-client"
    data class City(val name: String, val region: String, val country: String, val latitude: Double, val longitude: Double) {
        val label: String get() = listOf(name, region, country).filter { it.isNotBlank() }.distinct().joinToString(" · ")
    }

    suspend fun locate(context: Context): City {
        check(LegalConsent.isAccepted(context)) { "请先阅读并同意用户须知" }
        check(ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) { "未授权位置，请手动设置城市" }
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val location = try {
            withTimeout(18_000) { currentLocation(manager) }
        } catch (e: TimeoutCancellationException) {
            throw IllegalStateException("定位超时，请打开系统定位并稍后重试，或手动输入城市")
        }
        // City-level precision. Never transmit the original fix or a device identifier.
        val lat = round(location.latitude * 50.0) / 50.0
        val lon = round(location.longitude * 50.0) / 50.0
        return withContext(Dispatchers.IO) { reverse(lat, lon) }
    }

    @Suppress("MissingPermission")
    private suspend fun currentLocation(manager: LocationManager): Location = suspendCancellableCoroutine { continuation ->
        // GPS is deliberately excluded: the application only requests COARSE_LOCATION.
        val providers = manager.getProviders(true).filter { it != LocationManager.GPS_PROVIDER }
        if (LocationManager.NETWORK_PROVIDER !in providers) {
            continuation.resumeWith(Result.failure(IllegalStateException("网络定位不可用，请开启系统定位与网络定位服务，或手动选择城市")))
            return@suspendCancellableCoroutine
        }
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                runCatching { manager.removeUpdates(this) }
                if (continuation.isActive) continuation.resume(location)
            }
            override fun onProviderDisabled(provider: String) {
                runCatching { manager.removeUpdates(this) }
                if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("定位服务已关闭，请手动设置城市")))
            }
            override fun onProviderEnabled(provider: String) = Unit
            @Deprecated("Deprecated by platform")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
        }
        continuation.invokeOnCancellation { runCatching { manager.removeUpdates(listener) } }
        try {
            manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0L, 0f, listener, Looper.getMainLooper())
        } catch (e: Exception) {
            runCatching { manager.removeUpdates(listener) }
            if (continuation.isActive) continuation.resumeWith(Result.failure(e))
        }
    }

    private fun reverse(latitude: Double, longitude: Double): City {
        val conn = URL("$ENDPOINT?latitude=$latitude&longitude=$longitude&localityLanguage=zh").openConnection() as HttpsURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 12_000
        conn.requestMethod = "GET"
        conn.instanceFollowRedirects = false
        conn.setRequestProperty("User-Agent", "SmartSchedule/5.2 (Android)")
        return try {
            val code = conn.responseCode
            if (code == 429) throw IllegalStateException("定位请求较多，请稍后重试或手动输入城市")
            if (code !in 200..299) throw IllegalStateException("城市服务暂不可用（$code），可手动输入城市")
            val bytes = conn.inputStream.use { it.readBytesLimited(16 * 1024) }
            val json = JSONObject(String(bytes, Charsets.UTF_8))
            val city = json.optString("city").ifBlank { json.optString("locality") }.ifBlank { json.optString("principalSubdivision") }.trim()
            check(city.isNotBlank() && city.length <= 120 && !json.has("error")) { "未能识别城市，请手动输入" }
            City(city, json.optString("principalSubdivision"), json.optString("countryName"), latitude, longitude)
        } finally { conn.disconnect() }
    }

    private fun java.io.InputStream.readBytesLimited(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(1024)
        while (true) {
            val n = read(buffer)
            if (n < 0) break
            check(out.size() + n <= max) { "城市服务返回异常，请稍后重试" }
            out.write(buffer, 0, n)
        }
        return out.toByteArray()
    }
}

/** Stored separately and excluded from backups and shares. Only rounded coordinates. */
object WeatherLocation {
    private fun prefs(c: Context) = c.getSharedPreferences("weather_location", Context.MODE_PRIVATE)
    fun save(c: Context, city: CityLocator.City) = prefs(c).edit()
        .putString("city", city.name).putString("latitude", city.latitude.toString())
        .putString("longitude", city.longitude.toString()).putLong("updated", System.currentTimeMillis()).apply()
    fun clear(c: Context) = prefs(c).edit().clear().apply()
    fun coordinates(c: Context, city: String): Pair<Double, Double>? {
        val p = prefs(c)
        if (p.getString("city", "") != city) return null
        val lat = p.getString("latitude", null)?.toDoubleOrNull() ?: return null
        val lon = p.getString("longitude", null)?.toDoubleOrNull() ?: return null
        return if (lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
    }
}
