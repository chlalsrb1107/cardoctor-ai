package com.team.cardoctor.sdk

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.WheelArea
import ai.pleos.playground.vehicle.api.model.telltale.Telltales
import ai.pleos.playground.vehicle.constant.area.WheelValue
import ai.pleos.playground.vehicle.constant.control.OilLevelState
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.constant.telltale.TelltaleState
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.LowWasherFluidWaringListener
import ai.pleos.playground.vehicle.listener.OilLevelStateListener
import ai.pleos.playground.vehicle.listener.TelltaleInfoListener
import ai.pleos.playground.vehicle.listener.TirePressureListener
import android.content.Context
import android.util.Log
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Part
import com.team.cardoctor.engine.Sample
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** What the car itself reports through the Pleos Vehicle SDK. */
data class VehicleSnapshot(
    val connected: Boolean = false,
    val faults: List<Fault> = emptyList(),
    val gear: String? = null,
    val isDriving: Boolean? = null,
    /** Human-readable log of which SDK signals answered, shown in the self-diagnosis screen. */
    val signals: Map<String, String> = emptyMap(),
)

data class TireTrend(val samples: List<Sample>, val threshold: Double?)

/**
 * Reads diagnosis-relevant signals: Safety telltales (계기판 경고등), tire pressure per wheel,
 * engine oil / brake fluid levels, washer fluid, and the gear (for driving vs parked UX).
 * All SDK calls are async IPC; every callback just updates [state].
 */
class VehicleSource(context: Context) {

    private val vehicle = Vehicle(context.applicationContext)
    private val _state = MutableStateFlow(VehicleSnapshot())
    val state: StateFlow<VehicleSnapshot> = _state.asStateFlow()

    private val _trends = MutableStateFlow<Map<Part, TireTrend>>(emptyMap())
    /** Tire pressure history per wheel (in memory) for the predictive check. */
    val trends: StateFlow<Map<Part, TireTrend>> = _trends.asStateFlow()

    // Fault buckets keyed by signal so one signal clearing does not clear another's faults.
    private val buckets = mutableMapOf<String, List<Fault>>()
    private val lowThreshold = mutableMapOf<WheelValue, Float>()
    private val lastPressure = mutableMapOf<WheelValue, Float>()
    private var initialized = false

    private val wheels = listOf(
        WheelValue.LEFT_FRONT to Part.TIRE_FL,
        WheelValue.RIGHT_FRONT to Part.TIRE_FR,
        WheelValue.LEFT_REAR to Part.TIRE_RL,
        WheelValue.RIGHT_REAR to Part.TIRE_RR,
    )

