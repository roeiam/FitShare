package com.roeiamor.fitshare.ui.profile

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.data.repository.InteractionRepository
import com.roeiamor.fitshare.data.repository.UserRepository
import com.roeiamor.fitshare.util.ErrorMapper
import com.roeiamor.fitshare.util.Event
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * Drives the profile screen, for both of the jobs it does.
 *
 * [profileUserId] decides which: null means the signed-in user's own profile, a real uid means
 * someone else's. Everything downstream reads from [targetUserId], so the two cases differ in
 * exactly one place rather than throughout the class.
 *
 * Three live sources are combined here - the profile document, the user's workouts and (own profile
 * only) the favourites list - and the chain ends with `.asLiveData()`, so the Fragment only ever
 * sees `LiveData` (decision PHASE0_PLAN.md section 4.G).
 *
 * @param profileUserId whose profile to show; null for my own.
 * @param authRepository ends the session.
 * @param userRepository the profile document and the workout grid.
 * @param interactionRepository the favourites list, for the third statistic.
 */
class ProfileViewModel(
    private val profileUserId: String?,
    private val authRepository: AuthRepository,
    private val userRepository: UserRepository,
    private val interactionRepository: InteractionRepository
) : ViewModel() {

    /** True when this screen is showing the signed-in user's own profile. */
    val isOwnProfile: Boolean = profileUserId == null || profileUserId == authRepository.currentUserId

    /** The uid actually being displayed, or null when nobody is signed in. */
    private val targetUserId: String? = profileUserId ?: authRepository.currentUserId

    private val _navigateToLogin = MutableLiveData<Event<Unit>>()

    /** Fires once after signing out, to send the user back to the login screen. */
    val navigateToLogin: LiveData<Event<Unit>> = _navigateToLogin

    /**
     * What the screen should currently look like.
     *
     * The favourites flow is chosen up front rather than inside the combine: another user's
     * favourites are unreadable by design, so asking for them would produce a permission error and
     * put the whole screen into its error state over one statistic.
     */
    val uiState: LiveData<ProfileUiState> = buildUiState()

    private fun buildUiState(): LiveData<ProfileUiState> {
        val userId = targetUserId
            ?: return MutableLiveData(ProfileUiState.Error(R.string.error_generic))

        val favoritesCounts = if (isOwnProfile) {
            interactionRepository.observeFavorites().map { result -> result.getOrNull()?.size }
        } else {
            flowOf(null)
        }

        return combine(
            userRepository.observeUser(userId),
            userRepository.observeUserWorkouts(userId),
            favoritesCounts
        ) { userResult, workoutsResult, favoritesCount ->
            toUiState(userResult, workoutsResult, favoritesCount)
        }
            .onStart { emit(ProfileUiState.Loading) }
            .asLiveData()
    }

    /**
     * Folds the three sources into one state.
     *
     * A failure on **either** read is an error, but a missing profile document is [ProfileUiState.Empty]
     * rather than an error: a uid with no document is a normal outcome - a deleted account whose
     * name is still denormalized onto old workouts - and showing "user not found" is more honest
     * than "something went wrong".
     */
    private fun toUiState(
        userResult: Result<User?>,
        workoutsResult: Result<List<Workout>>,
        favoritesCount: Int?
    ): ProfileUiState {
        val user = userResult.fold(
            onSuccess = { it },
            onFailure = { return ProfileUiState.Error(ErrorMapper.toMessageRes(it)) }
        ) ?: return ProfileUiState.Empty

        val workouts = workoutsResult.getOrElse {
            return ProfileUiState.Error(ErrorMapper.toMessageRes(it))
        }

        return ProfileUiState.Content(
            user = user,
            workouts = workouts,
            likesReceived = workouts.sumOf { it.likesCount },
            favoritesCount = favoritesCount,
            isOwnProfile = isOwnProfile
        )
    }

    /**
     * Ends the session. Called only after the user confirms in the dialog, because signing out is
     * not something to do on a stray tap.
     */
    fun onLogoutConfirmed() {
        authRepository.logout()
        _navigateToLogin.value = Event(Unit)
    }
}
