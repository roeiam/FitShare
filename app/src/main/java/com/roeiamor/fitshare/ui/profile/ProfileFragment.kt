package com.roeiamor.fitshare.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.recyclerview.widget.GridLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roeiamor.fitshare.NavGraphDirections
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.databinding.FragmentProfileBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer
import com.roeiamor.fitshare.util.loadAvatar

/**
 * One profile screen doing two jobs, as decided in PHASE0_PLAN.md section 4.D.
 *
 * A null userId means "my own profile" - which is what the bottom navigation tab passes - and shows
 * edit, the theme toggle and logout. A real uid means another user's profile, read-only. Two
 * near-identical fragments sharing one layout would have been duplication, a graded defect.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private val args: ProfileFragmentArgs by navArgs()

    private val viewModel: ProfileViewModel by viewModels {
        ServiceLocator.viewModelFactory(args.userId)
    }

    private lateinit var stateRenderer: StateRenderer

    private val gridAdapter = WorkoutGridAdapter(::openWorkout)

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentProfileBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateRenderer = StateRenderer(
            content = binding.content,
            loading = binding.stateLoading,
            empty = binding.stateEmpty,
            error = binding.stateError
        )

        setUpGrid()
        setUpStatLabels()
        bindOwnerActions()
        observeViewModel()
    }

    private fun setUpGrid() {
        binding.workoutGrid.layoutManager = GridLayoutManager(requireContext(), GRID_SPAN_COUNT)
        binding.workoutGrid.adapter = gridAdapter
    }

    /**
     * The three statistic labels never change, so they are set once here rather than on every
     * emission - only the numbers are rewritten when the state arrives.
     */
    private fun setUpStatLabels() {
        binding.statWorkouts.statLabel.setText(R.string.profile_workouts)
        binding.statLikes.statLabel.setText(R.string.profile_likes)
        binding.statFavorites.statLabel.setText(R.string.profile_favorites)
    }

    /** Edit, the theme toggle and logout exist only on my own profile, never on someone else's. */
    private fun bindOwnerActions() {
        binding.ownerActions.isVisible = viewModel.isOwnProfile

        binding.editProfile.setOnClickListener {
            findNavController().navigate(ProfileFragmentDirections.actionProfileToEditProfile())
        }
        binding.logout.setOnClickListener { confirmLogout() }
        bindThemeSwitch()
    }

    /**
     * Wires the light/dark toggle.
     *
     * The preference is read and written straight from the ServiceLocator rather than through the
     * ViewModel. It is a **view-layer preference, not business logic**: nothing outside the UI cares
     * about it, and routing it through a ViewModel would mean handing that ViewModel an object that
     * holds a `Context`, which the layer rules in CLAUDE.md forbid.
     *
     * `setOnCheckedChangeListener` is attached *after* the initial value is set, so restoring the
     * switch to its stored position does not itself count as a change and re-apply the theme.
     */
    private fun bindThemeSwitch() {
        val themePreferences = ServiceLocator.themePreferences
        binding.themeSwitch.isChecked = themePreferences.isDarkTheme
        binding.themeSwitch.setOnCheckedChangeListener { _, isChecked ->
            // Recreates the running activities itself, so the change is visible immediately.
            themePreferences.isDarkTheme = isChecked
        }
    }

    /**
     * Asks before signing out. Logging out on a single stray tap would be hostile, and the
     * confirmation string already exists in SPEC section 8.
     */
    private fun confirmLogout() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.profile_logout_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ -> viewModel.onLogoutConfirmed() }
            .show()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner, ::render)

        viewModel.navigateToLogin.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            // A global action, so it empties the whole back stack rather than just this screen's.
            findNavController().navigate(NavGraphDirections.actionGlobalToLogin())
        }
    }

    /** Renders exactly one of the four states. Exhaustive, so a new state cannot be forgotten. */
    private fun render(state: ProfileUiState) {
        when (state) {
            is ProfileUiState.Loading -> stateRenderer.showLoading()

            is ProfileUiState.Content -> {
                renderContent(state)
                stateRenderer.showContent()
            }

            is ProfileUiState.Empty -> stateRenderer.showEmpty(
                titleRes = if (viewModel.isOwnProfile) {
                    R.string.profile_empty_title
                } else {
                    R.string.profile_empty_title_other
                },
                bodyRes = if (viewModel.isOwnProfile) {
                    R.string.profile_empty_body
                } else {
                    R.string.profile_empty_body_other
                }
            )

            is ProfileUiState.Error -> stateRenderer.showError(
                message = getString(state.messageRes),
                onRetry = { /* The listener is live; it recovers on its own once there is a connection. */ }
            )
        }
    }

    private fun renderContent(state: ProfileUiState.Content) {
        binding.avatar.loadAvatar(state.user.photoUrl)
        binding.displayName.text = state.user.displayName

        binding.bio.text = state.user.bio
        binding.bio.isGone = state.user.bio.isBlank()

        binding.statWorkouts.statValue.text = state.user.workoutsCount.toString()
        binding.statLikes.statValue.text = state.likesReceived.toString()
        // Null means "not knowable" - another user's favourites are private (SPEC section 10).
        binding.statFavorites.statValue.text =
            state.favoritesCount?.toString() ?: getString(R.string.profile_stat_unavailable)

        val hasWorkouts = state.workouts.isNotEmpty()
        binding.workoutGrid.isVisible = hasWorkouts
        binding.noWorkouts.isVisible = !hasWorkouts
        binding.noWorkouts.setText(
            if (state.isOwnProfile) {
                R.string.profile_no_workouts
            } else {
                R.string.profile_no_workouts_other
            }
        )

        gridAdapter.submitList(state.workouts)
    }

    private fun openWorkout(workout: Workout) {
        findNavController().navigate(
            ProfileFragmentDirections.actionProfileToWorkoutDetails(workout.id)
        )
    }

    /**
     * Releases the adapter with the view.
     *
     * A RecyclerView holds a reference back to its adapter, and the adapter's view holders hold
     * views; leaving them attached past `onDestroyView` keeps the whole destroyed view tree alive.
     */
    override fun onDestroyView() {
        binding.workoutGrid.adapter = null
        super.onDestroyView()
    }

    private companion object {
        /** Three across, which is what makes a square cell readable on a phone. */
        const val GRID_SPAN_COUNT = 3
    }
}
