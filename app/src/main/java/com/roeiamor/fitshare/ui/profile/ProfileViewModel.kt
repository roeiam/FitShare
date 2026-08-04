package com.roeiamor.fitshare.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.util.Event

/**
 * Drives the profile screen.
 *
 * Phase 3 gives it only what session handling needs - signing out. Phase 7 adds the profile
 * document, the three statistics and the workout grid.
 *
 * It exists now rather than later because a Fragment must never call a repository directly, and
 * logging out is repository work.
 *
 * @param authRepository ends the session.
 */
class ProfileViewModel(private val authRepository: AuthRepository) : ViewModel() {

    private val _navigateToLogin = MutableLiveData<Event<Unit>>()

    /** Fires once after signing out, to send the user back to the login screen. */
    val navigateToLogin: LiveData<Event<Unit>> = _navigateToLogin

    /**
     * Ends the session. Called only after the user confirms in the dialog, because signing out is
     * not something to do on a stray tap.
     */
    fun onLogoutConfirmed() {
        authRepository.logout()
        _navigateToLogin.value = Event(Unit)
    }
}
