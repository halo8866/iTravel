package com.itravel.app.map

import android.content.Context
import android.location.Geocoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import org.json.JSONObject

/**
 * 逆地理编码：将经纬度解析为简体中文地名，如 "杭州市 萧山区 盈丰街道"。
 * 优先使用系统 Geocoder，失败时回退到 BigDataCloud 免费接口（中文、无需 key，
 * 且在国内网络环境下可达，Nominatim 在国内不可用）。
 * 全部失败返回 null。
 */
suspend fun reverseGeocodeChinese(
    context: Context,
    latitude: Double,
    longitude: Double
): String? = withContext(Dispatchers.IO) {
    systemGeocoderChinese(context, latitude, longitude)
        ?: bigDataCloudChinese(latitude, longitude)
}

private fun systemGeocoderChinese(context: Context, latitude: Double, longitude: Double): String? {
    if (!Geocoder.isPresent()) return null
    return runCatching {
        @Suppress("DEPRECATION")
        Geocoder(context, Locale.SIMPLIFIED_CHINESE)
            .getFromLocation(latitude, longitude, 1)
            ?.firstOrNull()
            ?.let { addr ->
                listOfNotNull(
                    addr.locality,        // 城市
                    addr.subLocality,     // 区县
                    addr.featureName      // 地标 / 兴趣点
                )
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(" ")
                    .ifBlank { null }
            }
    }.getOrNull()
}

/** BigDataCloud 免费 reverse-geocode-client：无需 key，localityLanguage=zh 返回中文。 */
private fun bigDataCloudChinese(latitude: Double, longitude: Double): String? {
    var conn: HttpURLConnection? = null
    return runCatching {
        val url = URL(
            "https://api.bigdatacloud.net/data/reverse-geocode-client" +
                "?latitude=$latitude&longitude=$longitude&localityLanguage=zh"
        )
        conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 6000
            readTimeout = 6000
            requestMethod = "GET"
        }
        val body = conn?.inputStream?.bufferedReader()?.use { it.readText() } ?: return null
        val root = JSONObject(body)
        val city = root.optString("city").takeIf { it.isNotBlank() }
            ?: root.optString("principalSubdivision").takeIf { it.isNotBlank() }
        val district = root.optString("locality").takeIf { it.isNotBlank() }
        // 最细一级行政区（街道/乡镇，adminLevel >= 8）
        val subdistrict = root.optJSONObject("localityInfo")
            ?.optJSONArray("administrative")
            ?.let { arr ->
                var best: String? = null
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    if (o.optInt("adminLevel", 0) >= 8) {
                        o.optString("name").takeIf { it.isNotBlank() }?.let { best = it }
                    }
                }
                best
            }
        // 景点 / 地标：informative 中 order >= 8 的条目（跳过国名、时区、洲名）
        val poi = root.optJSONObject("localityInfo")
            ?.optJSONArray("informative")
            ?.let { arr ->
                var best: String? = null
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val order = o.optInt("order", 0)
                    val name = o.optString("name")
                    if (order >= 8 && name.isNotBlank() && !name.contains("/")) {
                        best = name
                    }
                }
                best
            }
        // 景点优先展示，其次城市 + 区县；街道仅在没有景点时补充
        if (poi != null) {
            listOfNotNull(city, poi).distinct().joinToString(" ")
        } else {
            listOfNotNull(city, district, subdistrict)
                .distinct()
                .joinToString(" ")
                .ifBlank { null }
        }
    }.getOrNull().also { conn?.disconnect() }
}
