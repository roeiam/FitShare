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
    /** The photo the workout already has, when editing. Shown until a new one is picked. */
    val existingImageUrl: String? = null,
    @param:StringRes val titleError: Int? = null,
    @param:StringRes val descriptionError: Int? = null,
    @param:StringRes val durationError: Int? = null,
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
class AddWorkoutViewModel(
    private val workoutRepository: WorkoutRepository,
    private val editingWorkoutId: String?
) : ViewModel() {

    /** True when this screen is editing an existing workout rather than publishing a new one. */
    val isEditing: Boolean get() = editingWorkoutId != null

    private val _prefill = MutableLiveData<Event<WorkoutDraft>>()

    /**
     * Fires once when an existing workout has loaded, so the Fragment can fill the fields.
     *
     * A one-shot event rather than state: the Fragment writes the values into `EditText`s, which
     * calls back into `onTitleChanged` and friends. Re-delivering it - on rotation, say - would
     * overwrite whatever the user had since typed with the original values.
     */
    val prefill: LiveData<Event<WorkoutDraft>> = _prefill

    /** Loads the workout being edited and asks the Fragment to fill the form with it. */
    private fun loadExistingWorkout(workoutId: String) {
        viewModelScope.launch {
            _uiState.value = currentState().copy(isUploading = true)

            workoutRepository.getWorkout(workoutId)
                .onSuccess { workout ->
                    if (workout == null) {
                        _message.value = Event(R.string.error_generic)
                        return@onSuccess
                    }
                    title = workout.title
                    description = workout.description
                    durationText = workout.durationMinutes.toString()

                    val draft = WorkoutDraft(
                        title = workout.title,
                        description = workout.description,
                        category = WorkoutCategory.fromName(workout.category),
                        durationMinutes = workout.durationMinutes,
                        difficulty = Difficulty.fromName(workout.difficulty)
                    )
                    _uiState.value = currentState().copy(
                        selectedCategory = draft.category,
                        selectedDifficulty = draft.difficulty,
                        existingImageUrl = workout.imageUrl
                    )
                    _prefill.value = Event(draft)
                    revalidate()
                }
                .onFailure {
                    _message.value = Event(ErrorMapper.toMessageRes(it))
                    revalidate()
                }
        }
    }

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

    /**
     * Loads the workout when this screen opens in edit mode.
     *
     * **This block must stay below every property it touches.** Kotlin runs initialisers and `init`
     * blocks in declaration order, so an `init` placed above `_uiState` runs while that field is
     * still null - which crashed the app with a NullPointerException the moment the edit button was
     * pressed. It compiled cleanly and only ever failed in edit mode, so it took running the screen
     * on a device to find.
     */
    init {
        if (editingWorkoutId != null) loadExistingWorkout(editingWorkoutId)
    }

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

            // Editing updates only the fields this form owns, so the counters, the author and
            // createdAt survive. Publishing writes a new document and moves workoutsCount.
            val result = if (editingWorkoutId == null) {
                workoutRepository.createWorkout(draft, state.imageUri)
            } else {
                workoutRepository.updateWorkout(editingWorkoutId, draft, state.imageUri)
            }

            result
                .onSuccess {
                    _message.value = Event(
                        if (isEditing) R.string.details_edit_saved else R.string.add_success
                    )
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
