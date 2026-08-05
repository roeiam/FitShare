package com.roeiamor.fitshare.di

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.roeiamor.fitshare.data.repository.AuthRepository
import com.roeiamor.fitshare.data.repository.InteractionRepository
import com.roeiamor.fitshare.data.repository.UserRepository
import com.roeiamor.fitshare.data.repository.WorkoutRepository
import com.roeiamor.fitshare.ui.addworkout.AddWorkoutViewModel
import com.roeiamor.fitshare.ui.auth.ForgotPasswordViewModel
import com.roeiamor.fitshare.ui.auth.LoginViewModel
import com.roeiamor.fitshare.ui.auth.RegisterViewModel
import com.roeiamor.fitshare.ui.details.WorkoutDetailsViewModel
import com.roeiamor.fitshare.ui.favorites.FavoritesViewModel
import com.roeiamor.fitshare.ui.feed.FeedViewModel
import com.roeiamor.fitshare.ui.profile.EditProfileViewModel
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
 * @param workoutRepository reading and writing workouts.
 * @param interactionRepository likes, comments and favourites.
 * @param userRepository profiles: reading one, and editing your own.
 */
class ViewModelFactory(
    private val authRepository: AuthRepository,
    private val workoutRepository: WorkoutRepository,
    private val interactionRepository: InteractionRepository,
    private val userRepository: UserRepository,
    /**
     * The id of whatever the screen is *about*, which the factory cannot know by itself.
     *
     * A workout id for the details screen and for the add form in edit mode; a user id for a
     * profile. One parameter rather than one per screen, because each ViewModel needs exactly one
     * and the `when` below says plainly which is which.
     *
     * Passed explicitly rather than read from a `SavedStateHandle` so the dependency stays visible
     * in this one file, which is the whole point of a hand-written factory.
     */
    private val screenArgument: String? = null
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
                ProfileViewModel(
                    // Null means "my own profile", which is what the bottom-navigation tab passes.
                    profileUserId = screenArgument,
                    authRepository = authRepository,
                    userRepository = userRepository,
                    interactionRepository = interactionRepository
                )

            modelClass.isAssignableFrom(EditProfileViewModel::class.java) ->
                EditProfileViewModel(userRepository)

            modelClass.isAssignableFrom(FavoritesViewModel::class.java) ->
                FavoritesViewModel(interactionRepository)

            modelClass.isAssignableFrom(FeedViewModel::class.java) ->
                FeedViewModel(workoutRepository)

            modelClass.isAssignableFrom(AddWorkoutViewModel::class.java) ->
                AddWorkoutViewModel(workoutRepository, screenArgument)

            modelClass.isAssignableFrom(WorkoutDetailsViewModel::class.java) ->
                WorkoutDetailsViewModel(
                    workoutId = requireNotNull(screenArgument) {
                        "WorkoutDetailsViewModel needs a workoutId; use " +
                            "ServiceLocator.viewModelFactory(workoutId)"
                    },
                    workoutRepository = workoutRepository,
                    interactionRepository = interactionRepository
                )

            else -> throw IllegalArgumentException(
                "ViewModelFactory has no branch for ${modelClass.name}. Add one."
            )
        }

        @Suppress("UNCHECKED_CAST")
        return viewModel as T
    }
}
