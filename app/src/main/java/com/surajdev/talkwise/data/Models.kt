package com.surajdev.talkwise.data

enum class Band(val label: String) {
    BEGINNER("Beginner"),
    INTERMEDIATE("Intermediate"),
    ADVANCED("Advanced"),
}

/** Each skill is a 0–100 score: 0–39 beginner, 40–69 intermediate, 70–100 advanced. */
data class Skills(
    val vocabulary: Float = 35f,
    val grammar: Float = 35f,
    val fluency: Float = 35f,
) {
    val overall: Float get() = (vocabulary + grammar + fluency) / 3f
}

data class UserProfile(
    val onboarded: Boolean = false,
    val skills: Skills = Skills(),
    val band: Band = Band.BEGINNER,
    val assessedTurns: Int = 0,
    val streak: Int = 0,
    val bestStreak: Int = 0,
    val freezes: Int = 1,
    val lastActiveDay: String? = null,
    val goalMinutes: Int = 10,
    val today: String? = null,
    val todaySeconds: Int = 0,
    val todayTurns: Int = 0,
) {
    /** Daily counters only count if they were written on [day]. */
    fun secondsOn(day: String): Int = if (today == day) todaySeconds else 0
    fun turnsOn(day: String): Int = if (today == day) todayTurns else 0
}

data class PocketWord(
    val id: String = "",
    val word: String,
    val meaning: String,
    val addedAt: Long = 0L,
)

data class Correction(val corrected: String, val tip: String)

data class ChatLine(val fromTutor: Boolean, val text: String, val correction: Correction? = null)

data class TutorTurn(
    val reply: String,
    val correction: Correction?,
    val word: PocketWord?,
    val scores: Skills?,
    val suggestions: List<String>,
)

data class Recap(val wins: List<String>, val focus: String)

data class Scenario(
    val id: String,
    val title: String,
    val subtitle: String,
    /** What the tutor is told to play. Empty for free conversation. */
    val role: String,
    val simpleOpener: String,
    val opener: String,
) {
    fun openerFor(band: Band): String = if (band == Band.BEGINNER) simpleOpener else opener
}
