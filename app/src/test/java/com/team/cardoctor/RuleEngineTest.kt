package com.team.cardoctor

import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Weather
import com.team.cardoctor.engine.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Mirrors the "부품별 판정 예시" table in the team PRD. */
class RuleEngineTest {

    private val dayClear = DrivingContext(isNight = false, weather = Weather.CLEAR, speedKmh = 50, remainingKm = null, isDriving = true)
    private val nightRain = dayClear.copy(isNight = true, weather = Weather.RAIN)

    private fun grade(part: Part, ctx: DrivingContext) = RuleEngine.evaluate(Fault(part, FaultSource.DEMO), ctx).grade

    @Test fun headlamp_day_is_relaxed() = assertEquals(Grade.RELAXED, grade(Part.HEADLAMP_LEFT, dayClear))
    @Test fun headlamp_night_is_service_now() = assertEquals(Grade.SERVICE_NOW, grade(Part.HEADLAMP_LEFT, nightRain))
    @Test fun headlamp_night_only_is_service_now() = assertEquals(Grade.SERVICE_NOW, grade(Part.HEADLAMP_LEFT, dayClear.copy(isNight = true)))

    @Test fun wiper_day_is_relaxed() = assertEquals(Grade.RELAXED, grade(Part.WIPER, dayClear))
    @Test fun wiper_rain_is_service_now() = assertEquals(Grade.SERVICE_NOW, grade(Part.WIPER, nightRain))

    @Test fun tire_day_is_after_arrival() = assertEquals(Grade.AFTER_ARRIVAL, grade(Part.TIRE_RR, dayClear))
    @Test fun tire_rain_is_service_now() = assertEquals(Grade.SERVICE_NOW, grade(Part.TIRE_RR, nightRain))

    @Test fun coolant_is_service_now_in_any_context() {
        assertEquals(Grade.SERVICE_NOW, grade(Part.COOLANT_OVERHEAT, dayClear))
        assertEquals(Grade.SERVICE_NOW, grade(Part.COOLANT_OVERHEAT, nightRain))
    }

    @Test fun brake_day_is_at_least_service_now() =
        assertTrue(grade(Part.BRAKE_WARNING, dayClear).level >= Grade.SERVICE_NOW.level)

    @Test fun brake_rain_is_stop_now() = assertEquals(Grade.STOP_NOW, grade(Part.BRAKE_WARNING, nightRain))

    @Test fun ev_battery_short_trip_is_after_arrival() = assertEquals(Grade.AFTER_ARRIVAL, grade(Part.EV_BATTERY_LOW, dayClear))
    @Test fun ev_battery_long_trip_is_service_now() =
        assertEquals(Grade.SERVICE_NOW, grade(Part.EV_BATTERY_LOW, dayClear.copy(remainingKm = 120.0)))

    /** Demo scenario C: rain + highway + low tire pressure -> grade 3 with both reasons shown. */
    @Test fun scenario_c_rain_highway_tire() {
        val v = RuleEngine.evaluate(Fault(Part.TIRE_RR, FaultSource.DEMO), dayClear.copy(weather = Weather.RAIN, speedKmh = 100))
        assertEquals(Grade.SERVICE_NOW, v.grade)
        assertEquals(5, v.score)
        assertEquals(3, v.reasons.size)
    }

    @Test fun safety_floor_is_flagged() {
        val v = RuleEngine.evaluate(Fault(Part.BRAKE_WARNING, FaultSource.DEMO), DrivingContext.DEFAULT)
        assertEquals(Grade.SERVICE_NOW, v.grade)
        assertTrue(v.flooredBySafety)
    }

    @Test fun overall_is_worst_grade_and_list_is_sorted() {
        val faults = listOf(Fault(Part.HEADLAMP_LEFT, FaultSource.DEMO), Fault(Part.BRAKE_WARNING, FaultSource.DEMO))
        val verdicts = RuleEngine.evaluateAll(faults, nightRain)
        assertEquals(Part.BRAKE_WARNING, verdicts.first().fault.part)
        assertEquals(Grade.STOP_NOW, RuleEngine.overall(verdicts))
        assertEquals(Grade.OK, RuleEngine.overall(emptyList()))
    }

    @Test fun what_if_shows_the_same_fault_across_situations() {
        val headlamp = RuleEngine.whatIf(Fault(Part.HEADLAMP_LEFT, FaultSource.DEMO)).toMap()
        assertEquals(Grade.RELAXED, headlamp["낮·맑음"])
        assertEquals(Grade.SERVICE_NOW, headlamp["밤"])
        assertEquals(Grade.RELAXED, headlamp["비"])

        val brake = RuleEngine.whatIf(Fault(Part.BRAKE_WARNING, FaultSource.DEMO)).toMap()
        assertEquals(Grade.SERVICE_NOW, brake["낮·맑음"])
        assertEquals(Grade.STOP_NOW, brake["빗길 고속"])
    }

    @Test fun what_if_label_matches_current_situation() {
        assertEquals("낮·맑음", RuleEngine.whatIfLabel(dayClear))
        assertEquals("밤", RuleEngine.whatIfLabel(dayClear.copy(isNight = true)))
        assertEquals("빗길 고속", RuleEngine.whatIfLabel(dayClear.copy(weather = Weather.RAIN, speedKmh = 100)))
        assertEquals(null, RuleEngine.whatIfLabel(nightRain)) // night + rain at city speed has no column
    }

    @Test fun score_bands() {
        assertEquals(Grade.RELAXED, RuleEngine.gradeFor(1))
        assertEquals(Grade.AFTER_ARRIVAL, RuleEngine.gradeFor(3))
        assertEquals(Grade.SERVICE_NOW, RuleEngine.gradeFor(5))
        assertEquals(Grade.STOP_NOW, RuleEngine.gradeFor(6))
    }
}
