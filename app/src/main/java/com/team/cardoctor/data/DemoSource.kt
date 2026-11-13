package com.team.cardoctor.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Demo panel state (feature F2). Injects faults the emulator cannot produce and overrides the
 * environment so the judging scenarios A/B/C can be replayed on stage. null = use the real value.
 */
data class DemoOverrides(
    val injected: Set<Part> = emptySet(),
    val night: Boolean? = null,
    val weather: Weather? = null,
    val speedKmh: Int? = null,
    val driving: Boolean? = null,
    val remainingKm: Double? = null,
    val scenario: String? = null,
    /** Scenario D: front-left tire slowly losing pressure -> predictive warning before any fault. */
    val slowLeak: Boolean = false,
)

class DemoSource {

    companion object {
        const val SLOW_LEAK_THRESHOLD = 30.0

        /** 8 readings over the last 3.5 days, 36 psi falling 0.5 psi/day -> about 8 days left. */
        fun slowLeakSamples(now: Long): List<com.team.cardoctor.engine.Sample> =
            (0 until 8).map { i ->
                val hoursAgo = (7 - i) * 12L
                com.team.cardoctor.engine.Sample(now - hoursAgo * 3_600_000L, 36.0 - 0.25 * i)
            }
    }

    fun toggleSlowLeak() = _state.update { it.copy(slowLeak = !it.slowLeak, scenario = null) }

    private val _state = MutableStateFlow(DemoOverrides())
    val state: StateFlow<DemoOverrides> = _state.asStateFlow()

    fun toggle(part: Part) = _state.update {
        it.copy(injected = if (part in it.injected) it.injected - part else it.injected + part, scenario = null)
    }

    fun setNight(v: Boolean?) = _state.update { it.copy(night = v, scenario = null) }
    fun setWeather(v: Weather?) = _state.update { it.copy(weather = v, scenario = null) }
    fun setSpeed(v: Int?) = _state.update { it.copy(speedKmh = v, scenario = null) }
    fun setDriving(v: Boolean?) = _state.update { it.copy(driving = v, scenario = null) }
    fun setRemainingKm(v: Double?) = _state.update { it.copy(remainingKm = v, scenario = null) }
    fun reset() = _state.update { DemoOverrides() }

    /** A: 낮, 전조등 고장 -> 1등급 주말 방문 */
    fun scenarioA() = _state.update {
        DemoOverrides(setOf(Part.HEADLAMP_LEFT), night = false, weather = Weather.CLEAR, speedKmh = 50, driving = true, scenario = "A")
    }

    /** B: same fault, only the clock moves to 21:00 -> 3등급 즉시 정비 */
    fun scenarioB() = _state.update {
        DemoOverrides(setOf(Part.HEADLAMP_LEFT), night = true, weather = Weather.CLEAR, speedKmh = 50, driving = true, scenario = "B")
    }

    /** C: 빗길 고속도로, 뒤 오른쪽 공기압 저하 -> 3등급 (비 +2, 고속 +1) */
    fun scenarioC() = _state.update {
        DemoOverrides(setOf(Part.TIRE_RR), night = false, weather = Weather.RAIN, speedKmh = 100, driving = true, scenario = "C")
    }

    /** Stop the car in scenario C to show the parked detail UX. */
    fun park() = _state.update { it.copy(speedKmh = 0, driving = false) }
}
