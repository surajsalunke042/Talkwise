package com.surajdev.talkwise

import com.surajdev.talkwise.data.Band
import com.surajdev.talkwise.data.LevelEngine
import com.surajdev.talkwise.data.LevelState
import com.surajdev.talkwise.data.Skills
import com.surajdev.talkwise.data.StreakEngine
import com.surajdev.talkwise.data.StreakState
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ProgressTest {
    private val day = LocalDate.of(2026, 10, 4)

    private fun flat(score: Float) = Skills(score, score, score)

    @Test
    fun firstSampleSetsTheLevelDirectly() {
        val next = LevelEngine.update(LevelState(Skills(), Band.BEGINNER, 0), flat(80f))
        assertEquals(80f, next.skills.overall, 0.01f)
        assertEquals(Band.ADVANCED, next.band)
    }

    @Test
    fun oneOutlierDoesNotChangeASettledBand() {
        val settled = LevelState(flat(55f), Band.INTERMEDIATE, 20)
        assertEquals(Band.INTERMEDIATE, LevelEngine.update(settled, flat(95f)).band)
        assertEquals(Band.INTERMEDIATE, LevelEngine.update(settled, flat(5f)).band)
    }

    @Test
    fun sustainedImprovementRaisesTheBand() {
        var state = LevelState(flat(55f), Band.INTERMEDIATE, 20)
        repeat(12) { state = LevelEngine.update(state, flat(90f)) }
        assertEquals(Band.ADVANCED, state.band)
    }

    @Test
    fun bandHoldsInsideTheMarginAroundAThreshold() {
        val justBelow = LevelState(flat(68f), Band.ADVANCED, 20)
        assertEquals(Band.ADVANCED, LevelEngine.update(justBelow, flat(68f)).band)
        val justAbove = LevelState(flat(72f), Band.INTERMEDIATE, 20)
        assertEquals(Band.INTERMEDIATE, LevelEngine.update(justAbove, flat(72f)).band)
    }

    @Test
    fun streakStartsAndGrowsOnConsecutiveDays() {
        val first = StreakEngine.onActive(StreakState(0, 0, 1, null), day)
        assertEquals(1, first.streak)
        val second = StreakEngine.onActive(first, day.plusDays(1))
        assertEquals(2, second.streak)
        assertEquals(2, second.best)
    }

    @Test
    fun sameDayActivityDoesNotGrowTheStreak() {
        val state = StreakState(4, 4, 1, day)
        assertEquals(4, StreakEngine.onActive(state, day).streak)
    }

    @Test
    fun aFreezeCoversOneMissedDay() {
        val next = StreakEngine.onActive(StreakState(4, 4, 1, day), day.plusDays(2))
        assertEquals(5, next.streak)
        assertEquals(0, next.freezes)
    }

    @Test
    fun streakResetsWithoutAFreezeOrAfterALongGap() {
        assertEquals(1, StreakEngine.onActive(StreakState(4, 9, 0, day), day.plusDays(2)).streak)
        val reset = StreakEngine.onActive(StreakState(4, 9, 2, day), day.plusDays(3))
        assertEquals(1, reset.streak)
        assertEquals(9, reset.best)
    }

    @Test
    fun aFreezeIsEarnedEverySevenDays() {
        val next = StreakEngine.onActive(StreakState(6, 6, 0, day), day.plusDays(1))
        assertEquals(7, next.streak)
        assertEquals(1, next.freezes)
    }

    @Test
    fun visibleStreakDropsToZeroOnceItCannotBeContinued() {
        val state = StreakState(4, 4, 0, day)
        assertEquals(4, StreakEngine.visible(state, day.plusDays(1)))
        assertEquals(0, StreakEngine.visible(state, day.plusDays(2)))
        assertEquals(4, StreakEngine.visible(state.copy(freezes = 1), day.plusDays(2)))
    }
}
