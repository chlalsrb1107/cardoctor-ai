package com.team.cardoctor.data

import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.model.LocationData
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.util.Log
import com.team.cardoctor.engine.Daylight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime

data class Environment(
    val lat: Double = DEFAULT_LAT,
    val lng: Double = DEFAULT_LNG,
    val hasFix: Boolean = false,
    val speedKmh: Int = 0,
    val weather: Weather = Weather.CLEAR,
    val weatherFetched: Boolean = false,
    /** Which location provider is feeding positions: "FusedLocation", "Android" or null (none yet). */
    val source: String? = null,
) {
    fun isNight(now: ZonedDateTime = ZonedDateTime.now()): Boolean = Daylight.isNight(now, lat, lng)

    companion object {
        // Seoul City Hall: used until the first location fix (emulator default is often 0,0 or Mountain View).
        const val DEFAULT_LAT = 37.5665
        const val DEFAULT_LNG = 126.9780
    }
}

/**
 * Position from the Pleos FusedLocation SDK (vehicle-fused position), falling back to Android
 * LocationManager where FusedLocation is not supported. Weather from Open-Meteo (no API key),
 * day/night computed locally from sunrise/sunset.
 */
class EnvironmentSource(context: Context) {

    private val appContext = context.applicationContext
    private val lm = appContext.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    private val _state = MutableStateFlow(Environment())
    val state: StateFlow<Environment> = _state.asStateFlow()

    private val listener = LocationListener { onLocation(it) }

    private val fused = runCatching { FusedLocation(appContext) }.getOrNull()
    private var fusedActive = false
    private var lastFix: LocationData? = null
    private val fusedListener = object : FusedLocationListener {
        override fun onChanged(data: LocationData?) { data?.let { onFused(it) } }
        override fun onError(e: Exception) { Log.w(TAG, "fused: ${e.message}") }
    }

    fun start() {
        if (fusedActive || startFused()) return
        startAndroidLocation()
    }

    /** @return true when the Pleos FusedLocation service is supported and now feeding positions. */
    private fun startFused(): Boolean {
        val f = fused ?: return false
        return try {
            if (!f.isSupported()) return false
            f.initialize()
            f.getLastKnownLocation(fusedListener)
            f.registerFusedLocationCallback(fusedListener)
            fusedActive = true
            _state.update { it.copy(source = "FusedLocation") }
            true
        } catch (e: Exception) {
            Log.w(TAG, "FusedLocation unavailable: ${e.message}")
            false
        }
    }

    /** FusedLocation has no speed field, so derive it from consecutive fixes. */
    private fun onFused(d: LocationData) {
        if (d.latitude == 0.0 && d.longitude == 0.0) return
        val prev = lastFix
        lastFix = d
        val speed = if (prev != null && d.timestampMillis > prev.timestampMillis) {
            val km = RepairShops.haversineKm(prev.latitude, prev.longitude, d.latitude, d.longitude)
            val hours = (d.timestampMillis - prev.timestampMillis) / 3_600_000.0
            (km / hours).toInt().coerceIn(0, 250)
        } else _state.value.speedKmh
        _state.update { it.copy(lat = d.latitude, lng = d.longitude, hasFix = true, speedKmh = speed) }
    }

    @SuppressLint("MissingPermission")
    private fun startAndroidLocation() {
        if (appContext.checkSelfPermission(android.Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            appContext.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED
        ) return
        for (provider in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
            try {
                if (!lm.isProviderEnabled(provider)) continue
                lm.getLastKnownLocation(provider)?.let { onLocation(it) }
                lm.requestLocationUpdates(provider, 2_000L, 5f, listener, Looper.getMainLooper())
                _state.update { it.copy(source = "Android") }
            } catch (e: Exception) {
                Log.w(TAG, "location $provider: ${e.message}")
            }
        }
    }

    fun stop() {
        runCatching { lm.removeUpdates(listener) }
        if (fusedActive) runCatching {
            fused?.unregisterFusedLocationCallback(fusedListener)
            fused?.release()
        }
        fusedActive = false
    }

    private fun onLocation(loc: Location) {
        // Ignore the emulator's 0,0 placeholder.
        if (loc.latitude == 0.0 && loc.longitude == 0.0) return
        _state.update {
            it.copy(lat = loc.latitude, lng = loc.longitude, hasFix = true, speedKmh = if (loc.hasSpeed()) (loc.speed * 3.6f).toInt() else 0)
        }
    }

    /** Fetch current weather once; call again on resume or every ~10 minutes. */
    suspend fun refreshWeather() {
        val env = _state.value
        val w = withContext(Dispatchers.IO) {
            runCatching {
                val url = URL("https://api.open-meteo.com/v1/forecast?latitude=${env.lat}&longitude=${env.lng}&current=weather_code")
                val conn = (url.openConnection() as HttpURLConnection).apply { connectTimeout = 5000; readTimeout = 5000 }
                try {
                    val code = JSONObject(conn.inputStream.bufferedReader().readText()).getJSONObject("current").getInt("weather_code")
                    weatherFromWmo(code)
                } finally {
                    conn.disconnect()
                }
            }.onFailure { Log.w(TAG, "weather: ${it.message}") }.getOrNull()
        } ?: return
        _state.update { it.copy(weather = w, weatherFetched = true) }
    }

    private companion object {
        const val TAG = "EnvironmentSource"

        /** WMO weather interpretation codes used by Open-Meteo. */
        fun weatherFromWmo(code: Int): Weather = when (code) {
            0, 1 -> Weather.CLEAR
            2, 3 -> Weather.CLOUDY
            45, 48 -> Weather.FOG
            in 51..67, in 80..82, in 95..99 -> Weather.RAIN
            in 71..77, 85, 86 -> Weather.SNOW
            else -> Weather.CLOUDY
        }
    }
}
