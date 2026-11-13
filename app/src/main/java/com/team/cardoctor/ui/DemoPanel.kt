package com.team.cardoctor.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Weather

/**
 * Hidden presenter panel (long-press the title). Replays scenarios A/B/C and injects faults the
 * emulator cannot produce. Real vehicle signals keep flowing underneath.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoPanel(state: UiState, vm: UiActions, onClose: () -> Unit) {
    val d = state.demo
    Column(
        Modifier.width(520.dp).fillMaxHeight().background(Palette.surface)
            .verticalScroll(rememberScrollState()).padding(24.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("데모 패널", color = Palette.text, fontSize = 26.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
            Pill("닫기", false) { onClose() }
        }
        Section("시나리오")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("A 낮·전조등", d.scenario == "A") { vm.scenario("A") }
            Pill("B 밤·전조등", d.scenario == "B") { vm.scenario("B") }
            Pill("C 빗길 고속·타이어", d.scenario == "C") { vm.scenario("C") }
            Pill("D 서서히 빠지는 공기압(예측)", d.slowLeak) { vm.toggleSlowLeak() }
            Pill("정차하기", d.driving == false) { vm.park() }
            Pill("초기화", false) { vm.scenario("reset") }
        }

        Section("시간대")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("실제", d.night == null) { vm.setNight(null) }
            Pill("낮 14시", d.night == false) { vm.setNight(false) }
            Pill("밤 21시", d.night == true) { vm.setNight(true) }
        }

        Section("날씨")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("실제", d.weather == null) { vm.setWeather(null) }
            Weather.entries.forEach { w -> Pill(w.label, d.weather == w) { vm.setWeather(w) } }
        }

        Section("속도 · 주행")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("실제", d.speedKmh == null) { vm.setSpeed(null) }
            listOf(0, 50, 100).forEach { s -> Pill("$s km/h", d.speedKmh == s) { vm.setSpeed(s) } }
            Pill("주행 중", d.driving == true) { vm.setDriving(true) }
            Pill("정차 중", d.driving == false) { vm.setDriving(false) }
        }

        Section("목적지까지 남은 거리")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill("실제/없음", d.remainingKm == null) { vm.setRemainingKm(null) }
            Pill("10 km", d.remainingKm == 10.0) { vm.setRemainingKm(10.0) }
            Pill("120 km", d.remainingKm == 120.0) { vm.setRemainingKm(120.0) }
        }

        Section("음성 질문 대신 입력 (마이크 없는 에뮬레이터용)")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("지금 정비소 가야 돼?", "왜 위험해?", "정비소 안내해줘", "리포트 보여줘").forEach { q ->
                Pill(q, state.voice.transcript == q) { vm.voiceText(q) }
            }
        }

        Section("고장 주입")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Part.entries.forEach { p -> Pill(p.label, p in d.injected) { vm.toggleFault(p) } }
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "차량 SDK: ${if (state.vehicle.connected) "연결됨" else "미연결"} · 기어 ${state.vehicle.gear ?: "-"} · AI 설명 ${if (state.aiEnabled) "켜짐" else "꺼짐(키 없음)"}",
            color = Palette.textDim, fontSize = 16.sp,
        )
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(20.dp))
    Text(title, color = Palette.textDim, fontSize = 18.sp)
    Spacer(Modifier.height(8.dp))
}

@Composable
private fun Pill(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        Modifier.background(if (selected) Palette.accent else Palette.surfaceHigh, RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 14.dp)
    ) {
        Text(text, color = if (selected) Palette.bg else Palette.text, fontSize = 18.sp, fontWeight = FontWeight.Medium)
    }
}
