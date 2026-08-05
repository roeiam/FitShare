package com.roeiamor.fitshare.ui.details

import androidx.annotation.StringRes
import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.data.model.Workout

/**
 * What the details screen is showing (SPEC section 5's four states).
 *
 * A sealed interface for the same reason as the feed's: the states are mutually exclusive by
 * construction, and the `when` in the Fragment is exhaustive.
 */
sealed interface WorkoutDetailsUiState {

    /** Waiting for the first emission. */
    data object Loading : WorkoutDetailsUiState

    /**
     * The workout, with everything the screen draws.
     *
     * @property isLiked whether the signed-in user has liked it; drives the mark's fill.
     * @property isFavorite whether it is saved.
     * @property isOwner whether the signed-in user wrote it; gates edit and delete.
     * @property comments oldest first.
     * @property currentUserId so the comment list can tell which rows offer deletion.
     */
    data class Content(
        val workout: Workout,
        val isLiked: Boolean,
        val isFavorite: Boolean,
        val isOwner: Boolean,
        val comments: List<Comment>,
        val currentUserId: String?
    ) : WorkoutDetailsUiState

    /**
     * The workout no longer exists.
     *
     * Its own state rather than an error: someone deleting their workout while you are looking at it
     * is a normal thing to happen, not a failure, and "try again" would be the wrong offer.
     *
     * @property isFavorite whether the signed-in user still has this workout saved. Deleting a
     *   workout does not touch anyone else's favourites - it cannot, since the rules only let a user
     *   write their own - so a saved copy outlives the workout and is the only thing left pointing
     *   at it. When that is the case this screen is the one place the user can reach it, so it
     *   offers to remove it rather than leaving a row that opens onto nothing forever.
     */
    data class Deleted(val isFavorite: Boolean) : WorkoutDetailsUiState

    /** The read failed. [messageRes] is already Hebrew. */
    data class Error(@param:StringRes val messageRes: Int) : WorkoutDetailsUiState
}
