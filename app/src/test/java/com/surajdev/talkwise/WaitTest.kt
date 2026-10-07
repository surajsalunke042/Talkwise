package com.surajdev.talkwise

import com.surajdev.talkwise.ai.quotaWaitMillis
import com.surajdev.talkwise.ui.talk.waitText
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.ZonedDateTime

class WaitTest {
    private val noon = ZonedDateTime.parse("2026-10-07T12:00:00-07:00[America/Los_Angeles]")

    @Test
    fun `a per-minute quota waits for the delay the server names`() {
        val error = RuntimeException("You exceeded your current quota. Please retry in 34.5s.")
        assertEquals(34_500L, error.quotaWaitMillis(noon))
    }

    @Test
    fun `a delay with hours and minutes is added up`() {
        val error = RuntimeException("Quota exceeded. Please retry in 1h2m3s.")
        assertEquals(3_723_000L, error.quotaWaitMillis(noon))
    }

    @Test
    fun `a daily quota waits until midnight Pacific wherever the learner is`() {
        val error = RuntimeException("Quota exceeded for quotaId GenerateRequestsPerDayPerProjectPerModel-FreeTier. Please retry in 20s.")
        assertEquals(12 * 3_600_000L, error.quotaWaitMillis(noon))
        assertEquals(12 * 3_600_000L, error.quotaWaitMillis(ZonedDateTime.parse("2026-10-08T00:30:00+05:30[Asia/Kolkata]")))
    }

    @Test
    fun `the delay is found in a wrapped cause`() {
        val error = IllegalStateException("Request failed", RuntimeException("Please retry in 5s"))
        assertEquals(5_000L, error.quotaWaitMillis(noon))
    }

    @Test
    fun `an unexplained quota error waits a minute`() {
        assertEquals(60_000L, RuntimeException("429 Resource exhausted").quotaWaitMillis(noon))
    }

    @Test
    fun `waits read in plain words and round up`() {
        assertEquals("1 second", waitText(400))
        assertEquals("42 seconds", waitText(41_200))
        assertEquals("1 minute", waitText(60_000))
        assertEquals("2 minutes", waitText(61_000))
        assertEquals("1 hour", waitText(3_600_000))
        assertEquals("5 hours 12 minutes", waitText(5 * 3_600_000L + 11 * 60_000L + 1_000))
        assertEquals("0 seconds", waitText(-50))
    }
}
