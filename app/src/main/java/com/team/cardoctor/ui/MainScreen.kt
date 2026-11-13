package com.team.cardoctor.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.cardoctor.ai.Explanation
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.RepairShop
import com.team.cardoctor.data.Verdict

@Composable
fun MainScreen(state: UiState, vm: UiActions, initialDemoOpen: Boolean = false) {
    var demoOpen by remember { mutableStateOf(initialDemoOpen) }
    val driving = state.context.isDriving

    Box(Modifier.fillMaxSize().background(Palette.bg)) {
        Column(Modifier.fillMaxSize().padding(24.dp)) {
            TopBar(state, onTitleLongPress = { demoOpen = !demoOpen }, onDiagnose = vm::runSelfDiagnosis, onVoice = vm::startVoice)
            Spacer(Modifier.height(16.dp))
            if (state.voice.listening || state.voice.transcript != null) {
                VoiceBubble(state)
                Spacer(Modifier.height(12.dp))
            }
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.weight(0.40f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally) {
                    CarDiagram(
                        verdicts = state.verdicts,
                        selected = state.selected?.fault?.part,
                        onSelect = vm::select,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                    )
                    state.selected?.let {
                        Text(it.fault.part.label, color = Palette.forGrade(it.grade), fontSize = 20.sp, fontWeight = FontWeight.SemiBold)
                    }
                    Legend()
                }
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(0.60f).fillMaxHeight().verticalScroll(rememberScrollState())) {
                    when {
                        state.diagnosis.running -> DiagnosisCard(state)
                        state.verdicts.isEmpty() -> {
                            AllClearCard(state)
                            if (state.predictions.isNotEmpty()) Gap { PredictionsCard(state) }
                            if (!driving && state.history.isNotEmpty()) Gap { HistoryCard(state) }
                        }
                        else -> {
                            val top = state.selected!!
                            if (driving) DrivingNotice()
                            VerdictCard(top, state.explanation, driving, state.recurring[top.fault.part])
                            if (!driving) Gap { WhatIfCard(top, state.context) }
                            if (top.grade == Grade.STOP_NOW) Gap { EmergencyCard(vm::callRoadside) }
                            if (top.grade.level >= Grade.SERVICE_NOW.level || !driving) {
                                Gap { ShopsCard(state.shops, state.shopsAreSamples, driving, state.naviStatus, vm::routeTo) }
                            }
                            if (!driving) Gap { ActionButton("정비소 사전 리포트 (QR)", Palette.surfaceHigh, vm::openReport) }
                            if (state.verdicts.size > 1) {
                                Gap { OtherFaults(state.verdicts, top, enabled = !driving, onSelect = vm::select) }
                            }
                            if (!driving && state.predictions.isNotEmpty()) Gap { PredictionsCard(state) }
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
        Text(
            "카닥터 AI",
            color = Palette.text, fontSize = 30.sp, fontWeight = FontWeight.Bold,
            modifier = Modifier.combinedClickable(onClick = {}, onLongClick = onTitleLongPress),
        )
        Spacer(Modifier.width(20.dp))
        GradeBadge(state.overall, big = true)
        Spacer(Modifier.width(20.dp))
        FlowRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            val ctx = state.context
            Chip(if (ctx.isNight) "야간" else "주간")
            Chip(ctx.weather.label + if (state.demo.weather == null && !state.environment.weatherFetched) " (기본값)" else "")
            Chip("${ctx.speedKmh} km/h")
            Chip(if (ctx.isDriving) "주행 중" else "정차 중", highlight = ctx.isDriving)
            ctx.remainingKm?.let { Chip("목적지 ${it.toInt()} km") }
            if (state.demo.scenario != null || state.demo.injected.isNotEmpty()) Chip("데모 ${state.demo.scenario ?: "수동"}", highlight = true)
            if (!state.vehicle.connected) Chip("차량 SDK 미연결")
        }
        // Voice works while driving: it is the safe way to ask "지금 가야 돼?".
        Button(
            onClick = onVoice,
            enabled = !state.voice.listening,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.surfaceHigh, contentColor = Color.White),
            modifier = Modifier.height(64.dp),
        ) { Text(if (state.voice.listening) "듣는 중…" else "음성 질문", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
        Spacer(Modifier.width(12.dp))
        Button(
            onClick = onDiagnose,
            enabled = !state.context.isDriving && !state.diagnosis.running,
            colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
            modifier = Modifier.height(64.dp),
        ) { Text("전체 점검", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
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
private fun Chip(text: String, highlight: Boolean = false) {
    Box(
        Modifier.background(if (highlight) Palette.accent.copy(alpha = 0.2f) else Palette.surfaceHigh, RoundedCornerShape(50))
            .padding(horizontal = 14.dp, vertical = 8.dp)
    ) { Text(text, color = if (highlight) Palette.accent else Palette.textDim, fontSize = 18.sp) }
}

@Composable
private fun Legend() {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.padding(top = 8.dp)) {
        listOf(Palette.normal to "정상", Palette.caution to "주의", Palette.danger to "위험").forEach { (c, t) ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(14.dp).background(c, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(t, color = Palette.textDim, fontSize = 18.sp)
            }
        }
    }
}

@Composable
private fun Card(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxWidth().background(Palette.surface, RoundedCornerShape(20.dp)).padding(24.dp)
    ) { content() }
}

@Composable
private fun DrivingNotice() {
    Text(
        "주행 중에는 요약만 표시합니다. 자세한 설명은 정차 후 확인하세요.",
        color = Palette.textDim, fontSize = 18.sp, modifier = Modifier.padding(bottom = 12.dp),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun VerdictCard(v: Verdict, exp: Explanation?, driving: Boolean, recurringDays: Int?) {
    val c = Palette.forGrade(v.grade)
    Card {
        Row(verticalAlignment = Alignment.CenterVertically) {
            GradeBadge(v.grade)
            Spacer(Modifier.width(14.dp))
            Text(v.fault.part.label, color = Palette.text, fontSize = 28.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(16.dp))
        Text(v.grade.advice, color = c, fontSize = 30.sp, fontWeight = FontWeight.Bold, lineHeight = 38.sp)
        Spacer(Modifier.height(16.dp))
        Text("판정 근거 · 긴급도 ${v.score}점", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            v.reasons.forEach { r -> Chip("${r.label} +${r.points}", highlight = r.points >= 2 && !r.label.startsWith("기본")) }
            if (v.flooredBySafety) Chip("안전 핵심 부품: 최소 3등급", highlight = true)
            recurringDays?.let { Chip("최근 30일 중 ${it}일 반복 — 근본 원인 점검 권장", highlight = true) }
        }
        v.fault.detail?.let {
            Spacer(Modifier.height(8.dp))
            Text("차량 신호: $it", color = Palette.textDim, fontSize = 16.sp)
        }
        if (exp != null) {
            Spacer(Modifier.height(20.dp))
            if (driving) {
                // The advice line above already says what to do; while moving add only the "why".
                Text(exp.risk, color = Palette.text, fontSize = 24.sp, lineHeight = 32.sp)
            } else {
                ExplainLine("원인", exp.cause)
                ExplainLine("위험", exp.risk)
                ExplainLine("할 일", exp.action)
            }
            Text(
                if (exp.byAi) "AI 설명 · 최종 판단은 정비 전문가에게 확인하세요" else "기본 안내 문구 · 최종 판단은 정비 전문가에게 확인하세요",
                color = Palette.textDim, fontSize = 14.sp, modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun ExplainLine(label: String, text: String) {
    Row(Modifier.padding(vertical = 6.dp)) {
        Text(label, color = Palette.textDim, fontSize = 20.sp, modifier = Modifier.width(72.dp))
        Text(text, color = Palette.text, fontSize = 22.sp, lineHeight = 30.sp)
    }
}

@Composable
private fun ShopsCard(shops: List<RepairShop>, samples: Boolean, driving: Boolean, naviStatus: String?, onRoute: (RepairShop) -> Unit) {
    Card {
        Text(if (samples) "가까운 정비소 (예시 데이터)" else "가까운 정비소 · OpenStreetMap", color = Palette.textDim, fontSize = 18.sp)
        Spacer(Modifier.height(12.dp))
        val list = if (driving) shops.take(1) else shops.take(3)
        list.forEach { shop ->
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                Column(Modifier.weight(1f)) {
                    Text(shop.name, color = Palette.text, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                    Text("%.1f km".format(shop.distanceKm), color = Palette.textDim, fontSize = 18.sp)
                }
                Button(
                    onClick = { onRoute(shop) },
                    colors = ButtonDefaults.buttonColors(containerColor = Palette.accent, contentColor = Color.White),
                    modifier = Modifier.height(76.dp),
                ) { Text("내비로 안내", fontSize = 22.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        naviStatus?.let { Text(it, color = Palette.textDim, fontSize = 16.sp, modifier = Modifier.padding(top = 8.dp)) }
    }
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

@Composable
private fun AllClearCard(state: UiState) {
    Card {
        Text("모든 항목 정상", color = Palette.ok, fontSize = 32.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text("경고등·타이어·오일류·워셔액 신호에서 이상이 없습니다.", color = Palette.text, fontSize = 20.sp)
        val signals = state.vehicle.signals
        val failed = signals.count { it.value == "미지원/오류" }
        val summary = buildList {
            state.diagnosis.finishedAt?.let {
                add("마지막 전체 점검 " + java.time.format.DateTimeFormatter.ofPattern("HH:mm").withZone(java.time.ZoneId.systemDefault()).format(java.time.Instant.ofEpochMilli(it)))
            }
            if (signals.isNotEmpty()) add("응답한 차량 신호 ${signals.size - failed}개" + if (failed > 0) " · 미지원 ${failed}개" else "")
            if (!state.vehicle.connected) add("차량 SDK 미연결 — 데모 패널로 시연할 수 있습니다")
        }
        if (summary.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(summary.joinToString("  ·  "), color = Palette.textDim, fontSize = 18.sp)
        }
    }
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
