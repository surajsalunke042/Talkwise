package com.surajdev.talkwise.speech

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.util.Locale
import android.speech.tts.Voice as TtsVoice

/** The tutor's voice, using the device text-to-speech engine. */
class Voice(context: Context) {
    private val main = Handler(Looper.getMainLooper())
    private var ready = false
    private var pending: (() -> Unit)? = null
    private var onDone: (() -> Unit)? = null
    private var utterance = 0

    /** The on-device twin of the online voice, used if the online one fails. Null once we have fallen back. */
    private var fallback: TtsVoice? = null
    private var lastText = ""
    private var lastRate = 1f

    private val _beat = MutableStateFlow(0)

    /** Increments as each word starts, so the UI can pulse in time with speech. */
    val beat: StateFlow<Int> = _beat

    // Google's engine has the most natural voices; Android falls back to the default engine if it is missing.
    private val tts: TextToSpeech = TextToSpeech(
        context.applicationContext,
        { status ->
            main.post {
                ready = status == TextToSpeech.SUCCESS
                if (ready) configure()
                pending?.invoke()
                pending = null
            }
        },
        GOOGLE_ENGINE,
    )

    private fun configure() {
        tts.language = Locale.US
        runCatching {
            val installed = tts.voices.orEmpty()
                .filter { it.locale.language == "en" && "notInstalled" !in it.features.orEmpty() }
            // The online voices are neural and sound far less robotic than the on-device ones.
            val online = installed.filter { it.isNetworkConnectionRequired }.best()
            val local = installed.filter { !it.isNetworkConnectionRequired }.best()
            (online ?: local)?.let { tts.voice = it }
            fallback = local.takeIf { online != null }
            Log.d("Talkwise", "Tutor voice: ${(online ?: local)?.name}")
        }
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) = Unit

            override fun onRangeStart(utteranceId: String?, start: Int, end: Int, frame: Int) {
                _beat.value += 1
            }

            override fun onDone(utteranceId: String?) = finish(utteranceId)

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) = failed(utteranceId)

            override fun onError(utteranceId: String?, errorCode: Int) = failed(utteranceId)

            override fun onStop(utteranceId: String?, interrupted: Boolean) = finish(utteranceId)
        })
    }

    private fun List<TtsVoice>.best(): TtsVoice? = minWithOrNull(
        compareBy(
            { voice -> PREFERRED.indexOfFirst { voice.name.startsWith(it) }.let { if (it < 0) PREFERRED.size else it } },
            { it.locale.country != "US" },
            { -it.quality },
        ),
    )

    private fun finish(utteranceId: String?) {
        main.post {
            if (utteranceId != utterance.toString()) return@post
            val callback = onDone
            onDone = null
            callback?.invoke()
        }
    }

    /** If the online voice could not speak (for example, a weak connection), say it again with the on-device voice. */
    private fun failed(utteranceId: String?) {
        main.post {
            if (utteranceId != utterance.toString()) return@post
            val local = fallback
            if (local == null || onDone == null) {
                finish(utteranceId)
                return@post
            }
            Log.w("Talkwise", "Online voice failed; switching to ${local.name}")
            fallback = null
            tts.voice = local
            start(lastText, lastRate)
        }
    }

    /** Speaks [text] at [rate] (1.0 is normal speed). [done] always runs on the main thread, even on failure. */
    fun speak(text: String, rate: Float, done: () -> Unit) {
        val run = {
            if (!ready) {
                done()
            } else {
                onDone = done
                start(text, rate)
            }
        }
        if (ready) run() else pending = run
    }

    private fun start(text: String, rate: Float) {
        utterance += 1
        lastText = text
        lastRate = rate
        tts.setSpeechRate(rate)
        if (tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, utterance.toString()) != TextToSpeech.SUCCESS) {
            val callback = onDone
            onDone = null
            callback?.invoke()
        }
    }

    fun stop() {
        pending = null
        onDone = null
        if (ready) tts.stop()
    }

    fun shutdown() {
        stop()
        tts.shutdown()
    }

    private companion object {
        const val GOOGLE_ENGINE = "com.google.android.tts"

        /** Google voice families in order of preference; change the order to try a different voice. */
        val PREFERRED = listOf("en-us-x-tpf", "en-us-x-iob", "en-us-x-sfg", "en-us-x-iog", "en-us-x-tpc", "en-us-x-tpd", "en-us-x-iom", "en-us-x-iol")
    }
}
