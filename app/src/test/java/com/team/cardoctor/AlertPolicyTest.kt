package com.team.cardoctor

import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Weather
import com.team.cardoctor.engine.AlertPolicy
import com.team.cardoctor.engine.AlertPolicy.Key
import com.team.cardoctor.engine.RuleEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AlertPolicyTest {
    private val day = DrivingContext(isNight = false, weather = Weather.CLEAR, speedKmh = 50, remainingKm = null, isDriving = true)
    private val night = day.copy(isNight = true)
    private fun top(part: Part, ctx: DrivingContext) = RuleEngine.evaluate(Fault(part, FaultSource.DEMO), ctx)

    @Test fun grade_one_is_silent() = assertNull(AlertPolicy.announcement(null, top(Part.HEADLAMP_LEFT, day)))

    @Test fun reaching_grade_three_speaks_with_reason() {
        val a = AlertPolicy.announcement(Key(Part.HEADLAMP_LEFT, Grade.RELAXED), top(Part.HEADLAMP_LEFT, night))
        assertTrue(a, a!!.startsWith("좌측 전조등 미점등. 3등급, 즉시 정비."))
        assertTrue(a, a.contains("야간 운행 때문에 위험도가 올라갔습니다."))
    }

    @Test fun same_state_is_not_repeated() =
        assertNull(AlertPolicy.announcement(Key(Part.HEADLAMP_LEFT, Grade.SERVICE_NOW), top(Part.HEADLAMP_LEFT, night)))

    @Test fun dropping_below_three_is_announced_once() {
        val a = AlertPolicy.announcement(Key(Part.HEADLAMP_LEFT, Grade.SERVICE_NOW), top(Part.HEADLAMP_LEFT, day))
        assertEquals("상황이 바뀌어 좌측 전조등 미점등 등급이 1등급으로 낮아졌습니다. 주말이나 다음 정기점검 때 방문하세요.", a)
    }

    @Test fun clearing_a_grade_three_fault_is_announced() =
        assertEquals("좌측 전조등 미점등 경고가 해제되었습니다.", AlertPolicy.announcement(Key(Part.HEADLAMP_LEFT, Grade.SERVICE_NOW), null))

    @Test fun arrival_reminds_only_grade_two_items() {
        val vs = RuleEngine.evaluateAll(
            listOf(Fault(Part.TIRE_RR, FaultSource.VEHICLE), Fault(Part.HEADLAMP_LEFT, FaultSource.VEHICLE)), day,
        )
        val a = AlertPolicy.arrivalReminder(vs)!!
        assertTrue(a, a.contains("뒤 오른쪽 타이어 공기압 저하 점검을 오늘 중에"))
        assertTrue("grade-1 headlamp is not nagged", !a.contains("전조등"))
        assertNull(AlertPolicy.arrivalReminder(RuleEngine.evaluateAll(listOf(Fault(Part.HEADLAMP_LEFT, FaultSource.VEHICLE)), day)))
    }

    @Test fun safety_floor_without_env_reason_has_no_reason_sentence() {
        val a = AlertPolicy.announcement(null, top(Part.COOLANT_OVERHEAT, day))!!
        assertTrue(a, a.startsWith("냉각수 과열. 3등급, 즉시 정비. 가장"))
    }
}
