package com.team.cardoctor

import com.team.cardoctor.engine.Daylight
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

class DaylightTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val lat = 37.5665
    private val lon = 126.9780

    @Test fun afternoon_in_seoul_is_day() =
        assertFalse(Daylight.isNight(ZonedDateTime.of(2026, 11, 13, 14, 0, 0, 0, seoul), lat, lon))

    @Test fun nine_pm_in_seoul_is_night() =
        assertTrue(Daylight.isNight(ZonedDateTime.of(2026, 11, 13, 21, 0, 0, 0, seoul), lat, lon))

    @Test fun seoul_november_sun_times_are_plausible() {
        val t = Daylight.sunTimes(LocalDate.of(2026, 11, 13), lat, lon, seoul)!!
        // Around 07:05 sunrise and 17:20 sunset in mid-November.
        assertTrue(t.sunrise.hour == 7)
        assertTrue(t.sunset.hour == 17)
    }
}
