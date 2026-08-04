package com.roeiamor.fitshare.ui.feed

import androidx.annotation.StringRes
import com.roeiamor.fitshare.data.model.Workout

/**
 * What the feed screen is showing, as one value.
 *
 * Modelled as a sealed interface rather than a flag-carrying data class so the four states required
 * by SPEC section 5 are mutually exclusive *by construction*. A data class with `isLoading`,
 * `isEmpty` and `error` fields allows nonsense combinations - loading and error at once - and the
 * Fragment then has to decide which wins. Here it cannot happen, and the `when` in the Fragment is
 * exhaustive, so adding a state later is a compile error rather than a blank screen.
 */
sealed interface FeedUiState {

    /** Waiting for the first emission from Firestore. */
    data object Loading : FeedUiState

    /** Workouts to show. Never empty - that case is [Empty]. */
    data class Content(val workouts: List<Workout>) : FeedUiState

    /**
     * Nothing to show.
     *
     * @property isFiltered true when the feed itself has workouts but the current category or
     *   search matched none. The copy differs: "be the first to post" is wrong advice when the
     *   real problem is that the user typed a word that matches nothing.
     */
    data class Empty(val isFiltered: Boolean) : FeedUiState

    /** The read failed. [messageRes] is already mapped to Hebrew by ErrorMapper. */
    data class Error(@StringRes val messageRes: Int) : FeedUiState
}
