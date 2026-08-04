package com.roeiamor.fitshare.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.data.repository.WorkoutRepository
import com.roeiamor.fitshare.ui.auth.ForgotPasswordViewModel
import com.roeiamor.fitshare.ui.feed.FeedViewModel
import com.roeiamor.fitshare.ui.auth.LoginViewModel
import com.roeiamor.fitshare.ui.auth.RegisterViewModel
import com.roeiamor.fitshare.ui.profile.ProfileViewModel

/**
 * Builds every ViewModel in the app, handing each one the repositories it needs.
 *
 * A ViewModel cannot be constructed with arguments by the framework, so something has to do it.
 * With Hilt that something is generated code nobody can read; here it is this `when` block, which
 * lists every ViewModel and its dependencies in one place you can point at.
 *
 * Fragments obtain it through `ServiceLocator.viewModelFactory` and use it with `by viewModels`.
 * Repositories arrive as interfaces, so no ViewModel can reach Firebase directly.
 *
 * @param authRepository accounts and sessions; used by the three auth screens.
 * @param workoutRepository reading workouts; used by the feed.
 */
class ViewModelFactory(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository
) : ViewModelProvider.Factory {

    /**
     * @throws IllegalArgumentException when asked for a ViewModel that is not listed here, which
     *   means someone added one and forgot this file. Failing loudly beats returning null.
     */
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val viewModel: ViewModel = when {
            modelClass.isAssignableFrom(LoginViewModel::class.java) ->
                LoginViewModel(authRepository)

            modelClass.isAssignableFrom(RegisterViewModel::class.java) ->
                RegisterViewModel(authRepository)

            modelClass.isAssignableFrom(ForgotPasswordViewModel::class.java) ->
                ForgotPasswordViewModel(authRepository)

            modelClass.isAssignableFrom(ProfileViewModel::class.java) ->
                ProfileViewModel(authRepository)

            modelClass.isAssignableFrom(FeedViewModel::class.java) ->
                FeedViewModel(workoutRepository)

            else -> throw IllegalArgumentException(
                "ViewModelFactory has no branch for ${modelClass.name}. Add one."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
