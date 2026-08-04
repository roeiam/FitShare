package com.roeiamor.fitshare.util

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Unit tests for [TimeFormatter].
 *
 * These run on the JVM because `describe` returns a [RelativeTime] value rather than text, and takes
 * "now" as a parameter instead of reading the system clock - so the boundaries are testable and the
 * results are not a race against real time.
 */
class TimeFormatterTest {

    private val now = 1_700_000_000_000L

    private fun ago(amount: Long, unit: TimeUnit): RelativeTime =
        TimeFormatter.describe(now - unit.toMillis(amount), now)

    @Test
    fun `a few seconds ago is now`() {
        assertEquals(RelativeTime.Now, ago(30, TimeUnit.SECONDS))
    }

    @Test
    fun `exactly one minute ago is one minute`() {
        assertEquals(RelativeTime.Minutes(1), ago(1, TimeUnit.MINUTES))
    }

    @Test
    fun `five minutes ago is five minutes`() {
        assertEquals(RelativeTime.Minutes(5), ago(5, TimeUnit.MINUTES))
    }

    @Test
    fun `fifty nine minutes ago is still minutes`() {
        assertEquals(RelativeTime.Minutes(59), ago(59, TimeUnit.MINUTES))
    }

    @Test
    fun `exactly one hour ago becomes hours`() {
        assertEquals(RelativeTime.Hours(1), ago(60, TimeUnit.MINUTES))
    }

    @Test
    fun `three hours ago is three hours`() {
        assertEquals(RelativeTime.Hours(3), ago(3, TimeUnit.HOURS))
    }

    @Test
    fun `twenty three hours ago is still hours`() {
        assertEquals(RelativeTime.Hours(23), ago(23, TimeUnit.HOURS))
    }

    @Test
    fun `exactly one day ago is yesterday`() {
        assertEquals(RelativeTime.Yesterday, ago(24, TimeUnit.HOURS))
    }

    @Test
    fun `two days ago falls back to the date`() {
        val timestamp = now - TimeUnit.DAYS.toMillis(2)
        assertEquals(RelativeTime.Older(timestamp), TimeFormatter.describe(timestamp, now))
    }

    /**
     * A device clock running behind the server's would otherwise produce a negative age and render
     * "לפני -3 דקות". Firestore timestamps come from the server, so this is a real possibility.
     */
    @Test
    fun `a future timestamp is reported as now rather than a negative age`() {
        val future = now + TimeUnit.MINUTES.toMillis(5)
        assertEquals(RelativeTime.Now, TimeFormatter.describe(future, now))
    }
}
