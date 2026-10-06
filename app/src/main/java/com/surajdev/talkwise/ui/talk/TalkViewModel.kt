package com.surajdev.talkwise.ui.talk

import android.os.SystemClock
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.surajdev.talkwise.AppContainer
import com.surajdev.talkwise.ai.TutorFailure
import com.surajdev.talkwise.ai.toTutorFailure
import com.surajdev.talkwise.data.Band
import com.surajdev.talkwise.data.ChatLine
import com.surajdev.talkwise.data.Correction
import com.surajdev.talkwise.data.LevelEngine
import com.surajdev.talkwise.data.PocketWord
import com.surajdev.talkwise.data.Recap
import com.surajdev.talkwise.data.Scenario
import com.surajdev.talkwise.data.Scenarios
import com.surajdev.talkwise.data.UserProfile
import com.surajdev.talkwise.speech.SpeechFailure
import com.surajdev.talkwise.speech.SpeechInput
import com.surajdev.talkwise.speech.Voice
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate

enum class Phase { IDLE, LISTENING, THINKING, SPEAKING }

data class Notice(val text: String, val canRetry: Boolean = false)

data class TalkUi(
    val scenario: Scenario = Scenarios.free,
    val phase: Phase = Phase.IDLE,
    val voiceMode: Boolean = true,
    val soundOn: Boolean = true,
    val tutorText: String = "",
    val userText: String = "",
    val correction: Correction? = null,
    val word: PocketWord? = null,
    val wordSaved: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val notice: Notice? = null,
    val lines: List<ChatLine> = emptyList(),
    val levelUp: Band? = null,
)

data class RecapUi(
    val loading: Boolean,
    val minutes: Int,
    val turns: Int,
    val wordsSaved: Int,
    val wins: List<String> = emptyList(),
    val focus: String = "",
)

/** Drives one conversation: listening, asking the tutor, speaking the reply, and the end-of-session recap. */
class TalkViewModel(private val container: AppContainer, private val backend: AppContainer.Backend) : ViewModel() {
    private val speech = SpeechInput(container.app)
    private val voice = Voice(container.app)

    private val _ui = MutableStateFlow(TalkUi())
    val ui: StateFlow<TalkUi> = _ui

    private val _recap = MutableStateFlow<RecapUi?>(null)
    val recap: StateFlow<RecapUi?> = _recap

    val micLevel: StateFlow<Float> = speech.level
    val partial: StateFlow<String> = speech.partial
    val voiceBeat: StateFlow<Int> = voice.beat

    private var request: Job? = null
    private var lastFailed: String? = null
    private var lastFailedOwnWords = true
    private var userTurns = 0
    private var wordsSaved = 0
    private var practisedMs = 0L
    private var runningSince: Long? = null

    init {
        speech.onFinal = { send(it) }
        speech.onFailure = ::onSpeechFailure
    }

    fun begin(scenario: Scenario) {
        halt()
        val profile = backend.users.profile.value ?: UserProfile()
        val opener = scenario.openerFor(profile.band)
        userTurns = 0
        wordsSaved = 0
        practisedMs = 0L
        lastFailed = null
        _recap.value = null
        _ui.value = TalkUi(
            scenario = scenario,
            voiceMode = container.prefs.getBoolean(PREF_VOICE_MODE, true) && speech.available,
            soundOn = container.prefs.getBoolean(PREF_SOUND, true),
            tutorText = opener,
            lines = listOf(ChatLine(fromTutor = true, text = opener)),
        )
        resume()
        say(opener, profile.band)
    }

    fun toggleMic() {
        when (_ui.value.phase) {
            Phase.THINKING -> Unit
            Phase.LISTENING -> speech.stop()
            else -> {
                voice.stop()
                _ui.update { it.copy(phase = Phase.LISTENING, notice = null) }
                speech.start()
            }
        }
    }

    fun onMicDenied() {
        setVoiceMode(false)
        _ui.update { it.copy(notice = Notice("Microphone access is off, so let's type instead. You can allow it in Settings.")) }
    }

