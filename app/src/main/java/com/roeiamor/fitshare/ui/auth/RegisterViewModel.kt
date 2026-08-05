package com.roeiamor.fitshare.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import com.roeiamor.fitshare.util.Validators
import kotlinx.coroutines.launch

/**
 * Everything the registration screen draws, as one immutable value.
 *
 * @property nameError message under the name field, or null.
 * @property emailError message under the email field, or null.
 * @property passwordError message under the password field, or null.
 * @property confirmationError message under the confirm field, or null.
 * @property isSubmitEnabled whether the register button can be pressed.
 * @property isLoading whether the request is in flight.
 */
data class RegisterUiState(
    @param:StringRes val nameError: Int? = null,
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    @param:StringRes val confirmationError: Int? = null,
    val isSubmitEnabled: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Drives the registration screen: validates four fields as they are typed, creates the account,
 * and reports the outcome.
 *
 * @param authRepository creates the Auth account and the users/{uid} document together.
 */
class RegisterViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableLiveData(RegisterUiState())

    /** What the screen should currently look like. */
    val uiState: LiveData<RegisterUiState> = _uiState

    private val _navigateToFeed = MutableLiveData<Event<Unit>>()

    /** Fires once after a successful registration. */
    val navigateToFeed: LiveData<Event<Unit>> = _navigateToFeed

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    private var name: String = ""
    private var email: String = ""
    private var password: String = ""
    private var confirmation: String = ""

    /** Called on every keystroke in the name field. */
    fun onNameChanged(value: String) {
        name = value
        revalidate()
    }

    /** Called on every keystroke in the email field. */
    fun onEmailChanged(value: String) {
        email = value
        revalidate()
    }

    /** Called on every keystroke in the password field. */
    fun onPasswordChanged(value: String) {
        password = value
        revalidate()
    }

    /** Called on every keystroke in the confirm-password field. */
    fun onConfirmationChanged(value: String) {
        confirmation = value
        revalidate()
    }

    /** Creates the account. Ignored while invalid or while a request is already running. */
    fun onSubmit() {
        val current = _uiState.value ?: return
        if (!current.isSubmitEnabled || current.isLoading) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, isSubmitEnabled = false)

            authRepository.register(name, email, password)
                .onSuccess { _navigateToFeed.value = Event(Unit) }
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }

            revalidate()
        }
    }

    /**
     * Recomputes all four errors and whether the button is live.
     *
     * As on the login screen, an error only appears once its field has content. The confirmation is
     * additionally rechecked whenever the password changes, so correcting the password clears a
     * stale "passwords do not match".
     */
    private fun revalidate() {
        val nameError = Validators.validateName(name)
        val emailError = Validators.validateEmail(email)
        val passwordError = Validators.validatePassword(password)
        val confirmationError = Validators.validatePasswordConfirmation(password, confirmation)

        _uiState.value = RegisterUiState(
            nameError = nameError.takeIf { name.isNotEmpty() },
            emailError = emailError.takeIf { email.isNotEmpty() },
            passwordError = passwordError.takeIf { password.isNotEmpty() },
            confirmationError = confirmationError.takeIf { confirmation.isNotEmpty() },
            isSubmitEnabled = nameError == null &&
                emailError == null &&
                passwordError == null &&
                confirmationError == null,
            isLoading = false
        )
    }
}
