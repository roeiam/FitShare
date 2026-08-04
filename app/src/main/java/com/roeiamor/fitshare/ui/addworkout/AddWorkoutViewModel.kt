package com.roeiamor.fitshare.ui.addworkout

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.data.model.WorkoutDraft
import com.roeiamor.fitshare.data.repository.WorkoutRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import com.roeiamor.fitshare.util.Validators
import kotlinx.coroutines.launch

/**
 * Everything the add-workout screen draws, as one immutable value.
 *
 * @property imageUri the chosen photo, or null. A workout without one is valid.
 * @property titleError message under the title field, or null.
 * @property descriptionError message under the description field, or null.
 * @property durationError message under the duration field, or null.
 * @property selectedCategory the checked category chip.
 * @property selectedDifficulty the checked difficulty chip.
 * @property isSubmitEnabled whether the publish button can be pressed.
 * @property isUploading whether the request is in flight.
 */
data class AddWorkoutUiState(
    val imageUri: Uri? = null,
    @StringRes val titleError: Int? = null,
    @StringRes val descriptionError: Int? = null,
    @StringRes val durationError: Int? = null,
    val selectedCategory: WorkoutCategory = WorkoutCategory.STRENGTH,
    val selectedDifficulty: Difficulty = Difficulty.MEDIUM,
    val isSubmitEnabled: Boolean = false,
    val isUploading: Boolean = false
)

/**
 * Drives publishing a workout: validates the form, then hands a [WorkoutDraft] to the repository.
 *
 * The typed values live here rather than in the views, which is what lets the form survive a failed
 * upload with everything still filled in (SPEC section 11) - and survive rotation, since the
 * ViewModel outlives the Fragment's view.
 *
 * @param workoutRepository uploads the photo and writes the workout with its author's counter.
 */
class AddWorkoutViewModel(private val workoutRepository: WorkoutRepository) : ViewModel() {

    private val _uiState = MutableLiveData(AddWorkoutUiState())

    /** What the screen should currently look like. */
    val uiState: LiveData<AddWorkoutUiState> = _uiState

    private val _navigateToFeed = MutableLiveData<Event<Unit>>()

    /** Fires once after a successful publish. */
    val navigateToFeed: LiveData<Event<Unit>> = _navigateToFeed

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    private var title: String = ""
    private var description: String = ""
    private var durationText: String = ""

    /** Called on every keystroke in the title field. */
    fun onTitleChanged(value: String) {
        title = value
        revalidate()
    }

    /** Called on every keystroke in the description field. */
    fun onDescriptionChanged(value: String) {
        description = value
        revalidate()
    }

    /** Called on every keystroke in the duration field. */
    fun onDurationChanged(value: String) {
        durationText = value
        revalidate()
    }

    /** Called when a category chip is checked. */
    fun onCategorySelected(category: WorkoutCategory) {
        _uiState.value = currentState().copy(selectedCategory = category)
    }

    /** Called when a difficulty chip is checked. */
    fun onDifficultySelected(difficulty: Difficulty) {
        _uiState.value = currentState().copy(selectedDifficulty = difficulty)
    }

    /**
     * Called when the user picks a photo, and with null when they remove it.
     *
     * A cancelled picker never reaches here - the Fragment simply does nothing - so cancelling
     * leaves any previously chosen photo in place, which is what a user expects.
     */
    fun onImageSelected(uri: Uri?) {
        _uiState.value = currentState().copy(imageUri = uri)
    }

    /**
     * Publishes. Ignored while the form is invalid or an upload is already running, so a double tap
     * cannot create the workout twice.
     */
    fun onPublish() {
        val state = currentState()
        if (!state.isSubmitEnabled || state.isUploading) return

        val draft = WorkoutDraft(
            title = title,
            description = description,
            category = state.selectedCategory,
            durationMinutes = durationText.trim().toIntOrNull() ?: return,
            difficulty = state.selectedDifficulty
        )

        viewModelScope.launch {
            _uiState.value = state.copy(isUploading = true, isSubmitEnabled = false)

            workoutRepository.createWorkout(draft, state.imageUri)
                .onSuccess {
                    _message.value = Event(R.string.add_success)
                    _navigateToFeed.value = Event(Unit)
                }
                .onFailure {
                    // The form is left exactly as it was, so nothing the user typed is lost.
                    _message.value = Event(ErrorMapper.toMessageRes(it))
                    revalidate()
                }
        }
    }

    /**
     * Recomputes the three field errors and whether the button is live.
     *
     * As on the auth screens, an error only shows once its field has content, so the form does not
     * open covered in red. The category and difficulty chips always have a selection, so they cannot
     * be invalid and never block the button.
     */
    private fun revalidate() {
        val titleError = Validators.validateWorkoutTitle(title)
        val descriptionError = Validators.validateDescription(description)
        val durationError = Validators.validateDuration(durationText)

        _uiState.value = currentState().copy(
            titleError = titleError.takeIf { title.isNotEmpty() },
            descriptionError = descriptionError.takeIf { description.isNotEmpty() },
            durationError = durationError.takeIf { durationText.isNotEmpty() },
            isSubmitEnabled = titleError == null && descriptionError == null && durationError == null,
            isUploading = false
        )
    }

    private fun currentState(): AddWorkoutUiState = _uiState.value ?: AddWorkoutUiState()
}
