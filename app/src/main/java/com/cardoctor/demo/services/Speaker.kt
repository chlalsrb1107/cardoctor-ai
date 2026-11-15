package com.cardoctor.demo.services

import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.EventListener
import ai.pleos.playground.tts.listener.OnInitializeListener
import android.content.Context
import android.speech.tts.TextToSpeech as AndroidTts
import android.util.Log
import java.util.Locale
import ai.pleos.playground.tts.TextToSpeech as PleosTts

/**
 * F3 음성 안내. Pleos TextToSpeech first, the Android engine as a fallback.
 * Callers decide what is worth saying; this class only speaks.
 */
class Speaker(context: Context) {

    private val appContext = context.applicationContext
    private var pleos: PleosTts? = null
    private var pleosReady = false
    private var fallback: AndroidTts? = null
    private var fallbackReady = false

    /** Which engine spoke last, for the footer: "Pleos TTS", "Android TTS" or null. */
    var engine: String? = null
        private set

    fun start() {
        startFallback() // ready early, in case Pleos TTS fails silently
        try {
            // On the Pleos Connect v2.1 emulator only ON_DEVICE + ko_KR produces audio:
            // HYBRID with "ko" fails (ON_DEVICE_ENGINE_ERROR) and HYBRID with ko_KR stays silent.
            val tts = PleosTts(appContext, Mode.ON_DEVICE)
            pleos = tts
            tts.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    // Listeners can only be added once the service is bound.
                    // With Locale.KOREAN ("ko") the emulator's on-device engine answered
                    // ON_DEVICE_ENGINE_ERROR and stayed silent; the system apps use "ko_KR".
                    runCatching { tts.setLocale(Locale.KOREA) }
                    runCatching { tts.addEventListener(events) }
                    pleosReady = true
                }

                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "Pleos TTS init failed: $type")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "Pleos TTS unavailable: ${e.message}")
        }
    }

    fun speak(text: String) {
        Log.i(TAG, "speak: $text")
        lastText = text
        val p = pleos
        if (pleosReady && !pleosBroken && p != null) {
            try {
                // isFinal = true: each call is a complete utterance. With false the service waits for
                // more text, so the last ~2 s stay in its buffer and play in front of the next sentence.
                p.speak(text, true)
                engine = "Pleos TTS"
                return
            } catch (e: Exception) {
                Log.w(TAG, "Pleos speak failed: ${e.message}")
            }
        }
        speakFallback(text)
    }

    private fun speakFallback(text: String) {
        if (fallbackReady) {
            val result = fallback?.speak(text, AndroidTts.QUEUE_FLUSH, null, "cardoctor")
            engine = "Android TTS"
            Log.i(TAG, "Android TTS result=$result")
        } else {
            Log.w(TAG, "no TTS engine ready")
        }
    }

    /** If Pleos TTS reports an error, say the same sentence with the Android engine instead. */
    private val events = object : EventListener {
        override fun onStart() { Log.i(TAG, "Pleos TTS start") }
        override fun onReady() {}
        override fun onDone() { Log.i(TAG, "Pleos TTS done") }
        override fun onStop() {}
        override fun onUpdatedRms(rms: Double) {}
        override fun onError(type: ErrorType) {
            Log.w(TAG, "Pleos TTS error: $type → Android TTS")
            pleosBroken = true
            lastText?.let { speakFallback(it) }
        }
    }

    @Volatile private var pleosBroken = false
    @Volatile private var lastText: String? = null

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
            Log.i(TAG, "Android TTS init status=$status")
            if (fallbackReady) fallback?.language = Locale.KOREA
        }
    }

    private companion object { const val TAG = "Speaker" }
}
