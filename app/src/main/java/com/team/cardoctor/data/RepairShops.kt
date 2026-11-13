package com.team.cardoctor.data

import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

data class RepairShop(
    val id: String,
    val name: String,
    val address: String,
    val lat: Double,
    val lng: Double,
    val distanceKm: Double = 0.0,
    val handles: Set<Category>,
)

/**
 * v1 uses a fixed sample list placed around the current position so the demo works offline.
 * Replace with a real POI search (e.g. Kakao Local API) in v2; the UI labels these as 예시.
 */
object RepairShops {

    private data class Seed(val name: String, val dLat: Double, val dLng: Double, val handles: Set<Category>)

    private val seeds = listOf(
        Seed("블루핸즈 예시 1호점", 0.012, 0.015, Category.entries.toSet()),
        Seed("타이어·브레이크 전문점 (예시)", -0.018, 0.006, setOf(Category.TIRE, Category.BRAKE, Category.STEERING)),
        Seed("전기차 전문 정비소 (예시)", 0.025, -0.020, setOf(Category.BATTERY, Category.ENERGY, Category.COOLING)),
        Seed("라이트·와이퍼 퀵서비스 (예시)", -0.006, -0.011, setOf(Category.LIGHTING, Category.WIPER)),
        Seed("종합정비 예시 2호점", 0.040, 0.030, Category.entries.toSet()),
    )

    fun near(lat: Double, lng: Double, category: Category?): List<RepairShop> =
        seeds.mapIndexed { i, s ->
            val sLat = lat + s.dLat
            val sLng = lng + s.dLng
            RepairShop(
                id = "sample-$i",
                name = s.name,
                address = "현재 위치 기준 예시 주소",
                lat = sLat,
                lng = sLng,
                distanceKm = haversineKm(lat, lng, sLat, sLng),
                handles = s.handles,
            )
        }
            .filter { category == null || category in it.handles }
            .sortedBy { it.distanceKm }

    fun haversineKm(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val r = 6371.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLng = Math.toRadians(lng2 - lng1)
        val a = sin(dLat / 2) * sin(dLat / 2) +
            cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLng / 2) * sin(dLng / 2)
        return 2 * r * atan2(sqrt(a), sqrt(1 - a))
    }
}
