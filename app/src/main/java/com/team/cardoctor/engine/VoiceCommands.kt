package com.team.cardoctor.engine

import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Verdict

/** What the driver asked by voice (feature A2). Keyword rules keep it predictable and offline. */
enum class VoiceCommand { STATUS, ROUTE_TO_SHOP, REPORT, EXPLAIN, UNKNOWN }

object VoiceCommands {

    private val ROUTE = listOf("정비소", "카센터", "길 안내", "길안내", "안내해", "데려다")
    private val REPORT = listOf("리포트", "보고서", "견적")
    private val EXPLAIN = listOf("왜", "이유", "무슨 뜻", "뭐야", "설명")
    private val STATUS = listOf("가야", "가도", "괜찮", "위험", "상태", "어때", "문제", "고장", "점검")

    fun parse(text: String): VoiceCommand {
        val t = text.replace(" ", "").ifBlank { return VoiceCommand.UNKNOWN }
        fun has(words: List<String>) = words.any { t.contains(it.replace(" ", "")) }
        return when {
            has(ROUTE) && !t.contains("가야") -> VoiceCommand.ROUTE_TO_SHOP
            has(REPORT) -> VoiceCommand.REPORT
            has(EXPLAIN) -> VoiceCommand.EXPLAIN
            has(STATUS) -> VoiceCommand.STATUS
            else -> VoiceCommand.UNKNOWN
        }
    }

    /** Short spoken answer to "지금 가야 돼?": grade first, then the one action. */
    fun statusAnswer(verdicts: List<Verdict>, ctx: DrivingContext): String {
        val top = verdicts.firstOrNull() ?: return "지금은 점검이 필요한 항목이 없습니다. 안심하고 운행하세요."
        val others = verdicts.size - 1
        val head = "${top.fault.part.label}, ${top.grade.level}등급 ${top.grade.title}입니다."
        val why = top.reasons.drop(1).firstOrNull()?.let { " ${it.label} 때문에 위험도가 올라갔습니다." } ?: ""
        val tail = if (others > 0) " 그 밖에 ${others}개 항목이 더 있습니다." else ""
        val action = when (top.grade) {
            Grade.STOP_NOW -> " 비상등을 켜고 안전한 곳에 정차하세요."
            Grade.SERVICE_NOW -> " 가장 가까운 정비소로 안내할까요? '정비소 안내해줘'라고 말씀하세요."
            Grade.AFTER_ARRIVAL -> if (ctx.isDriving) " 목적지까지는 가셔도 됩니다." else " 오늘 중에 점검하세요."
            Grade.RELAXED -> " 급하지 않습니다. 주말에 점검하세요."
            Grade.OK -> ""
        }
        return head + why + action + tail
    }

    const val UNKNOWN_ANSWER = "잘 못 알아들었어요. '지금 가야 돼?', '정비소 안내해줘', '왜 위험해?'처럼 말씀해 주세요."
}
