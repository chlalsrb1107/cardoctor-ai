package com.team.cardoctor.sdk

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.RouteOption
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import android.util.Log
import com.team.cardoctor.data.RepairShop
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Pleos NaviHelper: sends the chosen repair shop to the car's own navigation as the destination,
 * and reads the distance left to the current destination (a weight in the rule engine).
 */
class NaviSource(context: Context) {

    private val navi = NaviHelper(context.applicationContext)
    private var ready = false

    private val _remainingKm = MutableStateFlow<Double?>(null)
    val remainingKm: StateFlow<Double?> = _remainingKm.asStateFlow()

    private val _arrivals = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    /** Emits when the car's navigation reports arrival at the destination. */
    val arrivals: SharedFlow<Unit> = _arrivals.asSharedFlow()

    private val _status = MutableStateFlow<String?>(null)
    val status: StateFlow<String?> = _status.asStateFlow()

    private val listener = object : NaviHelperEventListener {
        override fun onDrivingInfo(info: DrivingInfo) {
            // DrivingState.distance is meters to the destination.
            _remainingKm.value = info.destination?.distance?.let { it / 1000.0 }
        }
        override fun onRouteStarted(info: ai.pleos.playground.navi.data.RouteStartInfo) {
            _status.value = "내비 안내를 시작했습니다"
        }
        override fun onDestinationArrived(info: ai.pleos.playground.navi.data.DestinationArrivedInfo) {
            _remainingKm.value = null
            _arrivals.tryEmit(Unit)
        }
        override fun onRouteEnded() { _remainingKm.value = null }
        override fun onRouteCancelled() { _remainingKm.value = null }
        override fun onError(code: NaviErrorCode) {
            _status.value = "내비 오류: ${code.name}"
        }
    }

    fun start() {
        try {
            navi.initialize(
                {
                    ready = true
                    navi.addListener(listener)
                    navi.getRouteStateInfo()
                },
                { e -> Log.w(TAG, "NaviHelper init failed: ${e.message}") },
            )
        } catch (e: Exception) {
            Log.w(TAG, "NaviHelper unavailable: ${e.message}")
        }
    }

    /** @return false when the navigation service is not reachable (e.g. plain AAOS emulator). */
    fun routeTo(shop: RepairShop): Boolean {
        if (!ready) {
            _status.value = "내비 서비스에 연결되지 않았습니다 (에뮬레이터 확인)"
            return false
        }
        return try {
            navi.requestRoute(
                RouteInfo(
                    shop.lat, shop.lng,
                    shop.lat, shop.lng,
                    shop.name, shop.id, shop.address, "",
                    RouteOption.RECOMMENDED, "", false,
                )
            )
            _status.value = "${shop.name}(으)로 경로를 요청했습니다"
            true
        } catch (e: Exception) {
            _status.value = "경로 요청 실패: ${e.message}"
            false
        }
    }

    fun stop() {
        if (!ready) return
        try {
            navi.removeListener(listener)
            navi.release()
        } catch (_: Exception) {
        }
        ready = false
    }

    private companion object { const val TAG = "NaviSource" }
}
