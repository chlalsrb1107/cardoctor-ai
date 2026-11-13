package com.team.cardoctor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.team.cardoctor.data.Grade

object Palette {
    val bg = Color(0xFF0B1018)
    val surface = Color(0xFF121A26)
    val surfaceHigh = Color(0xFF1A2433)
    val line = Color(0xFF2A3446)
    val text = Color(0xFFEEF2F7)
    val textDim = Color(0xFF97A3B6)
    val normal = Color(0xFF3A4558)
    val ok = Color(0xFF3FB27F)
    val caution = Color(0xFFF2B33D)
    val danger = Color(0xFFFF7A6B)
    val accent = Color(0xFF3B7BFF)

    /** Points that come from the situation (night, rain, speed) rather than the part itself. */
    val info = Color(0xFF8FB3FF)

    fun forGrade(g: Grade): Color = when (g) {
        Grade.OK -> ok
        Grade.RELAXED, Grade.AFTER_ARRIVAL -> caution
        Grade.SERVICE_NOW, Grade.STOP_NOW -> danger
    }
}

@Composable
fun CarDoctorTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = Palette.accent,
            background = Palette.bg,
            surface = Palette.surface,
            onSurface = Palette.text,
            onBackground = Palette.text,
        ),
        content = content,
    )
}
