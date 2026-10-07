package com.surajdev.talkwise.ai

import android.os.SystemClock
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.GenerationConfig
import com.google.firebase.ai.type.GenerativeBackend
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import com.google.firebase.remoteconfig.FirebaseRemoteConfig
import com.surajdev.talkwise.data.Band
import com.surajdev.talkwise.data.ChatLine
import com.surajdev.talkwise.data.Correction
import com.surajdev.talkwise.data.PocketWord
import com.surajdev.talkwise.data.Recap
import com.surajdev.talkwise.data.Scenario
import com.surajdev.talkwise.data.Skills
import com.surajdev.talkwise.data.TutorTurn
import com.surajdev.talkwise.data.UserProfile
import kotlinx.coroutines.CancellationException
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.Duration
import java.time.ZoneId
import java.time.ZonedDateTime

enum class TutorFailure { QUOTA, NETWORK, SETUP, OTHER }

/** Talks to Gemini through Firebase AI Logic. One request per conversation turn. */
class TutorService(private val remoteConfig: FirebaseRemoteConfig) {

    init {
        remoteConfig.setDefaultsAsync(mapOf(MODEL_KEY to DEFAULT_MODEL))
        remoteConfig.fetchAndActivate()
    }

    suspend fun reply(
        profile: UserProfile,
        scenario: Scenario,
        history: List<ChatLine>,
        message: String,
        spoken: Boolean,
    ): TutorTurn {
        val raw = generate(
            system = systemPrompt(profile, scenario, spoken),
            config = turnConfig,
            history = history.toContents(),
            message = message,
        )
        return parseTurn(raw, message)
    }

    suspend fun recap(lines: List<ChatLine>): Recap {
        val transcript = lines.takeLast(40).joinToString("\n") { (if (it.fromTutor) "Tutor: " else "Learner: ") + it.text }
        val raw = generate(
            system = "You review a short English practice conversation and encourage the learner. " +
                "Use simple, warm English and address the learner as \"you\". " +
                "wins: exactly two specific things the learner did well, each under 14 words. " +
                "focus: the single most useful thing to practise next, under 18 words, with a tiny example if helpful.",
            config = recapConfig,
            history = emptyList(),
            message = transcript,
        )
        val json = JSONObject(raw)
        return Recap(
            wins = json.optJSONArray("wins").strings().take(2),
            focus = json.optString("focus").trim(),
        )
    }

    private suspend fun generate(system: String, config: GenerationConfig, history: List<Content>, message: String): String {
        val ai = Firebase.ai(backend = GenerativeBackend.googleAI())
        var failure: Exception = IllegalStateException("No model answered")
        for (name in modelNames()) {
            try {
                val model = ai.generativeModel(
                    modelName = name,
                    generationConfig = config,
                    systemInstruction = content { text(system) },
                )
                val started = SystemClock.elapsedRealtime()
                val text = model.startChat(history).sendMessage(message).text
                Log.d("Talkwise", "$name answered in ${SystemClock.elapsedRealtime() - started} ms")
                if (!text.isNullOrBlank()) return text
                failure = IllegalStateException("Empty response from $name")
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e
                // Only a missing or retired model is worth trying the next name for.
                if (!e.isModelMissing()) throw e
            }
        }
        throw failure
    }

    private fun modelNames(): List<String> =
        listOf(remoteConfig.getString(MODEL_KEY).trim(), DEFAULT_MODEL, FALLBACK_MODEL).filter { it.isNotEmpty() }.distinct()

    private fun parseTurn(raw: String, message: String): TutorTurn {
        val json = JSONObject(raw)
        val reply = json.optString("reply").trim()
        check(reply.isNotEmpty()) { "Tutor reply was empty" }
        val corrected = json.optString("corrected").trim()
        val word = json.optString("word").trim()
        val meaning = json.optString("word_meaning").trim()
        return TutorTurn(
            reply = reply,
            correction = corrected.takeIf { it.isNotEmpty() && !it.sameWordsAs(message) }
                ?.let { Correction(it, json.optString("tip").trim()) },
            word = if (word.isNotEmpty() && meaning.isNotEmpty()) PocketWord(word = word, meaning = meaning) else null,
            scores = if (json.optBoolean("assessable")) {
                Skills(
                    vocabulary = json.optDouble("vocabulary", 0.0).toFloat(),
                    grammar = json.optDouble("grammar", 0.0).toFloat(),
                    fluency = json.optDouble("fluency", 0.0).toFloat(),
                )
            } else {
                null
            },
            suggestions = json.optJSONArray("suggestions").strings().take(2),
        )
    }

