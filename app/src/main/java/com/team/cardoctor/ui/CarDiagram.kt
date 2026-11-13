package com.team.cardoctor.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.team.cardoctor.data.Grade
import com.team.cardoctor.data.Part
import com.team.cardoctor.data.Verdict
import com.team.cardoctor.data.Zone

/** True in screenshot renders: infinite animations (grade-4 blink) are frozen so capture can settle. */
val LocalStaticRender = staticCompositionLocalOf { false }

/**
 * Top-down car (front at the top). Each zone takes the colour of its worst verdict:
 * gray = normal, yellow = grade 1-2, red = grade 3, blinking red = grade 4.
 */
@Composable
fun CarDiagram(
    verdicts: List<Verdict>,
    selected: Part?,
    onSelect: (Part) -> Unit,
    modifier: Modifier = Modifier,
) {
    val blink = if (LocalStaticRender.current) 1f else {
        val b by rememberInfiniteTransition(label = "blink").animateFloat(
            initialValue = 1f, targetValue = 0.25f,
            animationSpec = infiniteRepeatable(tween(450, easing = LinearEasing), RepeatMode.Reverse),
            label = "blinkAlpha",
        )
        b
    }
    val zoneGrades: Map<Zone, Verdict> = Zone.entries.mapNotNull { z ->
        verdicts.filter { z in it.fault.part.zones }.maxByOrNull { it.grade.level }?.let { z to it }
    }.toMap()
    val selectedZones = selected?.zones.orEmpty().toSet()

    val description = if (verdicts.isEmpty()) "차량 상태 그림: 모든 부위 정상" else
        "차량 상태 그림: " + verdicts.joinToString(", ") { "${it.fault.part.label} ${it.grade.level}등급" }
    Canvas(
        modifier.semantics { contentDescription = description }.pointerInput(verdicts) {
            detectTapGestures { pos ->
                val layout = CarLayout.of(Size(size.width.toFloat(), size.height.toFloat()))
                val zone = layout.zoneAt(pos) ?: return@detectTapGestures
                zoneGrades[zone]?.let { onSelect(it.fault.part) }
            }
        }
    ) {
        val layout = CarLayout.of(size)
        // Body outline
        drawRoundRect(Palette.surfaceHigh, layout.body.topLeft, layout.body.size, CornerRadius(layout.body.width * 0.28f))
        drawRoundRect(Palette.line, layout.body.topLeft, layout.body.size, CornerRadius(layout.body.width * 0.28f), style = Stroke(3f))
        drawDecorations(layout)

        for (zone in Zone.entries) {
            val r = layout.rect(zone)
            val v = zoneGrades[zone]
            val base = v?.let { Palette.forGrade(it.grade) } ?: Palette.normal
            val alpha = when {
                v == null -> if (zone in GLASS_OR_PANEL) 0.35f else 0.8f
                v.grade == Grade.STOP_NOW -> blink
                else -> 1f
            }
            drawZone(zone, r, base.copy(alpha = alpha))
            if (zone in selectedZones && v != null) {
                drawRoundRect(Color.White, r.topLeft - Offset(6f, 6f), Size(r.width + 12f, r.height + 12f), CornerRadius(14f), style = Stroke(4f))
            }
        }
    }
}

private val GLASS_OR_PANEL = setOf(Zone.HOOD, Zone.CABIN, Zone.TRUNK, Zone.WINDSHIELD)

