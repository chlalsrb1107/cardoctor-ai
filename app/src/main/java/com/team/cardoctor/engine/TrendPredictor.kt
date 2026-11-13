package com.team.cardoctor.engine

import com.team.cardoctor.data.Part

/** One reading of a slowly-changing signal (e.g. tire pressure in psi). */
data class Sample(val timeMillis: Long, val value: Double)

/** "앞 왼쪽 타이어가 약 9일 뒤 경고 기준에 닿을 것으로 보입니다" (feature C: 예측 정비 라이트). */
data class Prediction(val part: Part, val daysLeft: Double, val current: Double, val threshold: Double, val perDay: Double)

/**
 * Least-squares line through recent samples; if the value is falling toward [threshold], estimate
 * when it crosses. Deliberately simple and explainable: no ML, needs at least [MIN_SAMPLES] spanning
 * [MIN_SPAN_HOURS] so one noisy reading cannot raise an alarm.
 */
object TrendPredictor {
    const val MIN_SAMPLES = 4
    const val MIN_SPAN_HOURS = 6.0
    const val HORIZON_DAYS = 14.0
    private const val DAY_MS = 86_400_000.0

    fun predictFalling(part: Part, samples: List<Sample>, threshold: Double): Prediction? {
        if (samples.size < MIN_SAMPLES) return null
        val sorted = samples.sortedBy { it.timeMillis }
        val spanHours = (sorted.last().timeMillis - sorted.first().timeMillis) / 3_600_000.0
        if (spanHours < MIN_SPAN_HOURS) return null

        val t0 = sorted.first().timeMillis
        val xs = sorted.map { (it.timeMillis - t0) / DAY_MS }
        val ys = sorted.map { it.value }
        val mx = xs.average()
        val my = ys.average()
        val sxx = xs.sumOf { (it - mx) * (it - mx) }
        if (sxx == 0.0) return null
        val slope = xs.indices.sumOf { (xs[it] - mx) * (ys[it] - my) } / sxx // value per day
        if (slope >= 0) return null

        val current = my + slope * (xs.last() - mx)
        if (current <= threshold) return null // already below: that is a fault, not a prediction
        val daysLeft = (threshold - current) / slope
        if (daysLeft > HORIZON_DAYS) return null
        return Prediction(part, daysLeft, current, threshold, slope)
    }
}