    private val telltaleListener = object : TelltaleInfoListener {
        override fun onTelltaleInfoUpdated(telltales: Telltales?) = onTelltales(telltales)
        override fun onFailed(e: Exception) = fail("telltale", e)
    }
    private val gearListener = object : GearSelectionListener {
        override fun onSelected(gear: VehicleGear?) = onGear(gear)
        override fun onFailed(e: Exception) = fail("gear", e)
    }
    private val engineOilListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) = onOil("engineOil", Part.ENGINE_OIL_LOW, state)
        override fun onFailed(e: Exception) = fail("engineOil", e)
    }
    private val brakeFluidListener = object : OilLevelStateListener {
        override fun onOilLevelUpdated(state: OilLevelState?) = onOil("brakeFluid", Part.BRAKE_FLUID_LOW, state)
        override fun onFailed(e: Exception) = fail("brakeFluid", e)
    }
    private val washerListener = object : LowWasherFluidWaringListener {
        override fun onLowWasherFluidWaringUpdated(isLow: Boolean?) = onWasher(isLow)
        override fun onFailed(e: Exception) = fail("washer", e)
    }
    private val tireListeners = wheels.associate { (wheel, part) ->
        wheel to object : TirePressureListener {
            override fun onTirePressureUpdated(pressure: Float?) = onTire(wheel, part, pressure)
            override fun onFailed(e: Exception) = fail("tire.$wheel", e)
        }
    }

    fun start() {
        if (initialized) return
        try {
            vehicle.initialize()
            initialized = true
            _state.update { it.copy(connected = true) }
        } catch (e: Exception) {
            fail("initialize", e)
            return
        }
        subscribe()
        refreshAll()
    }

    /** Restore persisted tire history so predictions survive an app restart. */
    fun restoreTrends(saved: Map<Part, TireTrend>) {
        if (saved.isNotEmpty()) _trends.update { saved + it }
    }

    /** One-shot read of every signal; used by the "전체 점검" button. */
    fun refreshAll() {
        if (!initialized) return
        safe("telltale") { vehicle.getSafety().getTelltalesInfo({ t -> onTelltales(t) }, { fail("telltale", it) }) }
        safe("gear") { vehicle.getGear().getCurrentGearSelectionState({ g -> onGear(g) }, { fail("gear", it) }) }
        safe("engineOil") {
            vehicle.getSafety().getEngineOilLevelState({ s -> onOil("engineOil", Part.ENGINE_OIL_LOW, s) }, { fail("engineOil", it) })
        }
        safe("brakeFluid") {
            vehicle.getSafety().getBrakeFluidLevelState({ s -> onOil("brakeFluid", Part.BRAKE_FLUID_LOW, s) }, { fail("brakeFluid", it) })
        }
        safe("washer") { vehicle.getSafety().hasLowWasherFluidWarning({ low -> onWasher(low) }, { fail("washer", it) }) }
        wheels.forEach { (wheel, part) ->
            val area = WheelArea(wheel)
            safe("tireLow.$wheel") {
                vehicle.getTire().getLowTirePressure(area, { th -> onThreshold(wheel, part, th) }, { fail("tireLow.$wheel", it) })
            }
            safe("tire.$wheel") {
                vehicle.getTire().getTirePressure(area, { p -> onTire(wheel, part, p) }, { fail("tire.$wheel", it) })
            }
        }
    }

    private fun subscribe() {
        safe("telltale.sub") { vehicle.getSafety().registerTelltales(telltaleListener) }
        safe("gear.sub") { vehicle.getGear().registerGearSelection(gearListener) }
        safe("engineOil.sub") { vehicle.getSafety().registerEngineOilLevelState(engineOilListener) }
        safe("brakeFluid.sub") { vehicle.getSafety().registerBrakeFluidLevelState(brakeFluidListener) }
        safe("washer.sub") { vehicle.getSafety().registerLowWasherFluidWaringState(washerListener) }
        tireListeners.forEach { (wheel, l) -> safe("tire.sub.$wheel") { vehicle.getTire().registerTirePressure(WheelArea(wheel), l) } }
    }

    fun stop() {
        if (!initialized) return
        safe("unsub") {
            vehicle.getSafety().unregisterTelltales(telltaleListener)
            vehicle.getGear().unregisterGearSelection(gearListener)
            vehicle.getSafety().unregisterEngineOilLevelState(engineOilListener)
            vehicle.getSafety().unregisterBrakeFluidLevelState(brakeFluidListener)
            vehicle.getSafety().unregisterLowWasherFluidWaringState(washerListener)
            tireListeners.forEach { (wheel, l) -> vehicle.getTire().unregisterTirePressure(WheelArea(wheel), l) }
        }
        safe("release") { vehicle.release() }
        initialized = false
    }

    // --- signal handlers -------------------------------------------------------------------

    private var lastTelltales: Telltales? = null

    private fun onTelltales(telltales: Telltales?) {
        lastTelltales = telltales
        val driving = _state.value.isDriving == true
        val active = telltales?.telltales.orEmpty().filter { it.state == TelltaleState.ON || it.state == TelltaleState.BLINK }
        val faults = active.mapNotNull { d ->
            TelltaleMapper.partFor(d.type.name, driving)?.let { Fault(it, FaultSource.VEHICLE, "경고등 ${d.type.name} ${d.state.name}") }
        }.distinctBy { it.part }
        put("telltale", faults, "${active.size}개 점등")
    }

    private fun onGear(gear: VehicleGear?) {
        val name = gear?.name ?: "GEAR_UNKNOWN"
        val driving = name !in setOf("GEAR_PARK", "GEAR_NEUTRAL", "GEAR_UNKNOWN")
        val changed = _state.value.isDriving != driving
        _state.update { it.copy(gear = name, isDriving = driving, signals = it.signals + ("gear" to name)) }
        // The red parking-brake lamp only counts as a fault while driving, so re-judge telltales on P <-> D.
        if (changed && lastTelltales != null) onTelltales(lastTelltales)
    }

    private fun onOil(key: String, part: Part, state: OilLevelState?) {
        val low = state == OilLevelState.LOW || state == OilLevelState.CRITICALLY_LOW
        put(key, if (low) listOf(Fault(part, FaultSource.VEHICLE, "레벨 ${state?.name}")) else emptyList(), state?.name ?: "-")
    }

    private fun onWasher(isLow: Boolean?) {
        put("washer", if (isLow == true) listOf(Fault(Part.WASHER_FLUID, FaultSource.VEHICLE)) else emptyList(), if (isLow == true) "부족" else "정상")
    }

    /** The low-pressure threshold can arrive after the first pressure reading; re-judge with the last one. */
    private fun onThreshold(wheel: WheelValue, part: Part, threshold: Float?) {
        if (threshold == null) return
        lowThreshold[wheel] = threshold
        lastPressure[wheel]?.let { onTire(wheel, part, it, record = false) }
    }

    private fun onTire(wheel: WheelValue, part: Part, pressure: Float?, record: Boolean = true) {
        val threshold = lowThreshold[wheel]
        if (pressure != null) {
            lastPressure[wheel] = pressure
            if (record) recordSample(part, pressure.toDouble(), threshold?.toDouble())
        }
        val low = pressure != null && threshold != null && pressure < threshold
        val detail = pressure?.let { "%.1f (기준 %s)".format(it, threshold?.let { t -> "%.1f".format(t) } ?: "-") }
        put("tire.$wheel", if (low) listOf(Fault(part, FaultSource.VEHICLE, detail)) else emptyList(), detail ?: "-")
    }

    /** Keeps at most one reading per [SAMPLE_GAP_MS] per tire for the trend predictor (feature C). */
    private fun recordSample(part: Part, value: Double, threshold: Double?) {
        val now = System.currentTimeMillis()
        _trends.update { all ->
            val prev = all[part] ?: TireTrend(emptyList(), threshold)
            val last = prev.samples.lastOrNull()
            if (last != null && now - last.timeMillis < SAMPLE_GAP_MS) return@update all
            all + (part to TireTrend((prev.samples + Sample(now, value)).takeLast(MAX_SAMPLES), threshold ?: prev.threshold))
        }
    }

    private fun put(key: String, faults: List<Fault>, signal: String) {
        synchronized(buckets) { buckets[key] = faults }
        val all = synchronized(buckets) { buckets.values.flatten() }.distinctBy { it.part }
        _state.update { it.copy(faults = all, signals = it.signals + (key to signal)) }
    }

    private fun fail(key: String, e: Throwable) {
        Log.w(TAG, "$key failed: ${e.message}")
        _state.update { it.copy(signals = it.signals + (key to "미지원/오류")) }
    }

    private inline fun safe(key: String, block: () -> Unit) {
        try { block() } catch (e: Exception) { fail(key, e) }
    }

    private companion object {
        const val TAG = "VehicleSource"
        const val SAMPLE_GAP_MS = 10 * 60_000L
        const val MAX_SAMPLES = 200
    }
}
