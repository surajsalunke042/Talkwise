package com.surajdev.talkwise.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SpeechFailure { NOTHING_HEARD, NETWORK, PERMISSION, UNAVAILABLE, OTHER }

/** Wraps the device speech recogniser. All calls must come from the main thread. */
class SpeechInput(private val context: Context) {
    private var recognizer: SpeechRecognizer? = null

    private val _level = MutableStateFlow(0f)

    /** Microphone loudness, 0..1, while listening. */
    val level: StateFlow<Float> = _level

    private val _partial = MutableStateFlow("")
    val partial: StateFlow<String> = _partial

    var onFinal: (String) -> Unit = {}
    var onFailure: (SpeechFailure) -> Unit = {}

    val available: Boolean get() = SpeechRecognizer.isRecognitionAvailable(context)

    fun start() {
        if (!available) {
            onFailure(SpeechFailure.UNAVAILABLE)
            return
        }
        val active = recognizer ?: SpeechRecognizer.createSpeechRecognizer(context).also {
            it.setRecognitionListener(listener)
            recognizer = it
        }
        _partial.value = ""
        _level.value = 0f
        active.startListening(
            Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                .putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
                .putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                // Learners pause to think; ask for a longer silence before the recogniser gives up.
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L)
                .putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2000L),
        )
    }

    /** Stops recording and delivers whatever was heard. */
    fun stop() {
        recognizer?.stopListening()
    }

    fun cancel() {
        recognizer?.cancel()
        _level.value = 0f
        _partial.value = ""
    }

    fun destroy() {
        recognizer?.destroy()
        recognizer = null
    }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = Unit
        override fun onBeginningOfSpeech() = Unit
        override fun onBufferReceived(buffer: ByteArray?) = Unit
        override fun onEvent(eventType: Int, params: Bundle?) = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // Recognisers report roughly -2 dB (silence) to 10 dB (loud speech).
            _level.value = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
        }

        override fun onEndOfSpeech() {
            _level.value = 0f
        }

        override fun onPartialResults(partialResults: Bundle?) {
            partialResults.best()?.let { _partial.value = it }
        }

        override fun onResults(results: Bundle?) {
            _level.value = 0f
            val text = results.best() ?: _partial.value
            _partial.value = ""
            if (text.isBlank()) onFailure(SpeechFailure.NOTHING_HEARD) else onFinal(text)
        }

        override fun onError(error: Int) {
            _level.value = 0f
            val heard = _partial.value
            _partial.value = ""
            // Some recognisers report "no match" even though partial results arrived; keep what was heard.
            if (heard.isNotBlank() && (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)) {
                onFinal(heard)
                return
            }
            onFailure(
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> SpeechFailure.NOTHING_HEARD
                    SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> SpeechFailure.NETWORK
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> SpeechFailure.PERMISSION
                    else -> SpeechFailure.OTHER
                },
            )
        }
    }

    private fun Bundle?.best(): String? =
        this?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.trim()?.takeIf { it.isNotEmpty() }
}
