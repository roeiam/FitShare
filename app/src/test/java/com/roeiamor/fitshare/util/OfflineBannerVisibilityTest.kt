package com.roeiamor.fitshare.util

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The offline banner's showing rule, tested against **virtual** time.
 *
 * This exists because the behaviour under test is a race with the platform, and a real device cannot
 * be made to lose its connection for exactly 0.7 seconds on demand. `runTest` fast-forwards
 * `delay`, so a drop of any chosen length can be described exactly and the result is deterministic.
 *
 * The behaviour being pinned down: a connection that drops and returns faster than the delay must
 * never reach the user, a real outage must reach them after the delay and no later, and the banner
 * must come down the instant the connection does return.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class OfflineBannerVisibilityTest {

    private val delayMillis = 1_500L

    /**
     * The reported bug, in one test: Android reports a network as available shortly before it
     * reports it as validated, so the app sees a sub-second "offline" while the connection is fine.
     * Measured on a device at 0.69s and 1.05s. Neither may raise the banner.
     */
    @Test
    fun `a drop shorter than the delay never shows the banner`() = runTest {
        val statuses = flow {
            emit(NetworkStatus.ONLINE)
            delay(1_000)
            emit(NetworkStatus.OFFLINE)
            delay(1_050)          // the longest false window measured on the device
            emit(NetworkStatus.ONLINE)
            delay(5_000)
        }

        val visibility = statuses.offlineBannerVisibility(delayMillis).toList()

        assertEquals(listOf(false), visibility)
    }

    /** A real outage still reaches the user, and after the delay rather than at some later point. */
    @Test
    fun `a drop longer than the delay shows the banner, once the delay has passed`() = runTest {
        val shownAt = mutableListOf<Long>()

        val statuses = flow {
            emit(NetworkStatus.ONLINE)
            delay(1_000)
            emit(NetworkStatus.OFFLINE)
            delay(20_000)
        }

        statuses.offlineBannerVisibility(delayMillis).collect { visible ->
            if (visible) shownAt += testScheduler.currentTime
        }

        // 1_000 for the emission plus the 1_500 the rule waits before believing it.
        assertEquals(listOf(2_500L), shownAt)
    }

    /** Hiding is not debounced: the banner comes down on the same tick the connection returns. */
    @Test
    fun `the banner hides immediately when the connection comes back`() = runTest {
        val events = mutableListOf<Pair<Long, Boolean>>()

        val statuses = flow {
            emit(NetworkStatus.OFFLINE)
            delay(10_000)
            emit(NetworkStatus.ONLINE)
            delay(5_000)
        }

        statuses.offlineBannerVisibility(delayMillis).collect {
            events += testScheduler.currentTime to it
        }

        assertEquals(listOf(1_500L to true, 10_000L to false), events)
    }

    /**
     * UNKNOWN means "nothing has been reported yet", which is what the flow carries before the
     * platform answers. It is not offline and must never raise the banner.
     */
    @Test
    fun `unknown never shows the banner`() = runTest {
        val statuses = flow {
            emit(NetworkStatus.UNKNOWN)
            delay(20_000)
        }

        val visibility = statuses.offlineBannerVisibility(delayMillis).toList()

        assertEquals(listOf(false), visibility)
    }
}
