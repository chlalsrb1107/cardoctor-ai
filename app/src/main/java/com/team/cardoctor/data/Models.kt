package com.team.cardoctor.data

/** Which part of the car a fault belongs to; the engine's environment weights are keyed on this. */
enum class Category { LIGHTING, WIPER, TIRE, BRAKE, STEERING, COOLING, ENGINE, BATTERY, ENERGY, SAFETY_SYSTEM }

/** Where a part is drawn on the top-view car diagram. */
enum class Zone { HEADLAMP_LEFT, HEADLAMP_RIGHT, HOOD, WINDSHIELD, CABIN, WHEEL_FL, WHEEL_FR, WHEEL_RL, WHEEL_RR, TRUNK }

/**
 * Every fault the app knows how to judge.
 * baseSeverity: 1 = convenience, 2 = degrades safety, 3 = safety-critical (always at least grade 3).
 */
enum class Part(
    val label: String,
    val category: Category,
    val baseSeverity: Int,
    val zones: List<Zone>,
) {
    HEADLAMP_LEFT("좌측 전조등 미점등", Category.LIGHTING, 1, listOf(Zone.HEADLAMP_LEFT)),
    HEADLAMP_RIGHT("우측 전조등 미점등", Category.LIGHTING, 1, listOf(Zone.HEADLAMP_RIGHT)),
    HEADLAMP_SYSTEM("전조등(LED) 시스템 경고", Category.LIGHTING, 1, listOf(Zone.HEADLAMP_LEFT, Zone.HEADLAMP_RIGHT)),
    WIPER("와이퍼 작동 불량", Category.WIPER, 1, listOf(Zone.WINDSHIELD)),
    WASHER_FLUID("워셔액 부족", Category.WIPER, 1, listOf(Zone.WINDSHIELD)),
    TIRE_FL("앞 왼쪽 타이어 공기압 저하", Category.TIRE, 2, listOf(Zone.WHEEL_FL)),
    TIRE_FR("앞 오른쪽 타이어 공기압 저하", Category.TIRE, 2, listOf(Zone.WHEEL_FR)),
    TIRE_RL("뒤 왼쪽 타이어 공기압 저하", Category.TIRE, 2, listOf(Zone.WHEEL_RL)),
    TIRE_RR("뒤 오른쪽 타이어 공기압 저하", Category.TIRE, 2, listOf(Zone.WHEEL_RR)),
    TPMS("타이어 공기압 경고(TPMS)", Category.TIRE, 2, listOf(Zone.WHEEL_FL, Zone.WHEEL_FR, Zone.WHEEL_RL, Zone.WHEEL_RR)),
    COOLANT_OVERHEAT("냉각수 과열", Category.COOLING, 3, listOf(Zone.HOOD)),
    ENGINE_OIL_PRESSURE("엔진오일 압력 경고", Category.ENGINE, 3, listOf(Zone.HOOD)),
    ENGINE_OIL_LOW("엔진오일 부족", Category.ENGINE, 2, listOf(Zone.HOOD)),
    ENGINE_CHECK("엔진 점검 경고", Category.ENGINE, 2, listOf(Zone.HOOD)),
    BRAKE_WARNING("브레이크 경고", Category.BRAKE, 3, listOf(Zone.WHEEL_FL, Zone.WHEEL_FR, Zone.WHEEL_RL, Zone.WHEEL_RR)),
    BRAKE_FLUID_LOW("브레이크액 부족", Category.BRAKE, 3, listOf(Zone.HOOD)),
    ABS("ABS 경고", Category.BRAKE, 2, listOf(Zone.WHEEL_FL, Zone.WHEEL_FR, Zone.WHEEL_RL, Zone.WHEEL_RR)),
    ESC("차체자세제어(ESC) 경고", Category.BRAKE, 2, listOf(Zone.CABIN)),
    STEERING_MDPS("전동 조향(MDPS) 경고", Category.STEERING, 3, listOf(Zone.WHEEL_FL, Zone.WHEEL_FR)),
    BATTERY_12V("12V 배터리·충전 경고", Category.BATTERY, 2, listOf(Zone.HOOD)),
    EV_SYSTEM("전기차 시스템 경고", Category.BATTERY, 3, listOf(Zone.CABIN)),
    EV_BATTERY_LOW("구동 배터리 잔량 부족", Category.BATTERY, 2, listOf(Zone.CABIN)),
    FUEL_LOW("연료 부족", Category.ENERGY, 1, listOf(Zone.TRUNK)),
    AIRBAG("에어백 경고", Category.SAFETY_SYSTEM, 2, listOf(Zone.CABIN)),
}

enum class Weather(val label: String, val isPrecipitation: Boolean) {
    CLEAR("맑음", false),
    CLOUDY("흐림", false),
    FOG("안개", false),
    RAIN("비", true),
    SNOW("눈", true),
}

enum class FaultSource { VEHICLE, DEMO }

data class Fault(
    val part: Part,
    val source: FaultSource,
    /** Optional raw signal shown on the detail screen, e.g. "24.1 psi". */
    val detail: String? = null,
)

/** The driving situation the engine judges a fault against. */
data class DrivingContext(
    val isNight: Boolean,
    val weather: Weather,
    val speedKmh: Int,
    /** Distance left to the navigation destination; null when no route is active. */
    val remainingKm: Double?,
    val isDriving: Boolean,
) {
    val isHighSpeed: Boolean get() = speedKmh >= HIGH_SPEED_KMH

    companion object {
        const val HIGH_SPEED_KMH = 80
        const val LONG_TRIP_KM = 50.0
        val DEFAULT = DrivingContext(isNight = false, weather = Weather.CLEAR, speedKmh = 0, remainingKm = null, isDriving = false)
    }
}

enum class Grade(val level: Int, val title: String, val advice: String) {
    OK(0, "정상", "점검이 필요한 항목이 없습니다."),
    RELAXED(1, "여유 있게 정비", "주말이나 다음 정기점검 때 방문하세요."),
    AFTER_ARRIVAL(2, "목적지 후 정비", "목적지까지는 운행 가능합니다. 오늘 중 점검을 권장합니다."),
    SERVICE_NOW(3, "즉시 정비", "가장 가까운 정비소로 바로 가세요."),
    STOP_NOW(4, "즉시 정차", "안전한 곳에 정차하고 견인·긴급출동을 부르세요."),
}

/** One line of "why this grade": shown on screen so the judgement is explainable. */
data class Reason(val label: String, val points: Int)

data class Verdict(
    val fault: Fault,
    val grade: Grade,
    val score: Int,
    val reasons: List<Reason>,
    /** True when the safety-critical floor (base severity 3 -> at least grade 3) decided the grade. */
    val flooredBySafety: Boolean,
)
