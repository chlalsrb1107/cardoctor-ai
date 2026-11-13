package com.team.cardoctor.ai

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.messages.BetaOutputConfig
import com.anthropic.models.beta.messages.BetaStopReason
import com.anthropic.models.beta.messages.MessageCreateParams
import com.team.cardoctor.data.Category
import com.team.cardoctor.data.DrivingContext
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Verdict
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Plain-language explanation shown under a verdict. The grade itself always comes from RuleEngine. */
data class Explanation(val cause: String, val risk: String, val action: String, val byAi: Boolean)

interface Explainer {
    suspend fun explain(verdict: Verdict, ctx: DrivingContext): Explanation
}

/** Offline wording per category; also the fallback whenever the LLM is unavailable. */
object TemplateExplainer : Explainer {
    override suspend fun explain(verdict: Verdict, ctx: DrivingContext): Explanation = template(verdict)

    fun template(verdict: Verdict): Explanation {
        val part = verdict.fault.part
        val (cause, risk) = when (part.category) {
            Category.LIGHTING -> "전구·LED 모듈 또는 배선 문제로 ${part.label} 상태입니다." to "어두울 때 시야가 줄고, 상대 차량이 내 차를 늦게 발견합니다. 야간 단속 대상입니다."
            Category.WIPER -> "와이퍼 모터·블레이드 또는 워셔액 문제입니다." to "비나 눈이 오면 앞 유리 시야를 확보할 수 없습니다."
            Category.TIRE -> "타이어 공기가 빠졌거나 펑크가 났을 수 있습니다." to "제동거리가 늘고, 젖은 노면이나 고속에서 미끄러지거나 타이어가 터질 수 있습니다."
            Category.BRAKE -> "제동 계통(브레이크액·ABS·주차 브레이크) 이상 신호입니다." to "제동력이 떨어지거나 급제동 때 차가 흔들릴 수 있습니다."
            Category.STEERING -> "전동 조향 장치(MDPS)에 이상이 감지됐습니다." to "핸들이 갑자기 무거워져 조향이 어려울 수 있습니다."
            Category.COOLING -> "냉각수 부족 또는 냉각팬·수온 조절 문제로 엔진 온도가 높습니다." to "계속 달리면 엔진이 손상되어 큰 수리비가 들 수 있습니다."
            Category.ENGINE -> "엔진오일 부족·압력 저하 또는 엔진 제어 이상 신호입니다." to "윤활이 부족하면 엔진 내부가 빠르게 손상됩니다."
            Category.BATTERY -> "배터리 충전 계통 또는 구동 배터리 상태 이상입니다." to "주행 중 전원이 꺼지거나 목적지까지 가지 못할 수 있습니다."
            Category.ENERGY -> "남은 연료가 적습니다." to "주유소를 찾기 어려운 구간에서 멈출 수 있습니다."
            Category.SAFETY_SYSTEM -> "에어백 시스템 자기진단에서 이상이 감지됐습니다." to "사고 때 에어백이 터지지 않을 수 있습니다."
        }
        return Explanation(cause, risk, actionFor(verdict.grade), byAi = false)
    }

    fun actionFor(grade: Grade): String = when (grade) {
        Grade.STOP_NOW -> "비상등을 켜고 안전한 곳에 정차한 뒤 긴급출동을 부르세요."
        Grade.SERVICE_NOW -> "가장 가까운 정비소로 바로 이동하세요. 아래 버튼으로 내비 안내를 시작할 수 있습니다."
        Grade.AFTER_ARRIVAL -> "목적지까지는 운행해도 됩니다. 오늘 안에 점검을 받으세요."
        Grade.RELAXED -> "급하지 않습니다. 주말이나 다음 정기점검 때 함께 점검하세요."
        Grade.OK -> "할 일이 없습니다."
    }
}

/**
 * Claude writes the three lines (원인 / 위험 / 할 일) in plain Korean from the verdict the rule
 * engine already decided. Any failure, refusal or missing key falls back to [TemplateExplainer].
 */
class ClaudeExplainer(apiKey: String) : Explainer {

    private val client: AnthropicClient = AnthropicOkHttpClient.builder().apiKey(apiKey).build()
    private val cache = mutableMapOf<String, Explanation>()

    override suspend fun explain(verdict: Verdict, ctx: DrivingContext): Explanation {
        val key = "${verdict.fault.part}|${verdict.grade}|${verdict.reasons.joinToString { it.label }}"
        synchronized(cache) { cache[key] }?.let { return it }
        val result = withContext(Dispatchers.IO) { runCatching { call(verdict, ctx) }.getOrNull() }
            ?: return TemplateExplainer.template(verdict)
        synchronized(cache) { cache[key] = result }
        return result
    }

    private fun call(verdict: Verdict, ctx: DrivingContext): Explanation? {
        val params = MessageCreateParams.builder()
            .model("claude-opus-5-5")
            .maxTokens(2048L)
            // Server-side fallback: if a safety classifier declines, the API retries on a fallback model.
            .addBeta("server-side-fallback-2026-07-01")
            .putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
            .outputConfig(BetaOutputConfig.builder().effort(BetaOutputConfig.Effort.LOW).build())
            .system(SYSTEM_PROMPT)
            .addUserMessage(userPrompt(verdict, ctx))
            .build()

        val message = client.beta().messages().create(params)
        if (message.stopReason().orElse(null) == BetaStopReason.REFUSAL) return null
        val text = message.content().mapNotNull { block -> block.text().orElse(null)?.text() }.joinToString("\n")
        return parse(text)
    }

    private fun userPrompt(v: Verdict, ctx: DrivingContext): String = buildString {
        appendLine("고장: ${v.fault.part.label}")
        v.fault.detail?.let { appendLine("신호: $it") }
        appendLine("판정 등급: ${v.grade.level}등급 ${v.grade.title} (점수 ${v.score})")
        appendLine("판정 근거: " + v.reasons.joinToString { "${it.label} +${it.points}" })
        appendLine("상황: ${if (ctx.isNight) "야간" else "주간"}, ${ctx.weather.label}, ${ctx.speedKmh}km/h, ${if (ctx.isDriving) "주행 중" else "정차 중"}")
        append("권장 행동(바꾸지 말 것): ${v.grade.advice}")
    }

    internal companion object {
        val SYSTEM_PROMPT = """
            너는 차량 대시보드에 표시될 정비 안내 문장을 쓴다. 읽는 사람은 자동차를 잘 모르는 운전자다.
            등급과 권장 행동은 이미 정해져 있으니 바꾸거나 더 약하게 말하지 마라.
            정확히 아래 세 줄만 출력한다. 각 줄은 40자 안팎의 쉬운 한국어 한 문장이다.
            원인: <무엇이 왜 문제인지>
            위험: <지금 상황에서 무엇이 위험한지>
            할 일: <운전자가 지금 할 행동>
        """.trimIndent()

        fun parse(text: String): Explanation? {
            fun line(prefix: String) = text.lines().map { it.trim() }.firstOrNull { it.startsWith(prefix) }
                ?.removePrefix(prefix)?.trim()?.removePrefix(":")?.trim()?.takeIf { it.isNotEmpty() }
            val cause = line("원인") ?: return null
            val risk = line("위험") ?: return null
            val action = line("할 일") ?: line("할일") ?: return null
            return Explanation(cause, risk, action, byAi = true)
        }
    }
}

fun explainerFor(apiKey: String): Explainer = if (apiKey.isBlank()) TemplateExplainer else ClaudeExplainer(apiKey)
