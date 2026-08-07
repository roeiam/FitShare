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
 * The behaviour being pinned down, in two groups. The banner is **earned**: only a connection this
 * collection watched work and then fail may raise it, so an offline or unknown state that merely
 * opens a collection - which is what a resume looks like - shows nothing. And once it is earned, the
 * older timing rule still holds: a drop shorter than the delay never reaches the user, a real outage
 * reaches them after the delay and no later, and the banner comes down the instant the connection
 * returns.
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
            emit(NetworkStatus.ONLINE)      // the connection this collection watches fail
            emit(NetworkStatus.OFFLINE)
            delay(10_000)
            emit(NetworkStatus.ONLINE)
            delay(5_000)
        }

        statuses.offlineBannerVisibility(delayMillis).collect {
            events += testScheduler.currentTime to it
        }

        // The opening false is the ONLINE the collection needs to see before a loss can count.
        assertEquals(listOf(0L to false, 1_500L to true, 10_000L to false), events)
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

    /**
     * The resume case, and the reason this rule exists.
     *
     * A collection that opens on OFFLINE is not watching anything fail - it is reading whatever the
     * platform happens to say in the first moments after the app comes back, which on the device is
     * not yet trustworthy. However long it lasts, it may not raise the banner: nobody observed a
     * loss. This is what makes returning to the app on a working connection safe under every
     * lifecycle path, because every one of them restarts the collection.
     */
    @Test
    fun `an offline that opens a collection never shows the banner`() = runTest {
        val statuses = flow {
            emit(NetworkStatus.OFFLINE)
            delay(20_000)
        }

        val visibility = statuses.offlineBannerVisibility(delayMillis).toList()

        assertEquals(listOf(false), visibility)
    }

    /** Unknown is not evidence of a working connection either, so it cannot arm the banner. */
    @Test
    fun `an offline seen only after unknown never shows the banner`() = runTest {
        val statuses = flow {
            emit(NetworkStatus.UNKNOWN)
            delay(1_000)
            emit(NetworkStatus.OFFLINE)
            delay(20_000)
        }

        val visibility = statuses.offlineBannerVisibility(delayMillis).toList()

        assertEquals(listOf(false), visibility)
    }

    /**
     * The evidence does not survive a new collection, which is what `repeatOnLifecycle` starts on
     * every resume: a banner earned before the app was backgrounded is not still earned after it
     * comes back. Same flow, collected twice - the second collection has to start from nothing.
     */
    @Test
    fun `evidence is not carried from one collection into the next`() = runTest {
        val firstRun = flow {
            emit(NetworkStatus.ONLINE)
            delay(1_000)
            emit(NetworkStatus.OFFLINE)
            delay(20_000)
        }
        assertEquals(listOf(false, true), firstRun.offlineBannerVisibility(delayMillis).toList())

        // What the app sees on the way back in: still offline, but no loss observed this time.
        val afterResume = flow {
            emit(NetworkStatus.OFFLINE)
            delay(20_000)
        }
        assertEquals(listOf(false), afterResume.offlineBannerVisibility(delayMillis).toList())
    }

    /**
     * Once a connection has been seen, the rule holds for the rest of the collection rather than
     * being spent on the first loss: a second genuine outage still reaches the user.
     */
    @Test
    fun `a second loss in the same collection still shows the banner`() = runTest {
        val events = mutableListOf<Pair<Long, Boolean>>()

        val statuses = flow {
            emit(NetworkStatus.ONLINE)
            emit(NetworkStatus.OFFLINE)
            delay(5_000)
            emit(NetworkStatus.ONLINE)
            delay(5_000)
            emit(NetworkStatus.OFFLINE)
            delay(5_000)
        }

        statuses.offlineBannerVisibility(delayMillis).collect {
            events += testScheduler.currentTime to it
        }

        assertEquals(
            listOf(0L to false, 1_500L to true, 5_000L to false, 11_500L to true),
            events
        )
    }
}
