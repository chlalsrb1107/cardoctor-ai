package com.team.cardoctor

import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.HistoryLog
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Weather
import com.team.cardoctor.engine.RuleEngine
import com.team.cardoctor.engine.Sample
import com.team.cardoctor.engine.ServiceReport
import com.team.cardoctor.engine.TrendPredictor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private const val HOUR = 3_600_000L
private const val DAY = 24 * HOUR

class TrendPredictorTest {
    private fun falling(perDay: Double, start: Double = 36.0, n: Int = 8, everyHours: Long = 12) =
        (0 until n).map { Sample(it * everyHours * HOUR, start + perDay * it * everyHours / 24.0) }

    @Test fun slow_leak_is_predicted_within_horizon() {
        // 36 psi losing 0.5 psi/day, warning at 30 psi -> ~10 days left after 3.5 days of data
        val p = TrendPredictor.predictFalling(Part.TIRE_FL, falling(-0.5), threshold = 30.0)
        assertNotNull(p)
        assertTrue("daysLeft=${p!!.daysLeft}", p.daysLeft in 7.5..9.5)
        assertEquals(-0.5, p.perDay, 0.01)
    }

    @Test fun stable_pressure_has_no_prediction() =
        assertNull(TrendPredictor.predictFalling(Part.TIRE_FL, falling(0.0), threshold = 30.0))

    @Test fun very_slow_leak_beyond_two_weeks_is_ignored() =
        assertNull(TrendPredictor.predictFalling(Part.TIRE_FL, falling(-0.1), threshold = 30.0))

    @Test fun too_few_or_too_close_samples_are_ignored() {
        assertNull(TrendPredictor.predictFalling(Part.TIRE_FL, falling(-2.0, n = 3), threshold = 30.0))
        // 4 samples one hour apart span only 3 h (< MIN_SPAN_HOURS)
        assertNull(TrendPredictor.predictFalling(Part.TIRE_FL, falling(-2.0, n = 4, everyHours = 1), threshold = 30.0))
    }

    @Test fun already_below_threshold_is_a_fault_not_a_prediction() =
        assertNull(TrendPredictor.predictFalling(Part.TIRE_FL, falling(-0.5, start = 29.0), threshold = 30.0))
}

class HistoryLogTest {
    private val day = DrivingContext(isNight = false, weather = Weather.CLEAR, speedKmh = 0, remainingKm = null, isDriving = false)
    private fun verdicts(vararg parts: Part, ctx: DrivingContext = day) =
        RuleEngine.evaluateAll(parts.map { Fault(it, FaultSource.VEHICLE) }, ctx)

    @Test fun steady_fault_is_logged_once() {
        val log = HistoryLog()
        assertTrue(log.observe(verdicts(Part.HEADLAMP_LEFT), 0))
        assertFalse(log.observe(verdicts(Part.HEADLAMP_LEFT), HOUR))
        assertEquals(1, log.entries.size)
    }

    @Test fun worsening_grade_is_logged_again() {
        val log = HistoryLog()
        log.observe(verdicts(Part.HEADLAMP_LEFT), 0)
        log.observe(verdicts(Part.HEADLAMP_LEFT, ctx = day.copy(isNight = true)), HOUR)
        assertEquals(listOf(Grade.RELAXED, Grade.SERVICE_NOW), log.entries.map { it.grade })
    }

    @Test fun fault_returning_on_three_days_is_recurring() {
        val log = HistoryLog()
        for (d in 0 until 3) {
            log.observe(verdicts(Part.TPMS), d * DAY)
            log.observe(emptyList(), d * DAY + HOUR) // cleared
        }
        assertEquals(3, log.occurrences(Part.TPMS, 3 * DAY))
        assertTrue(log.isRecurring(Part.TPMS, 3 * DAY))
        assertFalse(log.isRecurring(Part.TPMS, 60 * DAY))
    }
}

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class HistoryJsonAndReportTest {
    @Test fun history_round_trips_through_json() {
        val log = HistoryLog()
        log.observe(RuleEngine.evaluateAll(listOf(Fault(Part.TIRE_RR, FaultSource.DEMO)), DrivingContext.DEFAULT), 1234L)
        val back = HistoryLog.fromJson(HistoryLog.toJson(log.entries))
        assertEquals(log.entries, back)
        assertEquals(emptyList<Any>(), HistoryLog.fromJson("not json"))
    }

    @Test fun tire_trends_round_trip_through_json() {
        val trends = mapOf(
            Part.TIRE_FL to com.team.cardoctor.sdk.TireTrend(listOf(Sample(1L, 35.5), Sample(2L, 35.0)), 30.0),
            Part.TIRE_RR to com.team.cardoctor.sdk.TireTrend(listOf(Sample(3L, 33.0)), null),
        )
        val back = com.team.cardoctor.data.TrendFile.fromJson(com.team.cardoctor.data.TrendFile.toJson(trends))
        assertEquals(trends, back)
        assertEquals(emptyMap<Part, Any>(), com.team.cardoctor.data.TrendFile.fromJson("{broken"))
    }

    @Test fun report_lists_faults_reasons_and_predictions() {
        val ctx = DrivingContext(isNight = true, weather = Weather.RAIN, speedKmh = 100, remainingKm = null, isDriving = true)
        val vs = RuleEngine.evaluateAll(listOf(Fault(Part.TIRE_RR, FaultSource.VEHICLE, "27.0 (기준 30.0)")), ctx)
        val pred = TrendPredictor.predictFalling(
            Part.TIRE_FL, (0 until 8).map { Sample(it * 12 * HOUR, 36.0 - 0.25 * it) }, 30.0,
        )
        val text = ServiceReport.build(vs, ctx, mapOf("tire.RIGHT_REAR" to "27.0 (기준 30.0)", "gear" to "-"), emptyList(), listOfNotNull(pred), 0L)
        assertTrue(text, text.contains("뒤 오른쪽 타이어 공기압 저하: 3등급"))
        assertTrue(text, text.contains("비 — 노면 미끄러움 +2"))
        assertTrue(text, text.contains("예측"))
        assertTrue(text, text.contains("tire.RIGHT_REAR"))
        assertFalse("placeholder signals are dropped", text.contains("gear:"))
    }
}
