package com.team.cardoctor

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Part
import com.team.cardoctor.ui.MainViewModel
import com.team.cardoctor.ui.UiState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Wires the real ViewModel with no Pleos services present (like a plain emulator): every SDK
 * must fail soft, and the demo scenarios must drive the whole pipeline to the expected grade.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ViewModelSmokeTest {

    private lateinit var vm: MainViewModel

    @Before fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        vm = MainViewModel(ApplicationProvider.getApplicationContext<Application>())
    }

    @After fun tearDown() = Dispatchers.resetMain()

    private val ui: UiState get() = vm.ui.value

    @Test fun starts_without_pleos_services() {
        assertFalse("no Vehicle Service under Robolectric", ui.vehicle.connected && ui.vehicle.faults.isNotEmpty())
        assertEquals(Grade.OK, ui.overall)
    }

    @Test fun scenario_a_then_b_changes_the_grade_for_the_same_fault() {
        vm.scenario("A")
        assertEquals(Part.HEADLAMP_LEFT, ui.selected?.fault?.part)
        assertEquals(Grade.RELAXED, ui.overall)

        vm.scenario("B")
        assertEquals(Grade.SERVICE_NOW, ui.overall)
        assertTrue(ui.selected!!.reasons.any { it.label == "야간 운행" })
        assertNotNull("template explanation is shown immediately", ui.explanation)
    }

    @Test fun scenario_c_then_park_switches_to_parked_ux() {
        vm.scenario("C")
        assertTrue(ui.context.isDriving)
        assertEquals(Grade.SERVICE_NOW, ui.overall)
        vm.park()
        assertFalse(ui.context.isDriving)
    }

    @Test fun slow_leak_produces_a_prediction() {
        vm.toggleSlowLeak()
        assertEquals(Part.TIRE_FL, ui.predictions.firstOrNull()?.part)
    }

    @Test fun report_opens_and_closes() {
        vm.scenario("C")
        vm.openReport()
        assertTrue(ui.report!!.contains("뒤 오른쪽 타이어 공기압 저하"))
        vm.closeReport()
        assertEquals(null, ui.report)
    }

    @Test fun voice_status_question_is_answered_from_the_verdict() {
        vm.scenario("B")
        vm.voiceText("지금 정비소 가야 돼?")
        assertEquals("지금 정비소 가야 돼?", ui.voice.transcript)
        assertTrue(ui.voice.reply!!, ui.voice.reply!!.startsWith("좌측 전조등 미점등, 3등급"))
    }

    @Test fun voice_button_without_stt_explains_instead_of_doing_nothing() {
        vm.startVoice()
        assertEquals(com.team.cardoctor.sdk.Listener.UNAVAILABLE, ui.voice.reply)
    }

    @Test fun demo_faults_are_not_written_to_history() {
        vm.scenario("B")
        assertTrue(ui.history.none { it.part == Part.HEADLAMP_LEFT })
    }
}
