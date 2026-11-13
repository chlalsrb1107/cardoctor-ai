package com.team.cardoctor.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel

/** Encodes [text] (UTF-8, Korean OK) as a QR code; null when the text is too long for one code. */
fun encodeQr(text: String): BitMatrix? = runCatching {
    QRCodeWriter().encode(
        text, BarcodeFormat.QR_CODE, 0, 0,
        mapOf(EncodeHintType.CHARACTER_SET to "UTF-8", EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.L, EncodeHintType.MARGIN to 2),
    )
}.getOrNull()

@Composable
fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { encodeQr(text) } ?: return
    Canvas(modifier) {
        drawRect(Color.White)
        val cell = minOf(size.width / matrix.width, size.height / matrix.height)
        val ox = (size.width - cell * matrix.width) / 2f
        val oy = (size.height - cell * matrix.height) / 2f
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(ox + x * cell, oy + y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}