/** Non-interactive details so the shape reads as a car at a glance: travel arrow, mirrors, rear glass, tail lamps. */
private fun DrawScope.drawDecorations(layout: CarLayout) {
    val b = layout.body
    val decor = Palette.line
    // Direction of travel
    val cx = b.center.x
    val tip = b.top - b.height * 0.035f
    drawPath(
        androidx.compose.ui.graphics.Path().apply {
            moveTo(cx, tip)
            lineTo(cx - b.width * 0.07f, b.top - 4f)
            lineTo(cx + b.width * 0.07f, b.top - 4f)
            close()
        },
        Palette.textDim,
    )
    // Side mirrors next to the windshield
    val my = b.top + b.height * 0.30f
    drawRoundRect(decor, Offset(b.left - b.width * 0.09f, my), Size(b.width * 0.09f, b.height * 0.035f), CornerRadius(8f))
    drawRoundRect(decor, Offset(b.right, my), Size(b.width * 0.09f, b.height * 0.035f), CornerRadius(8f))
    // Rear window
    drawRoundRect(decor, Offset(b.left + b.width * 0.18f, b.top + b.height * 0.735f), Size(b.width * 0.64f, b.height * 0.05f), CornerRadius(14f))
    // Tail lamps
    drawRoundRect(Palette.danger.copy(alpha = 0.35f), Offset(b.left + b.width * 0.08f, b.top + b.height * 0.965f), Size(b.width * 0.22f, b.height * 0.02f), CornerRadius(8f))
    drawRoundRect(Palette.danger.copy(alpha = 0.35f), Offset(b.left + b.width * 0.70f, b.top + b.height * 0.965f), Size(b.width * 0.22f, b.height * 0.02f), CornerRadius(8f))
}

private fun DrawScope.drawZone(zone: Zone, r: Rect, color: Color) {
    val corner = when (zone) {
        Zone.WHEEL_FL, Zone.WHEEL_FR, Zone.WHEEL_RL, Zone.WHEEL_RR -> CornerRadius(r.width * 0.35f)
        Zone.HEADLAMP_LEFT, Zone.HEADLAMP_RIGHT -> CornerRadius(r.height * 0.5f)
        else -> CornerRadius(18f)
    }
    drawRoundRect(color, r.topLeft, r.size, corner)
}

/** Geometry shared by drawing and tap hit-testing. */
private class CarLayout(val body: Rect) {
    fun rect(zone: Zone): Rect {
        val w = body.width
        val h = body.height
        fun rel(x0: Float, y0: Float, x1: Float, y1: Float) =
            Rect(body.left + w * x0, body.top + h * y0, body.left + w * x1, body.top + h * y1)
        return when (zone) {
            Zone.HEADLAMP_LEFT -> rel(0.08f, 0.015f, 0.34f, 0.055f)
            Zone.HEADLAMP_RIGHT -> rel(0.66f, 0.015f, 0.92f, 0.055f)
            Zone.HOOD -> rel(0.12f, 0.08f, 0.88f, 0.27f)
            Zone.WINDSHIELD -> rel(0.14f, 0.29f, 0.86f, 0.36f)
            Zone.CABIN -> rel(0.14f, 0.38f, 0.86f, 0.72f)
            Zone.TRUNK -> rel(0.16f, 0.80f, 0.84f, 0.96f)
            Zone.WHEEL_FL -> rel(-0.10f, 0.13f, 0.05f, 0.27f)
            Zone.WHEEL_FR -> rel(0.95f, 0.13f, 1.10f, 0.27f)
            Zone.WHEEL_RL -> rel(-0.10f, 0.70f, 0.05f, 0.84f)
            Zone.WHEEL_RR -> rel(0.95f, 0.70f, 1.10f, 0.84f)
        }
    }

    /** Small parts first so a headlamp wins over the hood behind it; wheels get a generous margin. */
    fun zoneAt(p: Offset): Zone? = HIT_ORDER.firstOrNull { rect(it).inflate(18f).contains(p) }

    companion object {
        val HIT_ORDER = listOf(
            Zone.HEADLAMP_LEFT, Zone.HEADLAMP_RIGHT, Zone.WHEEL_FL, Zone.WHEEL_FR, Zone.WHEEL_RL, Zone.WHEEL_RR,
            Zone.WINDSHIELD, Zone.HOOD, Zone.CABIN, Zone.TRUNK,
        )

        fun of(size: Size): CarLayout {
            // Keep the car's proportions whichever side is the limit, so a narrow column never stretches it.
            val bw = minOf(size.height * 0.92f / 2.1f, size.width * 0.66f)
            val bh = bw * 2.1f
            val left = (size.width - bw) / 2f
            val top = (size.height - bh) / 2f
            return CarLayout(Rect(left, top, left + bw, top + bh))
        }
    }
}
