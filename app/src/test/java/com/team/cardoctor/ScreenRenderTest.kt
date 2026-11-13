package com.team.cardoctor

import android.graphics.Bitmap
import android.graphics.Canvas
import androidx.activity.ComponentActivity
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import com.team.cardoctor.ui.LocalStaticRender
import com.team.cardoctor.ai.TemplateExplainer
import com.team.cardoctor.data.DemoOverrides
import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Environment
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.RepairShops
import com.team.cardoctor.data.Weather
import com.team.cardoctor.data.DemoSource
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.HistoryEntry
import com.team.cardoctor.engine.RuleEngine
import com.team.cardoctor.engine.ServiceReport
import com.team.cardoctor.engine.TrendPredictor
import com.team.cardoctor.engine.VoiceCommands
import com.team.cardoctor.sdk.VoiceState
import com.team.cardoctor.sdk.VehicleSnapshot
import com.team.cardoctor.ui.CarDoctorTheme
import com.team.cardoctor.ui.DiagnosisProgress
import com.team.cardoctor.ui.MainScreen
import com.team.cardoctor.ui.UiActions
import com.team.cardoctor.ui.UiState
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.File
import java.io.FileOutputStream

/**
 * Renders the main screens to PNG files in docs/renders without an emulator so the team can
 * review layout and the scenario A/B/C storyboard. Not a pixel-diff test: it only fails on crash.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w1408dp-h792dp-land-mdpi")
class ScreenRenderTest {

    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private val outDir = File(System.getProperty("renderDir") ?: "build/renders").apply { mkdirs() }

    private fun state(parts: List<Part>, ctx: DrivingContext, demo: DemoOverrides = DemoOverrides(), diagnosisStep: Int? = null): UiState {
        val verdicts = RuleEngine.evaluateAll(parts.map { Fault(it, FaultSource.DEMO) }, ctx)
        val selected = verdicts.firstOrNull()
        val env = Environment(weather = ctx.weather, weatherFetched = true)
        return UiState(
            context = ctx,
            verdicts = verdicts,
            overall = RuleEngine.overall(verdicts),
            selected = selected,
            explanation = selected?.let { TemplateExplainer.template(it) },
            shops = RepairShops.near(env.lat, env.lng, selected?.fault?.part?.category),
            vehicle = VehicleSnapshot(connected = true, gear = if (ctx.isDriving) "GEAR_DRIVE" else "GEAR_PARK", isDriving = ctx.isDriving),
            environment = env,
            demo = demo,
            diagnosis = diagnosisStep?.let { DiagnosisProgress(running = true, step = it) } ?: DiagnosisProgress(),
        )
    }

    private fun render(name: String, state: UiState, demoOpen: Boolean = false) {
        compose.setContent {
            CompositionLocalProvider(LocalStaticRender provides true) {
                CarDoctorTheme { MainScreen(state, UiActions.None, initialDemoOpen = demoOpen) }
            }
        }
        compose.waitForIdle()
        // Draw the window directly; captureToImage() relies on PixelCopy, which times out under Robolectric.
        val root = compose.activity.window.decorView.rootView
        val bmp = Bitmap.createBitmap(root.width, root.height, Bitmap.Config.ARGB_8888)
        root.draw(Canvas(bmp))
        FileOutputStream(File(outDir, "$name.png")).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    private val drivingDay = DrivingContext(isNight = false, weather = Weather.CLEAR, speedKmh = 50, remainingKm = null, isDriving = true)

    @Test fun a_day_headlamp_driving() =
        render("A_day_headlamp", state(listOf(Part.HEADLAMP_LEFT), drivingDay, DemoOverrides(scenario = "A")))

    @Test fun b_night_headlamp_driving() =
        render("B_night_headlamp", state(listOf(Part.HEADLAMP_LEFT), drivingDay.copy(isNight = true), DemoOverrides(scenario = "B")))

    @Test fun c_rain_highway_tire_driving() =
        render("C_rain_highway_tire", state(listOf(Part.TIRE_RR), drivingDay.copy(weather = Weather.RAIN, speedKmh = 100), DemoOverrides(scenario = "C")))

    @Test fun c_parked_detail() =
        render("C2_parked_detail", state(listOf(Part.TIRE_RR, Part.WASHER_FLUID), drivingDay.copy(weather = Weather.RAIN, speedKmh = 0, isDriving = false)))

    @Test fun stop_now_brake() =
        render("D_brake_stop_now", state(listOf(Part.BRAKE_WARNING, Part.HEADLAMP_LEFT), drivingDay.copy(isNight = true, weather = Weather.RAIN)))

    @Test fun all_clear() = render("E_all_clear", state(emptyList(), DrivingContext.DEFAULT))

    @Test fun self_diagnosis_running() = render("F_self_diagnosis", state(emptyList(), DrivingContext.DEFAULT, diagnosisStep = 2))

    @Test fun predictive_and_history_on_all_clear() {
        val now = System.currentTimeMillis()
        val pred = TrendPredictor.predictFalling(Part.TIRE_FL, DemoSource.slowLeakSamples(now), DemoSource.SLOW_LEAK_THRESHOLD)
        val history = listOf(
            HistoryEntry(now - 2 * 86_400_000L, Part.TPMS, Grade.AFTER_ARRIVAL, FaultSource.VEHICLE),
            HistoryEntry(now - 9 * 86_400_000L, Part.WASHER_FLUID, Grade.RELAXED, FaultSource.VEHICLE),
        )
        render("H_predict_history", state(emptyList(), DrivingContext.DEFAULT).copy(predictions = listOfNotNull(pred), history = history))
    }

    @Test fun shop_report_sheet() {
        val s = state(listOf(Part.TIRE_RR, Part.HEADLAMP_LEFT), drivingDay.copy(weather = Weather.RAIN, speedKmh = 0, isDriving = false))
        val text = ServiceReport.build(s.verdicts, s.context, mapOf("tire.RIGHT_REAR" to "27.0 (기준 30.0)"), emptyList(), emptyList(), System.currentTimeMillis())
        render("I_shop_report", s.copy(report = text))
    }

    @Test fun recurring_fault_badge() {
        val s = state(listOf(Part.TPMS), drivingDay.copy(speedKmh = 0, isDriving = false))
        render("J_recurring", s.copy(recurring = mapOf(Part.TPMS to 4)))
    }

    @Test fun voice_question_answer() {
        val ctx = drivingDay.copy(isNight = true)
        val s = state(listOf(Part.HEADLAMP_LEFT), ctx, DemoOverrides(scenario = "B"))
        val reply = VoiceCommands.statusAnswer(s.verdicts, ctx)
        render("K_voice_answer", s.copy(voice = VoiceState(available = true, transcript = "지금 정비소 가야 돼?", reply = reply)))
    }

    @Test fun demo_panel_open() =
        render("G_demo_panel", state(listOf(Part.HEADLAMP_LEFT), drivingDay, DemoOverrides(injected = setOf(Part.HEADLAMP_LEFT), scenario = "A")), demoOpen = true)
}
