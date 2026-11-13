package com.team.cardoctor.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.team.cardoctor.BuildConfig
import com.team.cardoctor.ai.Explanation
import com.team.cardoctor.ai.TemplateExplainer
import com.team.cardoctor.ai.explainerFor
import com.team.cardoctor.data.DemoOverrides
import com.team.cardoctor.data.DemoSource
import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Environment
import com.team.cardoctor.data.EnvironmentSource
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.FaultSource
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.HistoryEntry
import com.team.cardoctor.data.HistoryFile
import com.team.cardoctor.data.HistoryLog
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.RepairShop
import com.team.cardoctor.data.RepairShops
import com.team.cardoctor.data.ShopSearch
import com.team.cardoctor.data.TrendFile
import com.team.cardoctor.data.Verdict
import com.team.cardoctor.data.Weather
import com.team.cardoctor.engine.AlertPolicy
import com.team.cardoctor.engine.Prediction
import com.team.cardoctor.engine.RuleEngine
import com.team.cardoctor.engine.ServiceReport
import com.team.cardoctor.engine.TrendPredictor
import com.team.cardoctor.engine.VoiceCommand
import com.team.cardoctor.engine.VoiceCommands
import com.team.cardoctor.sdk.Listener
import com.team.cardoctor.sdk.NaviSource
import com.team.cardoctor.sdk.VoiceState
import com.team.cardoctor.sdk.Speaker
import com.team.cardoctor.sdk.VehicleSnapshot
import com.team.cardoctor.sdk.VehicleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class DiagnosisProgress(val running: Boolean = false, val step: Int = 0, val finishedAt: Long? = null) {
    companion object {
        val STEPS = listOf("경고등(Safety)", "타이어 공기압", "엔진오일·브레이크액", "워셔액·와이퍼", "등화·전조등", "배터리·에너지")
    }
}

data class UiState(
    val context: DrivingContext = DrivingContext.DEFAULT,
    val verdicts: List<Verdict> = emptyList(),
    val overall: Grade = Grade.OK,
    val selected: Verdict? = null,
    val explanation: Explanation? = null,
    val shops: List<RepairShop> = emptyList(),
    /** True when [shops] are the built-in samples (no location or no OpenStreetMap result). */
    val shopsAreSamples: Boolean = true,
    val vehicle: VehicleSnapshot = VehicleSnapshot(),
    val environment: Environment = Environment(),
    val demo: DemoOverrides = DemoOverrides(),
    val naviStatus: String? = null,
    val diagnosis: DiagnosisProgress = DiagnosisProgress(),
    val aiEnabled: Boolean = false,
    /** Feature C: parts expected to reach their warning threshold within two weeks. */
    val predictions: List<Prediction> = emptyList(),
    /** Feature H: most recent detections, newest first. */
    val history: List<HistoryEntry> = emptyList(),
    /** Parts seen on 3+ separate days in the last 30 days -> "반복 고장". Value = number of days. */
    val recurring: Map<Part, Int> = emptyMap(),
    /** Feature B: the shop report text while its sheet is open. */
    val report: String? = null,
    /** Feature A2: last voice question and the spoken answer. */
    val voice: VoiceState = VoiceState(),
)

class MainViewModel(app: Application) : AndroidViewModel(app), UiActions {

    val vehicle = VehicleSource(app)
    val demo = DemoSource()
    private val environment = EnvironmentSource(app)
    private val navi = NaviSource(app)
    private val speaker = Speaker(app)
    private val explainer = explainerFor(BuildConfig.ANTHROPIC_API_KEY)
    private val historyFile = HistoryFile(app)
    private val historyLog = HistoryLog(historyFile.load())
    private val trendFile = TrendFile(app)
    private val listener = Listener(app) { text -> viewModelScope.launch { answer(text) } }

    private val selectedPart = MutableStateFlow<Part?>(null)
    private val explanation = MutableStateFlow<Explanation?>(null)
    private val diagnosis = MutableStateFlow(DiagnosisProgress())
    private val clock = MutableStateFlow(System.currentTimeMillis())
    private val historyState = MutableStateFlow(historyLog.recent())
    private val report = MutableStateFlow<String?>(null)
    private val realShops = MutableStateFlow<List<RepairShop>>(emptyList())

