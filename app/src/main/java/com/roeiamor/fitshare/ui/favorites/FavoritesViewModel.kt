package com.roeiamor.fitshare.ui.favorites

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.roeiamor.fitshare.data.model.FavoriteWorkout
import com.roeiamor.fitshare.data.repository.InteractionRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * What the favourites screen is showing, as one value.
 *
 * A sealed interface for the same reason as the feed's: the four states in SPEC section 5 become
 * mutually exclusive by construction.
 */
sealed interface FavoritesUiState {

    /** Waiting for the first emission. */
    data object Loading : FavoritesUiState

    /** Saved workouts to show. Never empty - that case is [Empty]. */
    data class Content(val favorites: List<FavoriteWorkout>) : FavoritesUiState

    /** Nothing saved yet. */
    data object Empty : FavoritesUiState

    /** The read failed. [messageRes] is already mapped to Hebrew by ErrorMapper. */
    data class Error(@StringRes val messageRes: Int) : FavoritesUiState
}

/**
 * Drives the favourites screen.
 *
 * The whole screen is **one query**, because a favourite is stored as a denormalized snapshot rather
 * than a pointer (SPEC section 3) - so there is nothing to combine here and no per-item read.
 *
 * @param interactionRepository owns favourites, as the place likes and comments already live.
 */
class FavoritesViewModel(
    private val interactionRepository: InteractionRepository
) : ViewModel() {

    /** What the screen should currently look like. */
    val uiState: LiveData<FavoritesUiState> = interactionRepository.observeFavorites()
        .map { result ->
            result.fold(
                onSuccess = { favorites ->
                    if (favorites.isEmpty()) {
                        FavoritesUiState.Empty
                    } else {
                        FavoritesUiState.Content(favorites)
                    }
                },
                onFailure = { FavoritesUiState.Error(ErrorMapper.toMessageRes(it)) }
            )
        }
        .onStart { emit(FavoritesUiState.Loading) }
        .asLiveData()

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    /**
     * Removes a saved workout.
     *
     * Nothing is done to the list here: the removal is a Firestore write, the screen is already
     * listening to that collection, and the new list arrives on its own. Removing it locally as well
     * would be the same change applied twice, and the two copies could disagree if the write failed.
     */
    fun onRemoveConfirmed(workoutId: String) {
        viewModelScope.launch {
            interactionRepository.removeFavorite(workoutId)
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }
        }
    }
}
