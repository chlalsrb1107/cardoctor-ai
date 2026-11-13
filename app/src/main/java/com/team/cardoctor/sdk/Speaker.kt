package com.team.cardoctor.sdk

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * Spoken warnings (feature A: 단계적 알림). Uses the Pleos TextToSpeech SDK and falls back to the
 * Android engine when the Pleos speech service is not available.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null
    private var fallbackReady = false

    fun start() {
        try {
            val tts = PleosTts(appContext, Mode.HYBRID)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    pleosReady = true
                    runCatching { tts.setLocale(Locale.KOREAN) }
                }
                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TTS init failed: $type")
                    startFallback()
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Pleos TTS unavailable: ${e.message}")
            startFallback()
        }
    }

    fun speak(text: String) {
        val p = pleos
        if (pleosReady && p != null) {
            try {
                p.speak(text, false)
                return
            } catch (e: Exception) {
                Log.w(TAG, "Pleos speak failed: ${e.message}")
            }
        }
        if (fallback == null) startFallback()
        if (fallbackReady) fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "cardoctor")
    }

    fun stop() {
        runCatching { pleos?.stop(); pleos?.release() }
        runCatching { fallback?.shutdown() }
        pleos = null
        fallback = null
        pleosReady = false
        fallbackReady = false
    }

    private fun startFallback() {
        if (fallback != null) return
        fallback = AndroidTts(appContext) { status ->
            fallbackReady = status == AndroidTts.SUCCESS
            if (fallbackReady) fallback?.language = Locale.KOREAN
        }
    }

    private companion object { const val TAG = "Speaker" }
}
