package com.team.cardoctor.engine

import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin
import kotlin.math.tan

/** Sunrise/sunset from coordinates (NOAA simplified algorithm, about ±2 min). No network needed. */
object Daylight {

    data class SunTimes(val sunrise: ZonedDateTime, val sunset: ZonedDateTime)

    fun isNight(now: ZonedDateTime, lat: Double, lon: Double): Boolean {
        val t = sunTimes(now.toLocalDate(), lat, lon, now.zone) ?: return false
        return now.isBefore(t.sunrise) || now.isAfter(t.sunset)
    }

    /** Returns null for polar day/night. */
    fun sunTimes(date: LocalDate, lat: Double, lon: Double, zone: ZoneId): SunTimes? {
        val rise = utcEventMinutes(date, lat, lon, rising = true) ?: return null
        val set = utcEventMinutes(date, lat, lon, rising = false) ?: return null
        val midnightUtc = date.atStartOfDay(ZoneId.of("UTC"))
        // The UTC event can fall on the previous/next UTC day (e.g. Seoul sunrise is ~22:00 UTC the day before),
        // so pin each event onto the requested local date.
        fun onLocalDate(minutes: Double): ZonedDateTime {
            var t = midnightUtc.plusSeconds((minutes * 60).toLong()).withZoneSameInstant(zone)
            while (t.toLocalDate().isAfter(date)) t = t.minusDays(1)
            while (t.toLocalDate().isBefore(date)) t = t.plusDays(1)
            return t
        }
        return SunTimes(sunrise = onLocalDate(rise), sunset = onLocalDate(set))
    }

    private fun utcEventMinutes(date: LocalDate, lat: Double, lon: Double, rising: Boolean): Double? {
        val zenith = 90.833
        val n = date.dayOfYear
        val lngHour = lon / 15.0
        val t = n + ((if (rising) 6.0 else 18.0) - lngHour) / 24.0
        val m = 0.9856 * t - 3.289
        var l = m + 1.916 * sinD(m) + 0.020 * sinD(2 * m) + 282.634
        l = norm360(l)
        var ra = atanD(0.91764 * tanD(l))
        ra = norm360(ra)
        ra += floor(l / 90.0) * 90.0 - floor(ra / 90.0) * 90.0
        ra /= 15.0
        val sinDec = 0.39782 * sinD(l)
        val cosDec = cos(asin(sinDec))
        val cosH = (cosD(zenith) - sinDec * sinD(lat)) / (cosDec * cosD(lat))
        if (cosH > 1 || cosH < -1) return null
        val h = (if (rising) 360.0 - acosD(cosH) else acosD(cosH)) / 15.0
        val localT = h + ra - 0.06571 * t - 6.622
        val ut = ((localT - lngHour) % 24 + 24) % 24
        return ut * 60.0
    }

    private fun rad(d: Double) = Math.toRadians(d)
    private fun sinD(d: Double) = sin(rad(d))
    private fun cosD(d: Double) = cos(rad(d))
    private fun tanD(d: Double) = tan(rad(d))
    private fun acosD(x: Double) = Math.toDegrees(acos(x))
    private fun atanD(x: Double) = Math.toDegrees(kotlin.math.atan(x))
    private fun norm360(d: Double) = ((d % 360) + 360) % 360
}
