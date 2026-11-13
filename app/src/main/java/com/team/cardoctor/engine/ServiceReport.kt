package com.team.cardoctor.engine

import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.HistoryEntry
import com.team.cardoctor.data.Verdict
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Feature B (정비소 사전 리포트): one plain-text sheet the driver hands to the shop (shown as text
 * and as a QR code). It states what the car reported, not a diagnosis, so the shop can quote
 * before the visit and the driver is protected from unnecessary work.
 */
object ServiceReport {

    private val fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm").withZone(ZoneId.of("Asia/Seoul"))

    fun build(
        verdicts: List<Verdict>,
        ctx: DrivingContext,
        signals: Map<String, String>,
        history: List<HistoryEntry>,
        predictions: List<Prediction>,
        now: Long,
        vehicleId: String? = null,
    ): String = buildString {
        appendLine("[카닥터 AI 정비 사전 리포트]")
        appendLine("작성 ${fmt.format(Instant.ofEpochMilli(now))}" + (vehicleId?.let { " · 차량 $it" } ?: ""))
        appendLine("상황 ${if (ctx.isNight) "야간" else "주간"} · ${ctx.weather.label} · ${ctx.speedKmh}km/h")
        appendLine()
        if (verdicts.isEmpty()) {
            appendLine("감지된 고장 없음")
        } else {
            appendLine("감지 항목")
            verdicts.forEach { v ->
                append("- ${v.fault.part.label}: ${v.grade.level}등급 ${v.grade.title}")
                v.fault.detail?.let { append(" (신호 $it)") }
                if (v.fault.source.name == "DEMO") append(" [데모]")
                appendLine()
                appendLine("  근거 " + v.reasons.joinToString(", ") { "${it.label} +${it.points}" })
            }
        }
        if (predictions.isNotEmpty()) {
            appendLine()
            appendLine("예측")
            predictions.forEach { p ->
                appendLine("- ${p.part.label}: 약 ${p.daysLeft.toInt().coerceAtLeast(1)}일 뒤 경고 기준(%.1f) 도달 예상, 현재 %.1f".format(p.threshold, p.current))
            }
        }
        val recent = history.sortedByDescending { it.timeMillis }.take(5)
        if (recent.isNotEmpty()) {
            appendLine()
            appendLine("최근 이력")
            recent.forEach { appendLine("- ${fmt.format(Instant.ofEpochMilli(it.timeMillis))} ${it.part.label} ${it.grade.level}등급") }
        }
        val raw = signals.filterValues { it != "-" && it != "미지원/오류" }
        if (raw.isNotEmpty()) {
            appendLine()
            appendLine("차량 신호 원값")
            raw.toSortedMap().forEach { (k, v) -> appendLine("- $k: $v") }
        }
        appendLine()
        append("이 리포트는 차량 신호 기반 참고 자료이며 최종 진단은 정비사가 합니다.")
    }
}
