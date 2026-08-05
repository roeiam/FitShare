package com.roeiamor.fitshare.ui.profile

import androidx.annotation.StringRes
import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.data.model.Workout

/**
 * What the profile screen is showing, as one value.
 *
 * A sealed interface for the same reason as the feed's: the four states in SPEC section 5 become
 * mutually exclusive by construction, and the `when` in the Fragment is exhaustive.
 */
sealed interface ProfileUiState {

    /** Waiting for the profile document and the workout list. */
    data object Loading : ProfileUiState

    /**
     * The profile, loaded.
     *
     * @property user the profile document.
     * @property workouts everything this user has published, newest first. May be empty - a user
     *   with no workouts still has a name, a bio and an avatar to show, so this is not the empty
     *   state; the grid renders its own "nothing here yet" line instead.
     * @property likesReceived the sum of `likesCount` over [workouts]. Computed here rather than
     *   stored, because the workouts are already loaded for the grid, so it costs no extra reads
     *   (SPEC section 5).
     * @property favoritesCount how many workouts this user has saved, or **null** when that cannot
     *   be known. Another user's favourites are private - the security rules in SPEC section 10
     *   allow `users/{uid}/favorites` only to its owner - so on someone else's profile the third
     *   stat has no value to show and the screen renders a dash.
     * @property isOwnProfile whether edit, logout and the theme toggle are offered.
     */
    data class Content(
        val user: User,
        val workouts: List<Workout>,
        val likesReceived: Long,
        val favoritesCount: Int?,
        val isOwnProfile: Boolean
    ) : ProfileUiState

    /** No profile document for this uid - a deleted or never-created user. */
    data object Empty : ProfileUiState

    /** The read failed. [messageRes] is already mapped to Hebrew by ErrorMapper. */
    data class Error(@param:StringRes val messageRes: Int) : ProfileUiState
}
