package com.team.cardoctor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.cardoctor.ai.Explanation
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.RepairShop
import com.team.cardoctor.data.Verdict

/** Width the layout was designed at; larger car displays scale the whole screen up to fill. */
private val DESIGN_WIDTH = 1408.dp

@Composable
fun MainScreen(state: UiState, vm: UiActions, initialDemoOpen: Boolean = false) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val factor = (maxWidth / DESIGN_WIDTH).coerceIn(1f, 1.6f)
        val base = LocalDensity.current
        CompositionLocalProvider(LocalDensity provides Density(base.density * factor, base.fontScale)) {
            MainScreenContent(state, vm, initialDemoOpen)
        }
    }
}

@Composable
private fun MainScreenContent(state: UiState, vm: UiActions, initialDemoOpen: Boolean) {
    var demoOpen by remember { mutableStateOf(initialDemoOpen) }
    val driving = state.context.isDriving
    val verdict = if (state.diagnosis.running) null else state.selected

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            TopBar(state, onTitleLongPress = { demoOpen = !demoOpen }, onDiagnose = vm::runSelfDiagnosis, onVoice = vm::startVoice)
            Spacer(Modifier.height(16.dp))
            if (state.voice.listening || state.voice.transcript != null) {
                VoiceBubble(state)
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxSize()) {
                // Left: the car is naturally tall, so it gets the full height of its own panel.
                Column(
                    Modifier.weight(0.32f).fillMaxHeight()
                        .background(Palette.surface, RoundedCornerShape(28.dp)).padding(vertical = 24.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("차량 상태", color = Palette.textDim, fontSize = 18.sp, modifier = Modifier.align(Alignment.Start).padding(start = 8.dp))
                    CarDiagram(
                        verdicts = state.verdicts,
                        selected = state.selected?.fault?.part,
                        onSelect = vm::select,
                        modifier = Modifier.weight(1f).fillMaxWidth().padding(vertical = 12.dp),
                    )
                    state.selected?.let {
                        val c = Palette.forGrade(it.grade)
                        Box(Modifier.background(c.copy(alpha = 0.14f), RoundedCornerShape(50)).padding(horizontal = 18.dp, vertical = 8.dp)) {
                            Text(
                                it.fault.part.label, color = c, fontSize = 18.sp, fontWeight = FontWeight.SemiBold,
                                maxLines = 2, textAlign = TextAlign.Center, lineHeight = 24.sp,
                            )
                        }
                    }
                    Legend()
                }
                Spacer(Modifier.width(20.dp))
                // Right: the verdict on top, the next action (shops, emergency) pinned to the bottom edge
                // so both columns end on the same line; scrolls only when the content is taller than the screen.
                // Few cards (all clear, or a single fault while driving): the cards share the height,
                // so a tall display shows a bigger verdict instead of a gap. Otherwise: scroll.
                val fill = when {
                    state.diagnosis.running -> false
                    verdict == null -> state.predictions.isEmpty() && (driving || state.history.isEmpty())
                    else -> driving && verdict.grade != Grade.STOP_NOW && state.verdicts.size <= 1
                }
                if (fill) {
                    Column(Modifier.weight(0.68f).fillMaxHeight()) {
                        HeroCard(
                            verdict = verdict,
                            exp = state.explanation,
                            driving = driving,
                            recurringDays = verdict?.let { state.recurring[it.fault.part] },
                            summary = allClearSummary(state),
                            modifier = if (verdict != null) Modifier.weight(1f) else Modifier,
                        )
                        Spacer(Modifier.height(16.dp))
                        if (verdict == null) {
                            CheckGridCard(Modifier.weight(1f), stretch = true)
                        } else {
                            ShopsCard(state.shops, state.shopsAreSamples, driving, state.naviStatus, vm::routeTo, onReport = null)
                        }
                    }
                } else
                BoxWithConstraints(Modifier.weight(0.68f).fillMaxHeight()) {
                    Column(
                        Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = maxHeight),
                        verticalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Column {
                            HeroCard(
                                verdict = verdict,
                                exp = state.explanation,
                                driving = driving,
                                recurringDays = verdict?.let { state.recurring[it.fault.part] },
                                summary = allClearSummary(state),
                            )
                            when {
                                state.diagnosis.running -> Gap { DiagnosisCard(state) }
                                verdict == null -> {
                                    if (state.predictions.isNotEmpty()) Gap { PredictionsCard(state) }
                                    if (!driving && state.history.isNotEmpty()) Gap { HistoryCard(state) }
                                }
                                !driving -> {
                                    state.explanation?.let { Gap { ExplainCard(it) } }
                                    Gap { WhatIfCard(verdict, state.context) }
                                }
                            }
                        }
                        if (verdict != null) {
                            Column {
                                if (verdict.grade == Grade.STOP_NOW) Gap { EmergencyCard(vm::callRoadside) }
                                if (verdict.grade.level >= Grade.SERVICE_NOW.level || !driving) {
                                    Gap {
                                        ShopsCard(
                                            state.shops, state.shopsAreSamples, driving, state.naviStatus, vm::routeTo,
                                            onReport = if (driving) null else vm::openReport,
                                        )
                                    }
                                }
                                if (state.verdicts.size > 1) {
                                    Gap { OtherFaults(state.verdicts, verdict, enabled = !driving, onSelect = vm::select) }
                                }
                                if (!driving && state.predictions.isNotEmpty()) Gap { PredictionsCard(state) }
                            }
                        } else if (!state.diagnosis.running) {
                            Column { Gap { CheckGridCard() } }
                        }
                    }
                }
            }
        }

        state.report?.let { ReportSheet(it, vm::closeReport) }

        AnimatedVisibility(
            visible = demoOpen,
            enter = slideInHorizontally { it },
            exit = slideOutHorizontally { it },
            modifier = Modifier.align(Alignment.CenterEnd),
        ) {
            DemoPanel(state, vm, onClose = { demoOpen = false })
        }
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun TopBar(state: UiState, onTitleLongPress: () -> Unit, onDiagnose: () -> Unit, onVoice: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        // Long-press anywhere on the logo + title opens the demo panel.
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onTitleLongPress),
        ) {
            Box(
                Modifier.size(44.dp).background(Palette.accent.copy(alpha = 0.16f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) { Icon(Icons.Filled.Build, contentDescription = null, tint = Palette.info, modifier = Modifier.size(24.dp)) }
            Spacer(Modifier.width(12.dp))
            Text("카닥터 AI", color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.width(20.dp))
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            val ctx = state.context
            Chip(if (ctx.isNight) "야간" else "주간", dot = if (ctx.isNight) Palette.info else Palette.caution)
            Chip(ctx.weather.label + if (state.demo.weather == null && !state.environment.weatherFetched) " (기본값)" else "", dot = Palette.textDim)
            Chip("${ctx.speedKmh} km/h · " + if (ctx.isDriving) "주행 중" else "정차 중", dot = if (ctx.isDriving) Palette.accent else Palette.textDim)
            ctx.remainingKm?.let { Chip("목적지 ${it.toInt()} km", dot = Palette.textDim) }
            if (state.demo.scenario != null || state.demo.injected.isNotEmpty()) Chip("데모 ${state.demo.scenario ?: "수동"}", highlight = true)
            if (!state.vehicle.connected) Chip("차량 SDK 미연결", dot = Palette.caution)
        }
        Spacer(Modifier.width(12.dp))
        // Full check needs the car parked; it stays visible but quiet.
        Box(
            Modifier.height(56.dp).border(1.5.dp, Palette.line, RoundedCornerShape(50))
                .clickable(enabled = !state.context.isDriving && !state.diagnosis.running, onClick = onDiagnose)
                .padding(horizontal = 22.dp),
            contentAlignment = Alignment.Center,
        ) {
            val enabled = !state.context.isDriving && !state.diagnosis.running
            Text("전체 점검", color = if (enabled) Palette.text else Palette.textDim, fontSize = 20.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.width(12.dp))
        // Voice works while driving: it is the safe way to ask "지금 가야 돼?".
        val listening = state.voice.listening
        Box(
            Modifier.size(64.dp)
                .background(if (listening) Palette.danger.copy(alpha = 0.22f) else Palette.accent.copy(alpha = 0.18f), CircleShape)
                .clickable(enabled = !listening, onClick = onVoice)
                .semantics { contentDescription = if (listening) "듣는 중" else "음성 질문" },
            contentAlignment = Alignment.Center,
        ) { MicGlyph(if (listening) Palette.danger else Palette.info) }
    }
}

/** A small microphone drawn in place: the core icon set has no mic. */
@Composable
private fun MicGlyph(color: Color) {
    Canvas(Modifier.size(28.dp)) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.09f
        drawRoundRect(color, Offset(w * 0.34f, h * 0.06f), Size(w * 0.32f, h * 0.54f), CornerRadius(w * 0.16f))
        drawArc(color, 0f, 180f, false, Offset(w * 0.20f, h * 0.22f), Size(w * 0.60f, h * 0.52f), style = Stroke(stroke, cap = StrokeCap.Round))
        drawLine(color, Offset(w * 0.5f, h * 0.74f), Offset(w * 0.5f, h * 0.92f), stroke, StrokeCap.Round)
        drawLine(color, Offset(w * 0.34f, h * 0.93f), Offset(w * 0.66f, h * 0.93f), stroke, StrokeCap.Round)
    }
}