    private val context: StateFlow<DrivingContext> = combine(
        vehicle.state, environment.state, demo.state, navi.remainingKm, clock,
    ) { v, env, d, remaining, _ ->
        val speed = d.speedKmh ?: env.speedKmh
        DrivingContext(
            isNight = d.night ?: env.isNight(),
            weather = d.weather ?: env.weather,
            speedKmh = speed,
            remainingKm = d.remainingKm ?: remaining,
            isDriving = d.driving ?: v.isDriving ?: (speed > 0),
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, DrivingContext.DEFAULT)

    private val verdicts: StateFlow<List<Verdict>> = combine(vehicle.state, demo.state, context) { v, d, ctx ->
        val injected = d.injected.map { Fault(it, FaultSource.DEMO) }
        val all = (injected + v.faults).distinctBy { it.part }
        RuleEngine.evaluateAll(all, ctx)
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val predictions: StateFlow<List<Prediction>> = combine(vehicle.trends, demo.state, clock) { trends, d, now ->
        val real = trends.mapNotNull { (part, t) -> t.threshold?.let { TrendPredictor.predictFalling(part, t.samples, it) } }
        val demoLeak = if (d.slowLeak) {
            listOfNotNull(TrendPredictor.predictFalling(Part.TIRE_FL, DemoSource.slowLeakSamples(now), DemoSource.SLOW_LEAK_THRESHOLD))
        } else emptyList()
        (demoLeak + real).distinctBy { it.part }.sortedBy { it.daysLeft }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    private val base = combine(context, verdicts, selectedPart, explanation, diagnosis) { ctx, vs, sel, exp, diag ->
        val selected = vs.firstOrNull { it.fault.part == sel } ?: vs.firstOrNull()
        UiState(
            context = ctx,
            verdicts = vs,
            overall = RuleEngine.overall(vs),
            selected = selected,
            explanation = exp,
            diagnosis = diag,
            aiEnabled = BuildConfig.ANTHROPIC_API_KEY.isNotBlank(),
        )
    }

    private val withSources = combine(base, vehicle.state, environment.state, demo.state, navi.status) { s, v, env, d, nav ->
        s.copy(
            vehicle = v,
            environment = env,
            demo = d,
            naviStatus = nav,
        )
    }

    private data class Extras(
        val predictions: List<Prediction>,
        val history: List<HistoryEntry>,
        val report: String?,
        val voice: VoiceState,
        val realShops: List<RepairShop>,
    )

    private val extras = combine(predictions, historyState, report, listener.state, realShops, ::Extras)

    val ui: StateFlow<UiState> = combine(withSources, extras) { s, x ->
        val now = System.currentTimeMillis()
        val category = s.selected?.fault?.part?.category
        // Real OpenStreetMap shops when we have them (tire shops only for tire faults); samples otherwise.
        val real = x.realShops.filter { category == null || category in it.handles }
        s.copy(
            shops = real.ifEmpty { RepairShops.near(s.environment.lat, s.environment.lng, category) },
            shopsAreSamples = real.isEmpty(),
            predictions = x.predictions,
            history = x.history,
            recurring = s.verdicts.mapNotNull { v ->
                historyLog.occurrences(v.fault.part, now).takeIf { it >= HistoryLog.RECURRING_THRESHOLD }?.let { v.fault.part to it }
            }.toMap(),
            report = x.report,
            voice = x.voice,
        )
    }.stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private var lastSpoken: AlertPolicy.Key? = null

    init {
        vehicle.restoreTrends(trendFile.load())
        vehicle.start()
        // Grade 2 "목적지 후 정비": remind once when the navigation reports arrival.
        viewModelScope.launch {
            navi.arrivals.collect { AlertPolicy.arrivalReminder(verdicts.value)?.let { speaker.speak(it) } }
        }
        // Save tire history whenever a new sample lands (at most every 10 minutes per tire).
        viewModelScope.launch(Dispatchers.IO) {
            vehicle.trends.collect { t -> if (t.isNotEmpty()) trendFile.save(t) }
        }
        navi.start()
        speaker.start()
        listener.start()
        environment.start()

        viewModelScope.launch {
            while (isActive) {
                environment.refreshWeather()
                delay(10 * 60_000L)
            }
        }
        // Re-fetch weather right away on the first real fix and after moving ~10 km (0.1° grid),
        // instead of keeping the default-location weather for up to 10 minutes.
        viewModelScope.launch {
            environment.state
                .filter { it.hasFix }
                .map { Math.round(it.lat * 10) to Math.round(it.lng * 10) }
                .distinctUntilChanged()
                .collect { environment.refreshWeather() }
        }
        // Real repair shops around the car, refreshed after moving ~5 km.
        viewModelScope.launch {
            environment.state
                .filter { it.hasFix }
                .map { Math.round(it.lat * 20) to Math.round(it.lng * 20) }
                .distinctUntilChanged()
                .collect {
                    val env = environment.state.value
                    val found = ShopSearch.near(env.lat, env.lng)
                    if (found.isNotEmpty()) realShops.value = found
                }
        }
        // Day/night depends on wall-clock time; re-evaluate every minute.
        viewModelScope.launch {
            while (isActive) {
                delay(60_000L)
                clock.value = System.currentTimeMillis()
            }
        }
        // Explanation follows the selected verdict.
        viewModelScope.launch {
            var lastKey: String? = null
            // collectLatest cancels a slow LLM call when the selection or situation changes.
            ui.collectLatest { s ->
                val v = s.selected
                val key = v?.let { "${it.fault.part}|${it.grade}|${it.reasons}" }
                if (key == lastKey) return@collectLatest
                lastKey = key
                if (v == null) {
                    explanation.value = null
                    return@collectLatest
                }
                explanation.value = TemplateExplainer.template(v)
                explanation.value = explainer.explain(v, s.context)
            }
        }
        viewModelScope.launch {
            verdicts.collect { vs ->
                // Feature A (단계적 알림): speak on reaching/worsening grade 3-4 and when it eases; see AlertPolicy.
                val top = vs.firstOrNull()
                AlertPolicy.announcement(lastSpoken, top)?.let { speaker.speak(it) }
                lastSpoken = top?.let { AlertPolicy.Key(it.fault.part, it.grade) }

                // Feature H: log new or worsening faults (real vehicle signals only; demo faults stay out of the history).
                val real = vs.filter { it.fault.source == FaultSource.VEHICLE }
                if (historyLog.observe(real, System.currentTimeMillis())) {
                    historyState.value = historyLog.recent()
                    val snapshot = historyLog.entries
                    launch(Dispatchers.IO) { historyFile.save(snapshot) }
                }
            }
        }
    }

    override fun select(part: Part) {
        selectedPart.value = part
    }

    override fun routeTo(shop: RepairShop) {
        navi.routeTo(shop)
    }

    /** "전체 점검": re-reads every Vehicle SDK signal and walks the categories on screen. */
    override fun runSelfDiagnosis() {
        if (diagnosis.value.running) return
        vehicle.refreshAll()
        viewModelScope.launch {
            for (i in DiagnosisProgress.STEPS.indices) {
                diagnosis.value = DiagnosisProgress(running = true, step = i)
                delay(450)
            }
            diagnosis.update { DiagnosisProgress(running = false, step = DiagnosisProgress.STEPS.size, finishedAt = System.currentTimeMillis()) }
        }
    }

    override fun openReport() {
        val s = ui.value
        report.value = ServiceReport.build(
            verdicts = s.verdicts,
            ctx = s.context,
            signals = s.vehicle.signals,
            history = s.history,
            predictions = s.predictions,
            now = System.currentTimeMillis(),
        )
    }

    override fun closeReport() {
        report.value = null
    }

    /** Grade 4: hand off to the phone dialer (car's Bluetooth phone on AAOS). */
    override fun callRoadside() {
        val intent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$ROADSIDE_NUMBER")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { getApplication<Application>().startActivity(intent) }
    }

    override fun startVoice() = listener.listen()

    override fun voiceText(text: String) {
        viewModelScope.launch { answer(text) }
    }

    /** Feature A2: answer a spoken question with the rule engine's verdict, by voice and on screen. */
    private suspend fun answer(text: String) {
        val s = ui.value
        val reply = when (VoiceCommands.parse(text)) {
            VoiceCommand.STATUS -> VoiceCommands.statusAnswer(s.verdicts, s.context)
            VoiceCommand.ROUTE_TO_SHOP -> {
                val shop = s.shops.firstOrNull()
                if (shop != null && navi.routeTo(shop)) "${shop.name}(으)로 안내를 시작합니다." else "지금은 내비게이션에 연결할 수 없습니다."
            }
            VoiceCommand.REPORT -> if (s.context.isDriving) {
                "리포트는 정차한 뒤에 보여드릴게요."
            } else {
                openReport()
                "정비소 사전 리포트를 열었습니다."
            }
            VoiceCommand.EXPLAIN -> s.selected?.let { v ->
                val e = explanation.value ?: TemplateExplainer.template(v)
                "${e.cause} ${e.risk}"
            } ?: "지금은 설명할 고장이 없습니다."
            VoiceCommand.UNKNOWN -> VoiceCommands.UNKNOWN_ANSWER
        }
        listener.showExchange(text, reply)
        speaker.speak(reply)
        viewModelScope.launch {
            delay(VOICE_BUBBLE_MS)
            listener.clearIf(text)
        }
    }

    // Demo panel passthroughs
    override fun scenario(id: String) = when (id) {
        "A" -> demo.scenarioA()
        "B" -> demo.scenarioB()
        "C" -> demo.scenarioC()
        else -> demo.reset()
    }.let { selectedPart.value = null }

    override fun toggleFault(part: Part) = demo.toggle(part)
    override fun toggleSlowLeak() = demo.toggleSlowLeak()
    override fun setNight(v: Boolean?) = demo.setNight(v)
    override fun setWeather(v: Weather?) = demo.setWeather(v)
    override fun setSpeed(v: Int?) = demo.setSpeed(v)
    override fun setDriving(v: Boolean?) = demo.setDriving(v)
    override fun setRemainingKm(v: Double?) = demo.setRemainingKm(v)
    override fun park() = demo.park()
    fun onLocationPermissionGranted() = environment.start()

    override fun onCleared() {
        vehicle.stop()
        navi.stop()
        speaker.stop()
        listener.stop()
        environment.stop()
    }

    companion object {
        /** 긴급출동 번호(현대차 고객센터로 알고 있으나 미검증 — 제출 전 공식 번호 확인 필요, 기아·제네시스는 별도). */
        const val ROADSIDE_NUMBER = "080-600-6000"
        const val VOICE_BUBBLE_MS = 15_000L
    }
}