    fun setVoiceMode(voiceMode: Boolean) {
        if (voiceMode && !speech.available) {
            _ui.update { it.copy(notice = Notice("Voice input isn't available on this device. Let's type instead.")) }
            return
        }
        if (_ui.value.phase == Phase.LISTENING) {
            speech.cancel()
            _ui.update { it.copy(phase = Phase.IDLE) }
        }
        container.prefs.edit { putBoolean(PREF_VOICE_MODE, voiceMode) }
        _ui.update { it.copy(voiceMode = voiceMode) }
    }

    fun toggleSound() {
        val on = !_ui.value.soundOn
        container.prefs.edit { putBoolean(PREF_SOUND, on) }
        if (!on) {
            voice.stop()
            if (_ui.value.phase == Phase.SPEAKING) _ui.update { it.copy(phase = Phase.IDLE) }
        }
        _ui.update { it.copy(soundOn = on) }
    }

    /** Sends a suggested reply. It is not the learner's own wording, so it does not count towards their level. */
    fun sendSuggestion(text: String) = send(text, ownWords = false)

    fun send(text: String, ownWords: Boolean = true) {
        val message = text.trim()
        val state = _ui.value
        if (message.isEmpty() || state.phase == Phase.THINKING) return
        voice.stop()
        speech.cancel()

        val profile = backend.users.profile.value ?: UserProfile()
        if (profile.turnsOn(LocalDate.now().toString()) >= DAILY_TURNS) {
            _ui.update {
                it.copy(phase = Phase.IDLE, notice = Notice("That's a full day of practice. Wise will be rested and ready tomorrow."))
            }
            return
        }

        val history = state.lines
        lastFailed = null
        _ui.update {
            it.copy(
                phase = Phase.THINKING,
                userText = message,
                correction = null,
                word = null,
                wordSaved = false,
                suggestions = emptyList(),
                notice = null,
                lines = history + ChatLine(fromTutor = false, text = message),
            )
        }
        request = viewModelScope.launch {
            try {
                val turn = backend.tutor.reply(profile, state.scenario, history, message, spoken = state.voiceMode)
                userTurns += 1
                val after = backend.users.recordTurn(turn.scores.takeIf { ownWords })
                _ui.update {
                    it.copy(
                        tutorText = turn.reply,
                        correction = turn.correction,
                        word = turn.word,
                        suggestions = turn.suggestions,
                        lines = history + ChatLine(false, message, turn.correction) + ChatLine(true, turn.reply),
                        levelUp = after?.band?.takeIf { band -> // The first few turns only calibrate the estimate; a jump there is not an achievement.
                            band.ordinal > profile.band.ordinal && profile.assessedTurns >= LevelEngine.CALIBRATION_TURNS
                        },
                    )
                }
                say(turn.reply, after?.band ?: profile.band)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w("Talkwise", "Tutor request failed", e)
                lastFailed = message
                lastFailedOwnWords = ownWords
                // Drop the unanswered line so the history sent to the model keeps alternating.
                _ui.update { it.copy(phase = Phase.IDLE, lines = history, notice = e.toNotice()) }
            }
        }
    }

    fun retry() {
        lastFailed?.let { send(it, lastFailedOwnWords) }
    }

    fun saveWord() {
        val word = _ui.value.word ?: return
        if (_ui.value.wordSaved) return
        backend.users.saveWord(word)
        wordsSaved += 1
        _ui.update { it.copy(wordSaved = true) }
    }

    fun dismissNotice() = _ui.update { it.copy(notice = null) }

    fun dismissLevelUp() = _ui.update { it.copy(levelUp = null) }

    /** Called when the screen leaves the foreground. */
    fun pause() {
        speech.cancel()
        voice.stop()
        if (_ui.value.phase == Phase.LISTENING || _ui.value.phase == Phase.SPEAKING) {
            _ui.update { it.copy(phase = Phase.IDLE) }
        }
        runningSince?.let { practisedMs += SystemClock.elapsedRealtime() - it }
        runningSince = null
    }

    fun resume() {
        if (runningSince == null) runningSince = SystemClock.elapsedRealtime()
    }