/**
 * The verdict at a glance: a ring that fills with the urgency score (out of 6, where grade 4 starts)
 * with the grade inside, next to what to do now and the score breakdown.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun HeroCard(
    verdict: Verdict?,
    exp: Explanation?,
    driving: Boolean,
    recurringDays: Int?,
    summary: String?,
    modifier: Modifier = Modifier,
) {
    val grade = verdict?.grade ?: Grade.OK
    val c = Palette.forGrade(grade)
    BoxWithConstraints(
        modifier.fillMaxWidth()
            .background(Palette.surface, RoundedCornerShape(28.dp))
            .background(c.copy(alpha = 0.07f), RoundedCornerShape(28.dp)),
        contentAlignment = Alignment.Center,
    ) {
    // When the card is given extra height, the ring grows with it (and stays round).
    val ring = if (constraints.hasBoundedHeight) (maxHeight - 56.dp).coerceIn(184.dp, 320.dp) else 184.dp
    Row(
        Modifier.fillMaxWidth().padding(28.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GradeRing(verdict, c, Modifier.size(ring))
        Spacer(Modifier.width(32.dp))
        Column(Modifier.weight(1f)) {
            if (verdict == null) {
                Text("모든 항목 정상", color = Palette.ok, fontSize = 34.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("경고등·타이어·오일류·워셔액 신호에서 이상이 없습니다.", color = Palette.text, fontSize = 21.sp, lineHeight = 29.sp)
                summary?.let {
                    Spacer(Modifier.height(10.dp))
                    Text(it, color = Palette.textDim, fontSize = 18.sp)
                }
            } else {
                Text("${grade.level}등급 · ${grade.title} · ${verdict.fault.part.label}", color = c, fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                Text(grade.advice, color = Palette.text, fontSize = 34.sp, fontWeight = FontWeight.Bold, lineHeight = 42.sp)
                if (driving && exp != null) {
                    // While moving, add only the "why" under the action.
                    Spacer(Modifier.height(8.dp))
                    Text(exp.risk, color = Palette.textDim, fontSize = 21.sp, lineHeight = 29.sp)
                }
                Spacer(Modifier.height(16.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    verdict.reasons.forEach { r ->
                        val fromPart = r.label.startsWith("기본")
                        ScorePill(
                            label = if (fromPart) r.label.removePrefix("기본 심각도 (").removeSuffix(")") else r.label,
                            points = "+${r.points}",
                            tint = if (fromPart) c else Palette.info,
                        )
                    }
                    if (verdict.flooredBySafety) ScorePill("안전 핵심 부품", "최소 3등급", Palette.caution)
                    recurringDays?.let { ScorePill("최근 30일 중 ${it}일 반복", "점검 권장", Palette.caution) }
                }
                verdict.fault.detail?.let {
                    Spacer(Modifier.height(10.dp))
                    Text("차량 신호: $it", color = Palette.textDim, fontSize = 16.sp)
                }
                if (driving) {
                    Text(
                        "주행 중에는 요약만 표시합니다 · 최종 판단은 정비 전문가에게 확인하세요",
                        color = Palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun GradeRing(verdict: Verdict?, c: Color, modifier: Modifier) {
    val fraction = if (verdict == null) 1f else verdict.score.coerceIn(0, MAX_SCORE).toFloat() / MAX_SCORE
    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val k = maxWidth.value / 184f
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 16.dp.toPx() * k
            val inset = stroke / 2
            val arcSize = Size(size.width - stroke, size.height - stroke)
            drawArc(c.copy(alpha = 0.16f), -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
            drawArc(c, -90f, 360f * fraction, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        if (verdict == null) {
            Text("정상", color = c, fontSize = (42 * k).sp, fontWeight = FontWeight.Bold)
        } else {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("${verdict.grade.level}", color = c, fontSize = (76 * k).sp, fontWeight = FontWeight.Bold, lineHeight = (76 * k).sp)
                Text("긴급도 ${verdict.score}/$MAX_SCORE", color = Palette.textDim, fontSize = 16.sp)
            }
        }
    }
}

private const val MAX_SCORE = 6

@Composable
private fun ScorePill(label: String, points: String, tint: Color) {
    Row(
        Modifier.background(tint.copy(alpha = 0.12f), RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Palette.text, fontSize = 18.sp)
        Spacer(Modifier.width(10.dp))
        Text(points, color = tint, fontSize = 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun GradeBadge(grade: Grade, big: Boolean = false) {
    val c = Palette.forGrade(grade)
    val label = if (grade == Grade.OK) "정상" else "${grade.level}등급 · ${grade.title}"
    Box(
        Modifier.background(c.copy(alpha = 0.18f), RoundedCornerShape(50)).border(2.dp, c, RoundedCornerShape(50))
            .padding(horizontal = if (big) 20.dp else 14.dp, vertical = if (big) 10.dp else 6.dp)
    ) {
        Text(label, color = c, fontSize = if (big) 24.sp else 18.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Chip(text: String, highlight: Boolean = false, dot: Color? = null) {
    Row(
        Modifier.background(if (highlight) Palette.accent.copy(alpha = 0.18f) else Palette.surface, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (dot != null) {
            Box(Modifier.size(8.dp).background(dot, CircleShape))
            Spacer(Modifier.width(8.dp))
        }
        Text(text, color = if (highlight) Palette.info else Palette.textDim, fontSize = 18.sp)
    }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 10.dp)) {
        listOf(Palette.normal to "정상", Palette.caution to "주의", Palette.danger to "위험").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(10.dp).background(c, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(t, color = Palette.textDim, fontSize = 16.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(24.dp)).padding(24.dp)
    ) { content() }
}

/** Parked only: cause, risk and what to do, from the AI (or the built-in template). */
@Composable
private fun ExplainCard(exp: Explanation) {
    Card {
        ExplainLine("원인", exp.cause)
        ExplainLine("위험", exp.risk)
        ExplainLine("할 일", exp.action)
        Text(
            (if (exp.byAi) "AI 설명" else "기본 안내 문구") + " · 최종 판단은 정비 전문가에게 확인하세요",
            color = Palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
        )
    }
}

