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
 * Everything the login screen draws, as one immutable value.
 *
 * The errors are string resources rather than text, so this class stays free of any `Context` and
 * the ViewModel never has to resolve a string.
 *
 * @property emailError message under the email field, or null when there is nothing to say.
 * @property passwordError message under the password field, or null.
 * @property isSubmitEnabled whether the login button can be pressed.
 * @property isLoading whether the request is in flight; drives the progress indicator.
 */
data class LoginUiState(
    @param:StringRes val emailError: Int? = null,
    @param:StringRes val passwordError: Int? = null,
    val isSubmitEnabled: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Drives the login screen: validates as the user types, signs in, and reports the outcome.
 *
 * It never touches a `View`, a `Context` or a `Fragment` - it publishes state and one-shot events,
 * and the Fragment renders them.
 *
 * @param authRepository the interface; this class has no idea Firebase is behind it.
 */
class LoginViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableLiveData(LoginUiState())

    /** What the screen should currently look like. */
    val uiState: LiveData<LoginUiState> = _uiState

    private val _navigateToFeed = MutableLiveData<Event<Unit>>()

    /** Fires once after a successful sign-in. */
    val navigateToFeed: LiveData<Event<Unit>> = _navigateToFeed

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    private var email: String = ""
    private var password: String = ""

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

    /**
     * Signs in. Does nothing when the form is invalid or a request is already running, so a
     * double tap cannot start two sign-ins.
     */
    fun onSubmit() {
        val current = _uiState.value ?: return
        if (!current.isSubmitEnabled || current.isLoading) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, isSubmitEnabled = false)

            authRepository.login(email, password)
                .onSuccess { _navigateToFeed.value = Event(Unit) }
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }

            revalidate()
        }
    }

    /**
     * Recomputes errors and whether the button is live.
     *
     * An error is only shown once the field has something in it, so the screen does not greet the
     * user with two red "required field" messages before they have typed anything.
     */
    private fun revalidate() {
        val emailError = Validators.validateEmail(email)
        val passwordError = Validators.validatePassword(password)

        _uiState.value = LoginUiState(
            emailError = emailError.takeIf { email.isNotEmpty() },
            passwordError = passwordError.takeIf { password.isNotEmpty() },
            isSubmitEnabled = emailError == null && passwordError == null,
            isLoading = false
        )
    }
}
