package com.roeiamor.fitshare.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.ServerTimestamp

/**
 * A saved workout, mirroring `users/{uid}/favorites/{workoutId}` (SPEC section 3).
 *
 * This is a **denormalized snapshot**, not a pointer. It carries enough of the workout to draw a row
 * - title, image, author, category - so the Favorites screen renders from a single query instead of
 * reading the `workouts` collection once per saved item. Twenty favourites would otherwise be
 * twenty-one reads every time the screen opens, on a free tier with a daily read budget.
 *
 * The trade-off is honest and worth being able to state: a snapshot can go stale. If the author
 * renames a workout, the favourites row keeps the old title until it is saved again. For a feed of
 * finished workouts, which nobody edits often, that is a good trade. The details screen always reads
 * the live document, so nothing acts on stale data.
 *
 * @property workoutId the saved workout's id; also the document id.
 * @property savedAt when it was saved, written by the server; the Favorites list is ordered by it.
 */
data class FavoriteWorkout(
    val workoutId: String = "",
    val title: String = "",
    val imageUrl: String? = null,
    val authorName: String = "",
    val category: String = "",
    @ServerTimestamp val savedAt: Timestamp? = null
)