    private fun systemPrompt(profile: UserProfile, scenario: Scenario, spoken: Boolean): String {
        val skills = profile.skills
        val style = when (profile.band) {
            Band.BEGINNER -> "Use very common words and short, simple sentences in the present or simple past. " +
                "At most 2 short sentences. Ask easy, concrete questions. Be extra encouraging."
            Band.INTERMEDIATE -> "Use everyday natural English with some idioms and varied tenses. " +
                "At most 3 sentences. Ask open questions that invite a few sentences in reply."
            Band.ADVANCED -> "Use rich, natural, nuanced English, including idioms and precise vocabulary. " +
                "At most 4 sentences. Challenge the learner with opinions, hypotheticals and follow-up questions."
        }
        val setting = scenario.role.ifEmpty {
            "This is free conversation. Follow the learner's interests and keep it lively."
        }
        val input = if (spoken) {
            "The learner is SPEAKING; their words arrive through speech recognition, so ignore punctuation, " +
                "capitalisation and spelling, and do not correct them."
        } else {
            "The learner is TYPING; you may correct spelling, but ignore missing capital letters and final full stops."
        }
        return """
            You are Wise, a warm, witty English conversation partner inside the Talkwise app. The learner practises English by talking with you. Keep the conversation alive, curious and fun. Never lecture.

            LEARNER LEVEL: ${profile.band.label} (vocabulary ${skills.vocabulary.toInt()}/100, grammar ${skills.grammar.toInt()}/100, fluency ${skills.fluency.toInt()}/100).
            HOW TO SPEAK AT THIS LEVEL: $style Stay only a small step above what the learner writes: if their scores sit near the bottom of this level, lean towards simpler words and shorter sentences.
            SETTING: $setting
            INPUT: $input

            Fill the JSON fields like this:
            - reply: what you say next, as natural spoken English. No markdown, lists or emojis. React to what the learner meant first, then end with one question or invitation so they keep talking. Do not mention corrections in the reply.
            - corrected: if the learner's latest message has a clear mistake, the full corrected version of their message; otherwise an empty string. Fix only what matters at their level.
            - tip: one short, friendly sentence in simple words explaining the main fix; empty if there is no correction.
            - word and word_meaning: optionally one useful word or phrase from your reply that is slightly above the learner's level, with a meaning of at most 12 simple words; otherwise empty strings. Never pick a word the learner has already used.
            - suggestions: two short things the learner could say next, written at their level.
            - vocabulary, grammar, fluency: score ONLY the learner's latest message from 0 to 100 (0-39 beginner A1-A2, 40-69 intermediate B1-B2, 70-100 advanced C1-C2). Fluency means how naturally and fully they express the idea. Score the range of language they show, not just correctness: a short, simple sentence stays under 40 even when it has no mistakes.
            - assessable: false if the latest message has fewer than 4 words or is not English, otherwise true.

            If the learner writes in another language, answer in simple English and invite them to try in English. Keep everything kind and appropriate; if a topic is unsafe, decline briefly and steer back to practice.
        """.trimIndent()
    }

    private fun List<ChatLine>.toContents(): List<Content> {
        // Recent turns only, to keep each request small. The API expects the history to open with a user turn.
        val recent = takeLast(HISTORY_LINES).dropWhile { !it.fromTutor }
        if (recent.isEmpty()) return emptyList()
        return listOf(content(role = "user") { text("(The learner has just opened the conversation.)") }) +
            recent.map { line -> content(role = if (line.fromTutor) "model" else "user") { text(line.text) } }
    }

