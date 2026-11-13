package com.team.cardoctor.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Real repair shops near the car from OpenStreetMap (Overpass API, no key). NaviHelper has no POI
 * search, so we find coordinates here and hand them to NaviHelper as a route destination.
 * Returns an empty list on any failure; the caller falls back to [RepairShops] samples.
 */
object ShopSearch {

    private const val ENDPOINT = "https://overpass-api.de/api/interpreter"
    const val RADIUS_M = 5000

    suspend fun near(lat: Double, lng: Double): List<RepairShop> = withContext(Dispatchers.IO) {
        runCatching {
            val query = """
                [out:json][timeout:10];
                (
                  node(around:$RADIUS_M,$lat,$lng)[shop~"^(car_repair|tyres)$"];
                  way(around:$RADIUS_M,$lat,$lng)[shop~"^(car_repair|tyres)$"];
                );
                out center 30;
            """.trimIndent()
            val conn = (URL(ENDPOINT).openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 6000
                readTimeout = 12000
                setRequestProperty("User-Agent", "CarDoctorApp/0.1")
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
            }
            try {
                conn.outputStream.use { it.write(("data=" + URLEncoder.encode(query, "UTF-8")).toByteArray()) }
                parse(conn.inputStream.bufferedReader().readText(), lat, lng)
            } finally {
                conn.disconnect()
            }
        }.onFailure { Log.w("ShopSearch", "overpass: ${it.message}") }.getOrDefault(emptyList())
    }

    /** Overpass JSON -> shops sorted by distance. Unnamed shops are skipped (a driver cannot pick them). */
    fun parse(json: String, lat: Double, lng: Double): List<RepairShop> {
        val elements = JSONObject(json).optJSONArray("elements") ?: return emptyList()
        return (0 until elements.length()).mapNotNull { i ->
            val e = elements.getJSONObject(i)
            val tags = e.optJSONObject("tags") ?: return@mapNotNull null
            val name = tags.optString("name").ifBlank { return@mapNotNull null }
            val center = e.optJSONObject("center")
            val sLat = if (e.has("lat")) e.getDouble("lat") else center?.optDouble("lat") ?: return@mapNotNull null
            val sLng = if (e.has("lon")) e.getDouble("lon") else center?.optDouble("lon") ?: return@mapNotNull null
            val address = listOf("addr:city", "addr:district", "addr:street", "addr:housenumber")
                .mapNotNull { k -> tags.optString(k).takeIf { it.isNotBlank() } }
                .joinToString(" ")
            val handles = if (tags.optString("shop") == "tyres") setOf(Category.TIRE) else Category.entries.toSet()
            RepairShop(
                id = "osm-${e.optString("type")}-${e.optLong("id")}",
                name = name,
                address = address.ifBlank { "주소 정보 없음" },
                lat = sLat,
                lng = sLng,
                distanceKm = RepairShops.haversineKm(lat, lng, sLat, sLng),
                handles = handles,
            )
        }.distinctBy { it.id }.sortedBy { it.distanceKm }
    }
}
