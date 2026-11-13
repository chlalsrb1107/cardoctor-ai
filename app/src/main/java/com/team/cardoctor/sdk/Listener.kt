package com.team.cardoctor.sdk

import ai.pleos.playground.stt.SpeechToText
import ai.pleos.playground.stt.constant.ErrorType
import ai.pleos.playground.stt.constant.Mode
import ai.pleos.playground.stt.listener.OnInitializeListener
import ai.pleos.playground.stt.listener.ResultListener
import android.content.Context
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.util.Locale

data class VoiceState(
    val available: Boolean = false,
    val listening: Boolean = false,
    val transcript: String? = null,
    val reply: String? = null,
)

/**
 * Pleos SpeechToText (feature A2: "지금 가야 돼?"). One utterance per [listen] call; the final
 * transcript is handed to [onFinal]. The UI shows the partial text while the driver speaks.
 */
class Listener(context: Context, private val onFinal: (String) -> Unit) {

    private val stt = runCatching { SpeechToText(context.applicationContext, Mode.HYBRID) }.getOrNull()
    private val _state = MutableStateFlow(VoiceState())
    val state: StateFlow<VoiceState> = _state.asStateFlow()

    private val listener = object : ResultListener {
        override fun onUpdated(text: String, isFinal: Boolean) {
            _state.update { it.copy(transcript = text) }
            if (isFinal && text.isNotBlank()) {
                _state.update { it.copy(listening = false) }
                onFinal(text)
            }
        }
        override fun onUpdatedEpdData(start: Long, end: Long) = Unit
        override fun onStartedRecognition() = _state.update { it.copy(listening = true) }
        override fun onEndedRecognition() = _state.update { it.copy(listening = false) }
        override fun onError(type: ErrorType) {
            Log.w(TAG, "STT error: $type")
            _state.update { it.copy(listening = false) }
        }
        override fun onReady() = Unit
        override fun onUpdatedRms(rms: Double) = Unit
    }

    fun start() {
        val s = stt ?: return
        try {
            s.initialize(object : OnInitializeListener {
                override fun onSuccess() {
                    runCatching { s.setLocale(Locale.KOREAN) }
                    s.addListener(listener)
                    _state.update { it.copy(available = true) }
                }
                override fun onFailure(type: ErrorType) {
                    Log.w(TAG, "STT init failed: $type")
                }
            })
        } catch (e: Exception) {
            Log.w(TAG, "STT unavailable: ${e.message}")
        }
    }

    fun listen() {
        val s = stt
        if (s == null || !_state.value.available) {
            _state.update { it.copy(listening = false, transcript = "(음성 인식 사용 불가)", reply = UNAVAILABLE) }
            return
        }
        _state.update { it.copy(listening = true, transcript = null, reply = null) }
        runCatching { s.request() }.onFailure { _state.update { st -> st.copy(listening = false) } }
    }

    /** Hide the bubble, but only if nothing newer has been asked since [transcript]. */
    fun clearIf(transcript: String) = _state.update {
        if (it.transcript == transcript && !it.listening) it.copy(transcript = null, reply = null) else it
    }

    /** Used by the demo panel (emulator without a mic) and after answering. */
    fun showExchange(transcript: String, reply: String) = _state.update { it.copy(transcript = transcript, reply = reply, listening = false) }

    fun stop() {
        runCatching {
            stt?.stop()
            stt?.removeListener(listener)
            stt?.release()
        }
    }

    companion object {
        private const val TAG = "Listener"
        const val UNAVAILABLE = "이 환경에서는 Pleos 음성 인식 서비스에 연결되지 않았습니다. 데모 패널에서 질문을 골라 보세요."
    }
}
