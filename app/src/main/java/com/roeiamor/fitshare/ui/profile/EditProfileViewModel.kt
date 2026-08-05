package com.roeiamor.fitshare.ui.profile

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.data.repository.UserRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import com.roeiamor.fitshare.util.Validators
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Everything the edit-profile screen draws, as one immutable value.
 *
 * @property avatarUri a newly picked photo, or null while the existing one still applies.
 * @property existingPhotoUrl the avatar the profile already has; shown until a new one is picked.
 * @property nameError message under the name field, or null.
 * @property isSaveEnabled whether the save button can be pressed.
 * @property isSaving whether the request is in flight.
 */
data class EditProfileUiState(
    val avatarUri: Uri? = null,
    val existingPhotoUrl: String? = null,
    @StringRes val nameError: Int? = null,
    val isSaveEnabled: Boolean = false,
    val isSaving: Boolean = false
)

/**
 * Drives editing the signed-in user's name, bio and avatar.
 *
 * The typed values live here rather than in the views, which is what lets the form survive rotation
 * and a failed save with everything still filled in.
 *
 * @param userRepository loads the current profile and saves the edit.
 */
class EditProfileViewModel(private val userRepository: UserRepository) : ViewModel() {

    private val _uiState = MutableLiveData(EditProfileUiState())

    /** What the screen should currently look like. */
    val uiState: LiveData<EditProfileUiState> = _uiState

    private val _prefill = MutableLiveData<Event<User>>()

    /**
     * Fires once when the profile has loaded, so the Fragment can fill the fields.
     *
     * A one-shot event rather than state, for the same reason as on the add-workout form: writing
     * into an `EditText` calls back into [onNameChanged], so re-delivering it on rotation would
     * overwrite whatever the user had since typed.
     */
    val prefill: LiveData<Event<User>> = _prefill

    private val _saved = MutableLiveData<Event<Unit>>()

    /** Fires once after a successful save, to go back to the profile. */
    val saved: LiveData<Event<Unit>> = _saved

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    private var name: String = ""
    private var bio: String = ""

    /**
     * Loads the profile to prefill the form.
     *
     * **Below the properties on purpose.** Kotlin runs initialisers in declaration order, so an
     * `init` placed above `_uiState` would run while that field is still null - which is exactly the
     * NullPointerException that shipped briefly in the add-workout screen.
     */
    init {
        loadCurrentProfile()
    }

    /**
     * Takes the first emission of the profile stream and stops listening.
     *
     * A form is a snapshot: it should be filled from the profile as it was when the screen opened.
     * Staying subscribed would mean a change arriving from another device rewrote the fields under
     * the user's fingers mid-edit.
     */
    private fun loadCurrentProfile() {
        val userId = userRepository.currentUserId ?: run {
            _message.value = Event(R.string.error_generic)
            return
        }

        viewModelScope.launch {
            _uiState.value = currentState().copy(isSaving = true)

            userRepository.observeUser(userId).first()
                .onSuccess { user ->
                    if (user == null) {
                        _message.value = Event(R.string.error_generic)
                        _uiState.value = currentState().copy(isSaving = false)
                        return@onSuccess
                    }
                    name = user.displayName
                    bio = user.bio
                    _uiState.value = currentState().copy(existingPhotoUrl = user.photoUrl)
                    _prefill.value = Event(user)
                    revalidate()
                }
                .onFailure {
                    _message.value = Event(ErrorMapper.toMessageRes(it))
                    revalidate()
                }
        }
    }

    /** Called on every keystroke in the name field. */
    fun onNameChanged(value: String) {
        name = value
        revalidate()
    }

    /** Called on every keystroke in the bio field. */
    fun onBioChanged(value: String) {
        bio = value
        revalidate()
    }

    /**
     * Called when the user picks an avatar, and with null when they remove it.
     *
     * A cancelled picker never reaches here - the Fragment simply does nothing - so cancelling
     * leaves any previously chosen photo in place.
     */
    fun onAvatarSelected(uri: Uri?) {
        _uiState.value = currentState().copy(avatarUri = uri)
    }

    /**
     * Saves. Ignored while the form is invalid or a save is already running, so a double tap cannot
     * upload the avatar twice.
     */
    fun onSave() {
        val state = currentState()
        if (!state.isSaveEnabled || state.isSaving) return

        viewModelScope.launch {
            _uiState.value = state.copy(isSaving = true, isSaveEnabled = false)

            userRepository.updateProfile(name, bio, state.avatarUri)
                .onSuccess {
                    _message.value = Event(R.string.edit_profile_saved)
                    _saved.value = Event(Unit)
                }
                .onFailure {
                    // The form is left exactly as it was, so nothing the user typed is lost.
                    _message.value = Event(ErrorMapper.toMessageRes(it))
                    revalidate()
                }
        }
    }

    /**
     * Recomputes the name error and whether the button is live.
     *
     * The bio has no validation: it is free text and may be empty. As elsewhere, an error only shows
     * once the field has content, so the form does not open covered in red.
     */
    private fun revalidate() {
        val nameError = Validators.validateName(name)

        _uiState.value = currentState().copy(
            nameError = nameError.takeIf { name.isNotEmpty() },
            isSaveEnabled = nameError == null,
            isSaving = false
        )
    }

    private fun currentState(): EditProfileUiState = _uiState.value ?: EditProfileUiState()
}