    private companion object {
        const val MODEL_KEY = "tutor_model"
        const val DEFAULT_MODEL = "gemini-3.1-flash-lite"
        const val FALLBACK_MODEL = "gemini-2.5-flash-lite"
        const val HISTORY_LINES = 12

        val turnConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = Schema.obj(
                mapOf(
                    "reply" to Schema.string(),
                    "corrected" to Schema.string(),
                    "tip" to Schema.string(),
                    "word" to Schema.string(),
                    "word_meaning" to Schema.string(),
                    "suggestions" to Schema.array(Schema.string()),
                    "vocabulary" to Schema.integer(),
                    "grammar" to Schema.integer(),
                    "fluency" to Schema.integer(),
                    "assessable" to Schema.boolean(),
                ),
            )
            temperature = 0.9f
            maxOutputTokens = 500
        }

        val recapConfig = generationConfig {
            responseMimeType = "application/json"
            responseSchema = Schema.obj(
                mapOf(
                    "wins" to Schema.array(Schema.string()),
                    "focus" to Schema.string(),
                ),
            )
            temperature = 0.6f
            maxOutputTokens = 200
        }
    }
}

fun Throwable.toTutorFailure(): TutorFailure {
    val chain = generateSequence(this) { it.cause }.take(6).toList()
    val text = chain.joinToString(" ") { "${it::class.simpleName} ${it.message}" }.lowercase()
    return when {
        "quota" in text || "429" in text || "resource_exhausted" in text || "rate limit" in text -> TutorFailure.QUOTA
        chain.any { it is IOException } || "unable to resolve host" in text || "timeout" in text ||
            "network" in text || "failed to connect" in text -> TutorFailure.NETWORK
        "api key" in text || "permission_denied" in text || "403" in text || "app check" in text ||
            "has not been used" in text || "is disabled" in text || "not enabled" in text -> TutorFailure.SETUP
        else -> TutorFailure.OTHER
    }
}

/**
 * How long until the quota that rejected this request frees up, in milliseconds.
 * A daily quota resets at midnight Pacific time; a per-minute one names its own delay ("Please retry in 34.5s").
 */
fun Throwable.quotaWaitMillis(now: ZonedDateTime = ZonedDateTime.now()): Long {
    val text = generateSequence(this) { it.cause }.take(6).joinToString(" ") { it.message.orEmpty() }.lowercase()
    if ("perday" in text || "per day" in text) {
        val pacific = now.withZoneSameInstant(ZoneId.of("America/Los_Angeles"))
        return Duration.between(pacific, pacific.toLocalDate().plusDays(1).atStartOfDay(pacific.zone)).toMillis()
    }
    val delay = Regex("""retry in ((?:\d+(?:\.\d+)?(?:ms|h|m|s))+)""").find(text)?.groupValues?.get(1)
        ?: return DEFAULT_QUOTA_WAIT_MS
    val millis = Regex("""(\d+(?:\.\d+)?)(ms|h|m|s)""").findAll(delay).sumOf { part ->
        val amount = part.groupValues[1].toDouble()
        when (part.groupValues[2]) {
            "h" -> amount * 3_600_000
            "m" -> amount * 60_000
            "s" -> amount * 1_000
            else -> amount
        }
    }
    return millis.toLong().coerceAtLeast(1_000)
}

/** Per-minute limits are the common case, and they clear within a minute. */
private const val DEFAULT_QUOTA_WAIT_MS = 60_000L

private fun Throwable.isModelMissing(): Boolean {
    val text = generateSequence(this) { it.cause }.take(6).joinToString(" ") { it.message.orEmpty() }.lowercase()
    return "404" in text || "not found" in text || "not_found" in text || "is not supported" in text
}

private fun JSONArray?.strings(): List<String> =
    if (this == null) emptyList() else (0 until length()).map { optString(it).trim() }.filter { it.isNotEmpty() }

private fun String.sameWordsAs(other: String): Boolean {
    fun String.words() = lowercase().filter { it.isLetterOrDigit() || it.isWhitespace() }.split(Regex("\\s+")).filter { it.isNotEmpty() }
    return words() == other.words()
}
