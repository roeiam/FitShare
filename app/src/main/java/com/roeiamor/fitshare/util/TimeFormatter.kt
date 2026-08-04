package com.roeiamor.fitshare.util

import java.util.concurrent.TimeUnit

/**
 * How long ago something happened, as a value rather than as text.
 *
 * Keeping this Android-free is what makes [TimeFormatter] unit testable on the JVM. The UI layer
 * turns one of these into a Hebrew string in `ui/common/TimeLabels.kt`, which is the only place
 * that needs a `Context`.
 */
sealed interface RelativeTime {

    /** Less than a minute ago. */
    data object Now : RelativeTime

    /** Under an hour ago. */
    data class Minutes(val value: Int) : RelativeTime

    /** Under a day ago. */
    data class Hours(val value: Int) : RelativeTime

    /** Yesterday. */
    data object Yesterday : RelativeTime

    /** Longer ago than that; the UI shows the date itself. */
    data class Older(val timestampMillis: Long) : RelativeTime
}

/**
 * Buckets a timestamp into the relative-time steps SPEC section 8 asks for:
 * כרגע · לפני 5 דקות · לפני 3 שעות · אתמול · then the date.
 */
object TimeFormatter {

    /**
     * Describes how long before [nowMillis] the moment [timestampMillis] was.
     *
     * @param nowMillis injectable so tests are deterministic instead of racing the wall clock.
     * @return [RelativeTime.Now] for anything under a minute, and for anything in the future -
     *   a device whose clock is behind the server's would otherwise produce a negative age and
     *   render "לפני -3 דקות".
     */
    fun describe(timestampMillis: Long, nowMillis: Long): RelativeTime {
        val elapsedMillis = nowMillis - timestampMillis
        if (elapsedMillis < 0) return RelativeTime.Now

        val minutes = TimeUnit.MILLISECONDS.toMinutes(elapsedMillis)
        val hours = TimeUnit.MILLISECONDS.toHours(elapsedMillis)
        val days = TimeUnit.MILLISECONDS.toDays(elapsedMillis)

        return when {
            minutes < 1 -> RelativeTime.Now
            hours < 1 -> RelativeTime.Minutes(minutes.toInt())
            days < 1 -> RelativeTime.Hours(hours.toInt())
            days < 2 -> RelativeTime.Yesterday
            else -> RelativeTime.Older(timestampMillis)
        }
    }
}
