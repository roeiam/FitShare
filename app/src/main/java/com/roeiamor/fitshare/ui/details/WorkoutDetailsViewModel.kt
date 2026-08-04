package com.roeiamor.fitshare.ui.details

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.repository.InteractionRepository
import com.roeiamor.fitshare.data.repository.WorkoutRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import com.roeiamor.fitshare.util.Validators
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.launch

/**
 * Drives the details screen.
 *
 * Four live streams are combined here - the workout, whether it is liked, whether it is saved, and
 * its comments - and the chain ends with `asLiveData()`, so the Fragment sees one `LiveData` of one
 * state (decision PHASE0_PLAN.md section 4.G).
 *
 * Because all four are Firestore listeners, the screen is **self-correcting**: a like from another
 * device, a comment from another account, or the owner deleting the workout all arrive here without
 * anything being refreshed. It is also why nothing on this screen keeps its own copy of a counter -
 * the only source of truth is what the listener last said.
 *
 * @param workoutId which workout; supplied by SafeArgs so it cannot arrive missing.
 * @param workoutRepository the workout itself, and the owner's edit and delete.
 * @param interactionRepository likes, favourites and comments.
 */
class WorkoutDetailsViewModel(
    private val workoutId: String,
    private val workoutRepository: WorkoutRepository,
    private val interactionRepository: InteractionRepository
) : ViewModel() {

    /**
     * The whole screen, as one state.
     *
     * `combine` waits for all four flows to emit once, so the screen never renders half-populated -
     * a card with the right title but a like state that has not arrived yet would flash the wrong
     * heart.
     */
    val uiState: LiveData<WorkoutDetailsUiState> = combine(
        workoutRepository.observeWorkout(workoutId),
        interactionRepository.observeIsLiked(workoutId),
        interactionRepository.observeIsFavorite(workoutId),
        interactionRepository.observeComments(workoutId)
    ) { workoutResult, likedResult, favoriteResult, commentsResult ->
        toUiState(workoutResult, likedResult, favoriteResult, commentsResult)
    }
        .onStart { emit(WorkoutDetailsUiState.Loading) }
        .asLiveData()

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    private val _navigateBack = MutableLiveData<Event<Unit>>()

    /** Fires once after the owner deletes this workout. */
    val navigateBack: LiveData<Event<Unit>> = _navigateBack

    /** The comment currently being typed, kept here so it survives rotation. */
    private var commentText: String = ""

    private val _isCommentValid = MutableLiveData(false)

    /** Whether the send button is live. */
    val isCommentValid: LiveData<Boolean> = _isCommentValid

    /**
     * Likes or unlikes.
     *
     * Nothing is written to the state here. The transaction updates Firestore, the listener reports
     * the new truth, and the button re-renders from that. Optimistically flipping the heart first
     * would mean showing a like that might not have happened - and would fight the listener when it
     * disagreed.
     */
    fun onLikeClicked() {
        viewModelScope.launch {
            interactionRepository.toggleLike(workoutId)
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }
        }
    }

    /** Saves or unsaves, and confirms which happened. */
    fun onFavoriteClicked() {
        val workout = currentWorkout() ?: return

        viewModelScope.launch {
            interactionRepository.toggleFavorite(workout)
                .onSuccess { saved ->
                    _message.value = Event(
                        if (saved) R.string.details_saved else R.string.details_unsaved
                    )
                }
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }
        }
    }

    /** Called on every keystroke in the comment field. */
    fun onCommentTextChanged(text: String) {
        commentText = text
        _isCommentValid.value = Validators.validateComment(text) == null
    }

    /** Posts the comment. Ignored when it is empty or too long. */
    fun onSendComment() {
        val error = Validators.validateComment(commentText)
        if (error != null) {
            _message.value = Event(error)
            return
        }

        val text = commentText
        viewModelScope.launch {
            interactionRepository.addComment(workoutId, text)
                .onSuccess {
                    commentText = ""
                    _isCommentValid.value = false
                    _clearCommentField.value = Event(Unit)
                }
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }
        }
    }

    private val _clearCommentField = MutableLiveData<Event<Unit>>()

    /** Fires once after a comment is posted, so the Fragment can empty the input. */
    val clearCommentField: LiveData<Event<Unit>> = _clearCommentField

    /** Deletes one of the current user's own comments. Called after the confirmation dialog. */
    fun onDeleteCommentConfirmed(comment: Comment) {
        viewModelScope.launch {
            interactionRepository.deleteComment(workoutId, comment.id)
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }
        }
    }

    /** Deletes the workout. Called after the confirmation dialog, and only offered to the owner. */
    fun onDeleteWorkoutConfirmed() {
        val workout = currentWorkout() ?: return

        viewModelScope.launch {
            workoutRepository.deleteWorkout(workout)
                .onSuccess { _navigateBack.value = Event(Unit) }
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }
        }
    }

    /** The workout as last seen, or null while loading or after it was deleted. */
    fun currentWorkout(): Workout? =
        (uiState.value as? WorkoutDetailsUiState.Content)?.workout

    /** Folds the four streams into one state, failing as a whole if any of them failed. */
    private fun toUiState(
        workoutResult: Result<Workout?>,
        likedResult: Result<Boolean>,
        favoriteResult: Result<Boolean>,
        commentsResult: Result<List<Comment>>
    ): WorkoutDetailsUiState {
        val failure = listOf(workoutResult, likedResult, favoriteResult, commentsResult)
            .firstOrNull { it.isFailure }
        if (failure != null) {
            return WorkoutDetailsUiState.Error(ErrorMapper.toMessageRes(failure.exceptionOrNull()))
        }

        val workout = workoutResult.getOrNull() ?: return WorkoutDetailsUiState.Deleted
        val currentUserId = interactionRepository.currentUserId

        return WorkoutDetailsUiState.Content(
            workout = workout,
            isLiked = likedResult.getOrDefault(false),
            isFavorite = favoriteResult.getOrDefault(false),
            isOwner = currentUserId != null && currentUserId == workout.authorId,
            comments = commentsResult.getOrDefault(emptyList()),
            currentUserId = currentUserId
        )
    }
}
