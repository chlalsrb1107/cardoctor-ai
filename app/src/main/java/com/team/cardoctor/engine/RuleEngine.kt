package com.team.cardoctor.engine

import com.team.cardoctor.data.Category
import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Fault
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Reason
import com.team.cardoctor.data.Verdict
import com.team.cardoctor.data.Weather

/**
 * Decides the repair-urgency grade. Pure Kotlin and deterministic: the LLM never changes a grade.
 *
 * urgency = base severity (1..3) + sum of environment weights (0..3)
 *   >= 6 -> grade 4, 4..5 -> grade 3, 2..3 -> grade 2, 1 -> grade 1
 * Base severity 3 parts are always at least grade 3.
 */
object RuleEngine {

    fun evaluate(fault: Fault, ctx: DrivingContext): Verdict {
        val part = fault.part
        val reasons = mutableListOf(Reason("기본 심각도 (${part.label})", part.baseSeverity))
        reasons += environmentWeights(part.category, ctx)

        val score = reasons.sumOf { it.points }
        val byScore = gradeFor(score)
        val floored = part.baseSeverity >= 3 && byScore.level < Grade.SERVICE_NOW.level
        val grade = if (floored) Grade.SERVICE_NOW else byScore
        return Verdict(fault, grade, score, reasons, floored)
    }

    fun evaluateAll(faults: List<Fault>, ctx: DrivingContext): List<Verdict> =
        faults.map { evaluate(it, ctx) }
            .sortedWith(compareByDescending<Verdict> { it.grade.level }.thenByDescending { it.score })

    fun overall(verdicts: List<Verdict>): Grade = verdicts.maxByOrNull { it.grade.level }?.grade ?: Grade.OK

    /** "같은 고장, 다른 상황": the grade this fault would get in a few typical situations. */
    fun whatIf(fault: Fault): List<Pair<String, Grade>> {
        val base = DrivingContext(isNight = false, weather = Weather.CLEAR, speedKmh = 50, remainingKm = null, isDriving = true)
        return listOf(
            "낮·맑음" to base,
            "밤" to base.copy(isNight = true),
            "비" to base.copy(weather = Weather.RAIN),
            "빗길 고속" to base.copy(weather = Weather.RAIN, speedKmh = 100),
            "밤·빗길 고속" to base.copy(isNight = true, weather = Weather.RAIN, speedKmh = 100),
        ).map { (label, ctx) -> label to evaluate(fault, ctx).grade }
    }

    /** Which [whatIf] column matches the current situation (null for e.g. daytime rain at low speed in fog). */
    fun whatIfLabel(ctx: DrivingContext): String? {
        val rain = ctx.weather.isPrecipitation
        return when {
            ctx.isNight && rain && ctx.isHighSpeed -> "밤·빗길 고속"
            !ctx.isNight && rain && ctx.isHighSpeed -> "빗길 고속"
            !ctx.isNight && rain -> "비"
            ctx.isNight && !rain -> "밤"
            !ctx.isNight && !rain -> "낮·맑음"
            else -> null
        }
    }

    fun gradeFor(score: Int): Grade = when {
        score >= 6 -> Grade.STOP_NOW
        score >= 4 -> Grade.SERVICE_NOW
        score >= 2 -> Grade.AFTER_ARRIVAL
        score >= 1 -> Grade.RELAXED
        else -> Grade.OK
    }

    internal fun environmentWeights(category: Category, ctx: DrivingContext): List<Reason> = buildList {
        if (ctx.isNight && category == Category.LIGHTING) add(Reason("야간 운행", 3))

        if (ctx.weather.isPrecipitation) {
            when (category) {
                Category.WIPER -> add(Reason("${ctx.weather.label} — 시야 확보 필요", 3))
                Category.BRAKE -> add(Reason("${ctx.weather.label} — 제동거리 증가", 3))
                Category.TIRE -> add(Reason("${ctx.weather.label} — 노면 미끄러움", 2))
                else -> Unit
            }
        }

        if (ctx.isHighSpeed && category in HIGH_SPEED_SENSITIVE) {
            add(Reason("고속 주행 (${ctx.speedKmh}km/h)", 1))
        }

        val remaining = ctx.remainingKm
        if (remaining != null && remaining >= DrivingContext.LONG_TRIP_KM) {
            when (category) {
                Category.BATTERY -> add(Reason("목적지까지 ${remaining.toInt()}km 남음", 2))
                Category.COOLING, Category.ENGINE -> add(Reason("목적지까지 ${remaining.toInt()}km 남음", 1))
                else -> Unit
            }
        }
    }

    private val HIGH_SPEED_SENSITIVE = setOf(Category.TIRE, Category.BRAKE, Category.STEERING)
}
