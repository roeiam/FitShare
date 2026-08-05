package com.roeiamor.fitshare.ui.common

import android.content.Context
import com.google.firebase.Timestamp
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.util.RelativeTime
import com.roeiamor.fitshare.util.TimeFormatter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Turns a [RelativeTime] into Hebrew text. The only place that happens.
 *
 * Kept apart from [com.roeiamor.fitshare.util.TimeFormatter] so the bucketing stays free of any
 * `Context` and can be unit tested on the JVM.
 */

/** The date format used once something is older than yesterday, per SPEC section 8: `d.M.yy`. */
private const val OLDER_DATE_PATTERN = "d.M.yy"

/**
 * Formats a relative time as Hebrew.
 *
 * Minutes and hours go through `plurals`, not a single `%d` string. Hebrew inflects for one and for
 * two - "לפני דקה", "לפני שעתיים" - so a plain format string would produce "לפני 1 דקות", which
 * reads as broken Hebrew to a native speaker.
 */
fun Context.relativeTimeText(time: RelativeTime): String = when (time) {
    RelativeTime.Now -> getString(R.string.time_now)

    is RelativeTime.Minutes ->
        resources.getQuantityString(R.plurals.time_minutes_ago, time.value, time.value)

    is RelativeTime.Hours ->
        resources.getQuantityString(R.plurals.time_hours_ago, time.value, time.value)

    RelativeTime.Yesterday -> getString(R.string.time_yesterday)

    is RelativeTime.Older ->
        SimpleDateFormat(OLDER_DATE_PATTERN, Locale.getDefault()).format(Date(time.timestampMillis))
}

/**
 * The Hebrew "how long ago" line for anything Firestore stamped with `@ServerTimestamp`.
 *
 * Handles the null that the feed card, the comment row and the details screen all had to handle
 * separately before this existed: between a local write and the server acknowledging it, `createdAt`
 * really is null, so a workout published a second ago would otherwise show a blank timestamp.
 * Treating null as "just now" is both true and the least surprising thing to show.
 *
 * The three call sites used to carry their own copy of exactly this, which is three chances for the
 * "just published" case to be forgotten in one of them.
 */
fun Context.workoutTimeText(createdAt: Timestamp?): String {
    val millis = createdAt?.toDate()?.time ?: return getString(R.string.time_now)
    return relativeTimeText(TimeFormatter.describe(millis, System.currentTimeMillis()))
}
