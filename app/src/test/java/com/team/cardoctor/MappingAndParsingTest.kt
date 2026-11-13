package com.team.cardoctor

import com.team.cardoctor.ai.ClaudeExplainer
import com.team.cardoctor.data.Category
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.RepairShops
import com.team.cardoctor.sdk.TelltaleMapper
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MappingAndParsingTest {

    @Test fun telltales_map_to_parts() {
        assertEquals(Part.TPMS, TelltaleMapper.partFor("TPMS", isDriving = true))
        assertEquals(Part.COOLANT_OVERHEAT, TelltaleMapper.partFor("ENGINE_TEMPERATURE", isDriving = false))
        assertEquals(Part.HEADLAMP_SYSTEM, TelltaleMapper.partFor("LED_HEADLAMP", isDriving = false))
        assertNull("status lamps are not faults", TelltaleMapper.partFor("TURN_SIGNAL_LEFT", isDriving = true))
        assertNull(TelltaleMapper.partFor("DRIVE_MODE_ECO", isDriving = true))
    }

    @Test fun parking_brake_lamp_is_a_fault_only_while_driving() {
        assertNull(TelltaleMapper.partFor("PARKING_BRAKE", isDriving = false))
        assertEquals(Part.BRAKE_WARNING, TelltaleMapper.partFor("PARKING_BRAKE", isDriving = true))
    }

    @Test fun claude_output_with_three_lines_parses() {
        val e = ClaudeExplainer.parse("원인: 전구가 끊어졌습니다.\n위험: 밤에 앞이 잘 안 보입니다.\n할 일: 가까운 정비소로 가세요.")
        assertNotNull(e)
        assertEquals("전구가 끊어졌습니다.", e!!.cause)
        assertEquals("가까운 정비소로 가세요.", e.action)
        assertTrue(e.byAi)
    }

    @Test fun claude_output_tolerates_spacing_and_missing_space_in_label() {
        val e = ClaudeExplainer.parse("  원인 : A\n위험:B\n할일: C")
        assertEquals("A", e?.cause)
        assertEquals("C", e?.action)
    }

    @Test fun claude_output_missing_a_line_falls_back() {
        assertNull(ClaudeExplainer.parse("원인: A\n할 일: C"))
        assertNull(ClaudeExplainer.parse(""))
    }

    @Test fun shops_are_sorted_and_filtered_by_category() {
        val all = RepairShops.near(37.5665, 126.9780, null)
        assertEquals(all.sortedBy { it.distanceKm }, all)
        val tire = RepairShops.near(37.5665, 126.9780, Category.TIRE)
        assertTrue(tire.all { Category.TIRE in it.handles })
        assertTrue(tire.size < all.size)
    }

    @Test fun haversine_seoul_to_busan_is_about_325km() {
        val d = RepairShops.haversineKm(37.5665, 126.9780, 35.1796, 129.0756)
        assertTrue(d in 315.0..335.0)
    }
}
