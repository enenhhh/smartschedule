package com.enenhhh.smartschedule.weather

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

object WeatherFetcher {

    private fun httpGet(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8000
        conn.readTimeout = 8000
        conn.requestMethod = "GET"
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            stream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun getCoordinatesForCity(city: String): Pair<Double, Double>? {
        return try {
            // 使用Open-Meteo的地理编码API
            val encodedCity = URLEncoder.encode(city, "UTF-8")
            val url = "https://geocoding-api.open-meteo.com/v1/search?name=$encodedCity&count=1&language=zh&format=json"
            
            val json = JSONObject(httpGet(url))
            val results = json.optJSONArray("results")
            
            if (results != null && results.length() > 0) {
                val location = results.getJSONObject(0)
                val latitude = location.getDouble("latitude")
                val longitude = location.getDouble("longitude")
                Pair(latitude, longitude)
            } else {
                // 如果没找到，使用北京的坐标作为默认值
                Log.w("WeatherFetcher", "未找到城市: $city，使用默认坐标")
                Pair(39.9042, 116.4074)
            }
        } catch (e: Exception) {
            Log.e("WeatherFetcher", "获取城市坐标失败: ${e.message}", e)
            // 失败时使用北京的坐标作为默认值
            Pair(39.9042, 116.4074)
        }
    }

    fun fetchTomorrowSummary(ctx: Context): String {
        val city = ctx.getSharedPreferences("prefs", Context.MODE_PRIVATE)
            .getString("weather_city", "北京") ?: "北京"

        val coordinates = getCoordinatesForCity(city)
            ?: return "获取失败（城市不支持：$city）"

        val (latitude, longitude) = coordinates
        
        // 获取明天日期
        val cal = Calendar.getInstance()
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        
        // Open-Meteo API 请求
        val url = "https://api.open-meteo.com/v1/forecast?" +
                "latitude=$latitude&longitude=$longitude&" +
                "daily=temperature_2m_max,temperature_2m_min,weathercode,precipitation_probability_max&" +
                "timezone=auto&start_date=$dateStr&end_date=$dateStr"

        return try {
            val json = JSONObject(httpGet(url))
            val daily = json.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            if (dates.length() == 0) {
                return "暂无天气数据"
            }
            
            val tempMax = daily.getJSONArray("temperature_2m_max").getDouble(0).toInt()
            val tempMin = daily.getJSONArray("temperature_2m_min").getDouble(0).toInt()
            val weatherCode = daily.getJSONArray("weathercode").getInt(0)
            val precipitationProb = daily.getJSONArray("precipitation_probability_max").getInt(0)
            
            val weatherText = getWeatherText(weatherCode)
            val precipitationText = if (precipitationProb > 0) "，降水概率$precipitationProb%" else ""
            
            "$weatherText，${tempMin}°/${tempMax}°$precipitationText"
            
        } catch (e: Exception) {
            Log.e("WeatherFetcher", "获取天气失败", e)
            "获取天气失败"
        }
    }

    private fun getWeatherText(weatherCode: Int): String {
        return when (weatherCode) {
            0 -> "晴"
            1 -> "晴间多云"
            2 -> "多云"
            3 -> "阴天"
            45 -> "雾"
            48 -> "冻雾"
            51 -> "小雨"
            53 -> "中雨"
            55 -> "大雨"
            56 -> "冻雨"
            57 -> "强冻雨"
            61 -> "小雨"
            63 -> "中雨"
            65 -> "大雨"
            66 -> "冻雨"
            67 -> "强冻雨"
            71 -> "小雪"
            73 -> "中雪"
            75 -> "大雪"
            77 -> "冰雹"
            80 -> "阵雨"
            81 -> "强阵雨"
            82 -> "暴雨"
            85 -> "阵雪"
            86 -> "强阵雪"
            95 -> "雷雨"
            96 -> "雷暴"
            99 -> "强雷暴"
            else -> "未知"
        }
    }
}
