package com.team.cardoctor.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.team.cardoctor.data.Grade

object Palette {
    val bg = Color(0xFF0B0D10)
    val surface = Color(0xFF16191E)
    val surfaceHigh = Color(0xFF20242B)
    val line = Color(0xFF2E333B)
    val text = Color(0xFFF2F4F7)
    val textDim = Color(0xFF9AA3AF)
    val normal = Color(0xFF59606B)
    val ok = Color(0xFF3FB27F)
    val caution = Color(0xFFF2B33D)
    val danger = Color(0xFFEF4444)
    val accent = Color(0xFF4C8DFF)

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