@Composable
private fun ExplainLine(label: String, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Palette.textDim, fontSize = 20.sp, modifier = Modifier.width(72.dp))
        Text(text, color = Palette.text, fontSize = 22.sp, lineHeight = 30.sp)
    }
}

/**
 * The nearest shop is the hero with one big "안내 시작" button; the rest fold behind a link
 * (and are hidden while driving, together with the report link).
 */
@Composable
private fun ShopsCard(
    shops: List<RepairShop>,
    samples: Boolean,
    driving: Boolean,
    naviStatus: String?,
    onRoute: (RepairShop) -> Unit,
    onReport: (() -> Unit)?,
) {
    var showOthers by remember { mutableStateOf(false) }
    val first = shops.firstOrNull()
    Card {
        Text(if (samples) "가까운 정비소 (예시 데이터)" else "가까운 정비소 · OpenStreetMap", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(14.dp))
        if (first != null) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    Modifier.size(52.dp).background(Palette.accent.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Filled.Build, contentDescription = null, tint = Palette.info, modifier = Modifier.size(26.dp)) }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(first.name, color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                    Text("가장 가까운 정비소", color = Palette.textDim, fontSize = 16.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("%.1f km".format(first.distanceKm), color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
                    Text("거리", color = Palette.textDim, fontSize = 16.sp)
                }
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = { onRoute(first) },
                colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth().height(76.dp),
            ) {
                Icon(Icons.Filled.Place, contentDescription = null, modifier = Modifier.size(26.dp))
                Spacer(Modifier.width(8.dp))
                Text("안내 시작", fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
            }
        }
        val others = shops.drop(1).take(2)
        if (!driving && (onReport != null || others.isNotEmpty())) {
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                onReport?.let { LinkText("정비소 사전 리포트 (QR)", it) }
                Spacer(Modifier.weight(1f))
                if (others.isNotEmpty()) {
                    LinkText(if (showOthers) "접기" else "다른 정비소 ${others.size}곳 ›") { showOthers = !showOthers }
                }
            }
            if (showOthers) {
                others.forEach { shop ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth().padding(top = 10.dp)
                            .background(Palette.surfaceHigh, RoundedCornerShape(16.dp)).padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(shop.name, color = Palette.text, fontSize = 20.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("%.1f km".format(shop.distanceKm), color = Palette.textDim, fontSize = 16.sp)
                        }
                        LinkText("안내", onClick = { onRoute(shop) })
                    }
                }
            }
        }
        naviStatus?.let { Text(it, color = Palette.textDim, fontSize = 16.sp, modifier = Modifier.padding(top = 10.dp)) }
    }
}

