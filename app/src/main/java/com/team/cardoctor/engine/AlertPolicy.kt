package com.team.cardoctor.engine

import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Verdict

/**
 * Feature A (단계적 알림): decides when the app speaks and what it says.
 * - Grade 3-4: speak once when it first appears or gets worse, including the main reason.
 * - Dropping from grade 3-4 to a lower grade: say so once (e.g. it became daytime or the rain stopped).
 * - Grade 1-2 and steady states: stay silent (the screen badge is enough).
 */
object AlertPolicy {

    data class Key(val part: Part, val grade: Grade)

    fun announcement(previous: Key?, top: Verdict?): String? {
        val now = top?.let { Key(it.fault.part, it.grade) }
        if (now == previous) return null

        if (top != null && top.grade.level >= Grade.SERVICE_NOW.level) {
            val worse = previous == null || previous.part != top.fault.part || top.grade.level > previous.grade.level
            if (!worse) return null
            val reason = top.reasons.drop(1).maxByOrNull { it.points }?.let { " ${it.label} 때문에 위험도가 올라갔습니다." } ?: ""
            return "${top.fault.part.label}. ${top.grade.level}등급, ${top.grade.title}.$reason ${top.grade.advice}"
        }

        if (previous != null && previous.grade.level >= Grade.SERVICE_NOW.level) {
            return if (top == null || top.fault.part != previous.part) {
                "${previous.part.label} 경고가 해제되었습니다."
            } else {
                "상황이 바뀌어 ${top.fault.part.label} 등급이 ${top.grade.level}등급으로 낮아졌습니다. ${top.grade.advice}"
            }
        }
        return null
    }

    /**
     * Grade 2 means "목적지 후 정비": when the navigation reports arrival, remind once so the
     * advice is not forgotten. Other grades already had their moment (or are not urgent).
     */
    fun arrivalReminder(verdicts: List<Verdict>): String? {
        val due = verdicts.filter { it.grade == Grade.AFTER_ARRIVAL }
        if (due.isEmpty()) return null
        val names = due.joinToString(", ") { it.fault.part.label }
        return "목적지에 도착했습니다. $names 점검을 오늘 중에 받으세요. 화면에서 가까운 정비소를 확인할 수 있습니다."
    }
}
