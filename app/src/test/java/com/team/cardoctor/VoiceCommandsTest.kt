package com.team.cardoctor

import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Weather
import com.team.cardoctor.engine.RuleEngine
import com.team.cardoctor.engine.VoiceCommand
import com.team.cardoctor.engine.VoiceCommands
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VoiceCommandsTest {

    @Test fun parses_common_questions() {
        assertEquals(VoiceCommand.STATUS, VoiceCommands.parse("지금 정비소 가야 돼?"))
        assertEquals(VoiceCommand.STATUS, VoiceCommands.parse("차 상태 어때"))
        assertEquals(VoiceCommand.ROUTE_TO_SHOP, VoiceCommands.parse("정비소 안내해줘"))
        assertEquals(VoiceCommand.ROUTE_TO_SHOP, VoiceCommands.parse("가까운 카센터로 데려다 줘"))
        assertEquals(VoiceCommand.EXPLAIN, VoiceCommands.parse("왜 위험해?"))
        assertEquals(VoiceCommand.REPORT, VoiceCommands.parse("리포트 보여줘"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommands.parse("노래 틀어줘"))
        assertEquals(VoiceCommand.UNKNOWN, VoiceCommands.parse("   "))
    }

    @Test fun status_answer_leads_with_grade_and_reason() {
        val ctx = DrivingContext(isNight = true, weather = Weather.CLEAR, speedKmh = 50, remainingKm = null, isDriving = true)
        val vs = RuleEngine.evaluateAll(listOf(Fault(Part.HEADLAMP_LEFT, FaultSource.DEMO)), ctx)
        val a = VoiceCommands.statusAnswer(vs, ctx)
        assertTrue(a, a.startsWith("좌측 전조등 미점등, 3등급 즉시 정비입니다."))
        assertTrue(a, a.contains("야간 운행 때문에"))
        assertTrue(a, a.contains("정비소 안내해줘"))
    }

    @Test fun status_answer_when_all_clear() =
        assertTrue(VoiceCommands.statusAnswer(emptyList(), DrivingContext.DEFAULT).contains("없습니다"))
}