@Composable
private fun LinkText(text: String, onClick: () -> Unit) {
    Text(
        text, color = Palette.info, fontSize = 18.sp, fontWeight = FontWeight.Medium,
        modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 10.dp),
    )
}

@Composable
private fun OtherFaults(verdicts: List<Verdict>, selected: Verdict?, enabled: Boolean, onSelect: (com.team.cardoctor.data.Part) -> Unit) {
    Card {
        Text("감지된 항목 ${verdicts.size}개", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        verdicts.forEach { v ->
            val isSel = v.fault.part == selected?.fault?.part
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
                    .background(if (isSel) Palette.surfaceHigh else Color.Transparent, RoundedCornerShape(12.dp))
                    .clickable(enabled = enabled) { onSelect(v.fault.part) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
            ) {
                Box(Modifier.size(14.dp).background(Palette.forGrade(v.grade), CircleShape))
                Spacer(Modifier.width(12.dp))
                Text(v.fault.part.label, color = Palette.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text("${v.grade.level}등급", color = Palette.forGrade(v.grade), fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** All clear: the six areas the full check covers, each marked normal. */
@Composable
private fun CheckGridCard(modifier: Modifier = Modifier, stretch: Boolean = false) {
    // With a weight the tiles stretch to share the card's height; without one they keep their natural size.
    Column(modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(24.dp)).padding(24.dp)) {
        Text("점검 항목", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(14.dp))
        DiagnosisProgress.STEPS.chunked(3).forEachIndexed { i, row ->
            if (i > 0) Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.fillMaxWidth().then(if (stretch) Modifier.weight(1f) else Modifier).height(IntrinsicSize.Min)) {
                row.forEach { name ->
                    Row(
                        Modifier.weight(1f).fillMaxHeight().background(Palette.ok.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                            .padding(horizontal = 16.dp, vertical = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(28.dp).background(Palette.ok.copy(alpha = 0.18f), CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = Palette.ok, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(name, color = Palette.text, fontSize = 19.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            Text("정상", color = Palette.ok, fontSize = 16.sp)
                        }
                    }
                }
            }
        }
    }
}

/** One status line under "모든 항목 정상": last full check and how many vehicle signals answered. */
private fun allClearSummary(state: UiState): String? {
    val signals = state.vehicle.signals
    val failed = signals.count { it.value == "미지원/오류" }
    val parts = buildList {
        state.diagnosis.finishedAt?.let {
            add("마지막 전체 점검 " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(it)))
        }
        if (signals.isNotEmpty()) add("응답한 차량 신호 ${signals.size - failed}개" + if (failed > 0) " · 미지원 ${failed}개" else "")
        if (!state.vehicle.connected) add("차량 SDK 미연결 — 데모 패널로 시연할 수 있습니다")
    }
    return parts.takeIf { it.isNotEmpty() }?.joinToString("  ·  ")
}

@Composable
private fun DiagnosisCard(state: UiState) {
    val steps = DiagnosisProgress.STEPS
    Card {
        Text("전체 점검 중", color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        LinearProgressIndicator(
            progress = { (state.diagnosis.step + 1f) / steps.size },
            modifier = Modifier.fillMaxWidth().height(10.dp),
            color = Palette.accent,
        )
        Spacer(Modifier.height(16.dp))
        steps.forEachIndexed { i, s ->
            val done = i < state.diagnosis.step
            val now = i == state.diagnosis.step
            Text(
                (if (done) "완료  " else if (now) "점검  " else "대기  ") + s,
                color = if (now) Palette.text else Palette.textDim, fontSize = 20.sp,
                modifier = Modifier.padding(vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun Gap(content: @Composable () -> Unit) {
    Spacer(Modifier.height(16.dp))
    content()
}

@Composable
private fun ActionButton(text: String, color: Color, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        colors = ButtonDefaults.buttonColors(containerColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().height(76.dp),
    ) { Text(text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
}

/** Grade 4: the first action is getting help, not navigation. */
@Composable
private fun EmergencyCard(onCall: () -> Unit) {
    Card {
        Text("비상등을 켜고 갓길이나 안전지대로 이동한 뒤 차량 밖 안전한 곳에서 기다리세요.", color = Palette.text, fontSize = 22.sp, lineHeight = 30.sp)
        Spacer(Modifier.height(16.dp))
        ActionButton("긴급출동 연결", Palette.danger, onCall)
    }
}

/** Feature C: warnings before anything is broken. */
@Composable
private fun PredictionsCard(state: UiState) {
    Card {
        Text("예측 정비", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        state.predictions.forEach { p ->
            val days = p.daysLeft.toInt().coerceAtLeast(1)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 6.dp)) {
                Box(Modifier.size(14.dp).background(Palette.caution, CircleShape))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("${p.part.label.removeSuffix(" 저하")}: 약 ${days}일 뒤 경고 기준 도달", color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text(
                        "현재 %.1f · 하루 %.1f씩 감소 · 기준 %.1f".format(p.current, -p.perDay, p.threshold) +
                            if (days <= 7) " · 이번 주 안에 공기압 점검을 권장합니다" else " · 다음 주유·충전 때 점검하세요",
                        color = Palette.textDim, fontSize = 18.sp,
                    )
                }
            }
        }
    }
}

/** Feature H: recent detections from real vehicle signals. */
@Composable
private fun HistoryCard(state: UiState) {
    val fmt = remember { java.time.format.DateTimeFormatter.ofPattern("M/d HH:mm").withZone(java.time.ZoneId.systemDefault()) }
    Card {
        Text("최근 감지 이력", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        state.history.take(5).forEach { h ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp)) {
                Text(fmt.format(java.time.Instant.ofEpochMilli(h.timeMillis)), color = Palette.textDim, fontSize = 18.sp, modifier = Modifier.width(120.dp))
                Text(h.part.label, color = Palette.text, fontSize = 20.sp, modifier = Modifier.weight(1f))
                Text("${h.grade.level}등급", color = Palette.forGrade(h.grade), fontSize = 20.sp)
            }
        }
    }
}

/** Feature B: report text + QR the shop can scan before the visit. */
@Composable
private fun ReportSheet(text: String, onClose: () -> Unit) {
    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.7f)).clickable(onClick = onClose),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier.fillMaxWidth(0.88f).fillMaxHeight(0.86f)
                .background(Palette.surface, RoundedCornerShape(24.dp))
                .clickable(enabled = false) {}
                .padding(28.dp)
        ) {
            Column(Modifier.weight(0.38f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("정비소 사전 리포트", color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(16.dp))
                QrCode(text, Modifier.fillMaxWidth().weight(1f))
                Spacer(Modifier.height(12.dp))
                Text("정비소에서 QR을 찍으면 고장 부위·등급·신호 기록을 미리 받습니다.", color = Palette.textDim, fontSize = 16.sp)
            }
            Spacer(Modifier.width(28.dp))
            Column(Modifier.weight(0.62f)) {
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                    Text(text, color = Palette.text, fontSize = 18.sp, lineHeight = 26.sp)
                }
                Spacer(Modifier.height(12.dp))
                ActionButton("닫기", Palette.surfaceHigh, onClose)
            }
        }
    }
}

/** Feature A2: what the driver asked and what the app answered (also spoken by TTS). */
@Composable
private fun VoiceBubble(state: UiState) {
    val v = state.voice
    Row(
        Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(20.dp)).padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(14.dp).background(if (v.listening) Palette.danger else Palette.accent, CircleShape))
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                "“${v.transcript ?: "말씀하세요…"}”",
                color = Palette.textDim, fontSize = 20.sp,
            )
            v.reply?.let { Text(it, color = Palette.text, fontSize = 22.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold) }
        }
    }
}

/** "같은 고장, 다른 상황": the core idea at a glance, shown only while parked. */
@Composable
private fun WhatIfCard(v: Verdict, ctx: com.team.cardoctor.data.DrivingContext) {
    val rows = remember(v.fault.part) { com.team.cardoctor.engine.RuleEngine.whatIf(v.fault) }
    val current = com.team.cardoctor.engine.RuleEngine.whatIfLabel(ctx)
    Card {
        Text("같은 고장, 상황이 바뀌면", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
            rows.forEach { (label, grade) ->
                val c = Palette.forGrade(grade)
                val now = label == current
                Column(
                    Modifier.weight(1f).background(c.copy(alpha = 0.14f), RoundedCornerShape(14.dp))
                        .then(if (now) Modifier.border(2.dp, Color.White, RoundedCornerShape(14.dp)) else Modifier)
                        .padding(vertical = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(if (now) "$label · 지금" else label, color = if (now) Palette.text else Palette.textDim, fontSize = 16.sp)
                    Text("${grade.level}등급", color = c, fontSize = 24.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
