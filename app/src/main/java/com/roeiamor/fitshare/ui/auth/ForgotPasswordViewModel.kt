package com.roeiamor.fitshare.ui.auth

import androidx.annotation.StringRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import com.roeiamor.fitshare.util.Validators
import kotlinx.coroutines.launch

/**
 * Everything the password reset screen draws, as one immutable value.
 *
 * @property emailError message under the email field, or null.
 * @property isSubmitEnabled whether the send button can be pressed.
 * @property isLoading whether the request is in flight.
 */
data class ForgotPasswordUiState(
    @StringRes val emailError: Int? = null,
    val isSubmitEnabled: Boolean = false,
    val isLoading: Boolean = false
)

/**
 * Drives the password reset screen.
 *
 * Firebase reports success even for an address with no account, so that this screen cannot be used
 * to find out which addresses are registered. The confirmation message is therefore shown on any
 * success and does not claim the address exists.
 *
 * @param authRepository sends the reset email.
 */
class ForgotPasswordViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _uiState = MutableLiveData(ForgotPasswordUiState())

    /** What the screen should currently look like. */
    val uiState: LiveData<ForgotPasswordUiState> = _uiState

    private val _message = MutableLiveData<Event<Int>>()

    /** A Hebrew message to show in a snackbar, fired once. */
    val message: LiveData<Event<Int>> = _message

    private var email: String = ""

    /** Called on every keystroke in the email field. */
    fun onEmailChanged(value: String) {
        email = value
        revalidate()
    }

    /** Sends the reset email. Ignored while invalid or while a request is already running. */
    fun onSubmit() {
        val current = _uiState.value ?: return
        if (!current.isSubmitEnabled || current.isLoading) return

        viewModelScope.launch {
            _uiState.value = current.copy(isLoading = true, isSubmitEnabled = false)

            // The screen stays put on success. Navigating away here would tear down the Fragment
            // before its snackbar could appear, so the user would never see the confirmation.
            authRepository.sendPasswordReset(email)
                .onSuccess { _message.value = Event(R.string.forgot_sent) }
                .onFailure { _message.value = Event(ErrorMapper.toMessageRes(it)) }

            revalidate()
        }
    }

    /** Recomputes the email error and whether the button is live. */
    private fun revalidate() {
        val emailError = Validators.validateEmail(email)

        _uiState.value = ForgotPasswordUiState(
            emailError = emailError.takeIf { email.isNotEmpty() },
            isSubmitEnabled = emailError == null,
            isLoading = false
        )
    }
}