    /** Ends the session. Returns true if there is a recap to show, false if the learner never spoke. */
    fun end(): Boolean {
        halt()
        val seconds = (practisedMs / 1000).toInt()
        practisedMs = 0L
        if (userTurns == 0) return false
        backend.users.addPracticeSeconds(seconds)

        val lines = _ui.value.lines
        val turns = userTurns
        val scenarioId = _ui.value.scenario.id
        val summary = RecapUi(loading = true, minutes = maxOf(1, seconds / 60), turns = turns, wordsSaved = wordsSaved)
        _recap.value = summary
        viewModelScope.launch {
            val recap = try {
                backend.tutor.recap(lines).takeIf { it.wins.isNotEmpty() && it.focus.isNotEmpty() }
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                null
            } ?: localRecap(lines, turns)
            backend.users.saveSession(scenarioId, seconds, turns, recap)
            _recap.value = summary.copy(loading = false, wins = recap.wins, focus = recap.focus)
        }
        return true
    }

    /** Used when the recap request fails, so the screen never ends on an error. */
    private fun localRecap(lines: List<ChatLine>, turns: Int): Recap {
        val lastFix = lines.lastOrNull { it.correction != null }?.correction
        return Recap(
            wins = listOf(
                "You kept the conversation going for $turns ${if (turns == 1) "turn" else "turns"}.",
                "You showed up and practised today.",
            ),
            focus = lastFix?.let { "Say this once more out loud: \"${it.corrected}\"" }
                ?: "Next time, try answering with one extra sentence.",
        )
    }

    private fun say(text: String, band: Band) {
        // Stay silent if muted or if the app went to the background while the tutor was thinking.
        if (!_ui.value.soundOn || runningSince == null) {
            _ui.update { it.copy(phase = Phase.IDLE) }
            return
        }
        _ui.update { it.copy(phase = Phase.SPEAKING) }
        val rate = when (band) {
            Band.BEGINNER -> 0.9f
            Band.INTERMEDIATE -> 0.98f
            Band.ADVANCED -> 1.05f
        }
        voice.speak(text, rate) {
            if (_ui.value.phase == Phase.SPEAKING) _ui.update { it.copy(phase = Phase.IDLE) }
        }
    }

    private fun onSpeechFailure(failure: SpeechFailure) {
        if (_ui.value.phase != Phase.LISTENING) return
        val notice = when (failure) {
            SpeechFailure.NOTHING_HEARD -> Notice("I didn't catch that. Tap the mic and try again.")
            SpeechFailure.NETWORK -> Notice("Voice input needs a connection. Check your internet, or type instead.")
            SpeechFailure.PERMISSION -> Notice("Microphone access is off. Allow it in Settings, or type instead.")
            SpeechFailure.UNAVAILABLE -> Notice("Voice input isn't available on this device. Let's type instead.")
            SpeechFailure.OTHER -> Notice("The microphone had a hiccup. Tap to try again.")
        }
        _ui.update {
            it.copy(phase = Phase.IDLE, notice = notice, voiceMode = it.voiceMode && failure != SpeechFailure.UNAVAILABLE)
        }
    }

    private fun Exception.toNotice(): Notice = when (toTutorFailure()) {
        TutorFailure.QUOTA -> Notice("Wise is catching a breath after a busy spell. Try again in a minute.", canRetry = true)
        TutorFailure.NETWORK -> Notice("The connection dropped. Check your internet and try again.", canRetry = true)
        TutorFailure.SETUP -> Notice("The AI service turned this app away. Check that Firebase AI Logic is enabled and App Check is set up.", canRetry = true)
        TutorFailure.OTHER -> Notice("That reply got lost on the way. Try again.", canRetry = true)
    }

    private fun halt() {
        request?.cancel()
        request = null
        pause()
        if (_ui.value.phase != Phase.IDLE) _ui.update { it.copy(phase = Phase.IDLE) }
    }

    override fun onCleared() {
        speech.destroy()
        voice.shutdown()
    }

    private companion object {
        const val PREF_VOICE_MODE = "voice_mode"
        const val PREF_SOUND = "sound_on"

        /** Keeps one learner from using up the shared free quota. */
        const val DAILY_TURNS = 60
    }
}
