package com.surajdev.talkwise.data

import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class LevelState(val skills: Skills, val band: Band, val assessedTurns: Int)

object LevelEngine {
    /** Turns during which the estimate follows the learner directly instead of being smoothed. */
    const val CALIBRATION_TURNS = 3
    private const val SMOOTHING = 0.2f
    private const val INTERMEDIATE_AT = 40f
    private const val ADVANCED_AT = 70f

    /** How far past a threshold the score must move before the band changes, so it does not flip-flop. */
    private const val MARGIN = 4f

    fun update(state: LevelState, sample: Skills): LevelState {
        val alpha = maxOf(SMOOTHING, 1f / (state.assessedTurns + 1))
        val skills = Skills(
            vocabulary = blend(state.skills.vocabulary, sample.vocabulary, alpha),
            grammar = blend(state.skills.grammar, sample.grammar, alpha),
            fluency = blend(state.skills.fluency, sample.fluency, alpha),
        )
        val turns = state.assessedTurns + 1
        val band = if (turns <= CALIBRATION_TURNS) rawBand(skills.overall) else settledBand(skills.overall, state.band)
        return LevelState(skills, band, turns)
    }

    private fun blend(old: Float, new: Float, alpha: Float) =
        (old + (new.coerceIn(0f, 100f) - old) * alpha).coerceIn(0f, 100f)

    private fun rawBand(overall: Float) = when {
        overall >= ADVANCED_AT -> Band.ADVANCED
        overall >= INTERMEDIATE_AT -> Band.INTERMEDIATE
        else -> Band.BEGINNER
    }

    private fun settledBand(overall: Float, current: Band): Band {
        val up = rawBand(overall - MARGIN)
        val down = rawBand(overall + MARGIN)
        return when {
            up.ordinal > current.ordinal -> up
            down.ordinal < current.ordinal -> down
            else -> current
        }
    }
}

data class StreakState(val streak: Int, val best: Int, val freezes: Int, val lastActiveDay: LocalDate?)

object StreakEngine {
    const val MAX_FREEZES = 2
    private const val DAYS_PER_FREEZE = 7

    /** Call when the learner completes a turn on [today]. */
    fun onActive(state: StreakState, today: LocalDate): StreakState {
        val gap = state.lastActiveDay?.let { ChronoUnit.DAYS.between(it, today) }
        var freezes = state.freezes
        val streak = when {
            gap == null -> 1
            gap <= 0L -> return state.copy(streak = maxOf(state.streak, 1))
            gap == 1L -> state.streak + 1
            gap == 2L && freezes > 0 -> {
                freezes -= 1
                state.streak + 1
            }
            else -> 1
        }
        if (streak % DAYS_PER_FREEZE == 0) freezes = minOf(freezes + 1, MAX_FREEZES)
        return StreakState(streak, maxOf(state.best, streak), freezes, today)
    }

    /** The streak to show on [today]: a streak that can no longer be continued reads as zero. */
    fun visible(state: StreakState, today: LocalDate): Int {
        val gap = state.lastActiveDay?.let { ChronoUnit.DAYS.between(it, today) } ?: return 0
        return when {
            gap <= 1L -> state.streak
            gap == 2L && state.freezes > 0 -> state.streak
            else -> 0
        }
    }
}
