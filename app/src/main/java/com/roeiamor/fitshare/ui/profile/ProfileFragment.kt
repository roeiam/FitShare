package com.roeiamor.fitshare.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roeiamor.fitshare.NavGraphDirections
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.databinding.FragmentProfileBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer

/**
 * One profile screen doing two jobs, as decided in PHASE0_PLAN.md section 4.D.
 *
 * A null userId means "my own profile" - which is what the bottom navigation tab passes - and shows
 * edit and logout. A real uid means another user's profile, read-only. Two near-identical fragments
 * sharing one layout would have been duplication, a graded defect.
 *
 * Phase 3 adds signing out. Phase 7 adds the avatar, bio, the three stats and the grid.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private val args: ProfileFragmentArgs by navArgs()

    private val viewModel: ProfileViewModel by viewModels { ServiceLocator.viewModelFactory }

    private lateinit var stateRenderer: StateRenderer

    /** True when this screen is showing the signed-in user's own profile. */
    private val isOwnProfile: Boolean get() = args.userId == null

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

        bindOwnerActions()
        observeViewModel()
        renderEmptyState()
    }

    /** Edit and logout exist only on my own profile, never on someone else's. */
    private fun bindOwnerActions() {
        binding.editProfile.isVisible = isOwnProfile
        binding.logout.isVisible = isOwnProfile

        binding.editProfile.setOnClickListener {
            findNavController().navigate(ProfileFragmentDirections.actionProfileToEditProfile())
        }
        binding.logout.setOnClickListener { confirmLogout() }
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
        viewModel.navigateToLogin.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            // A global action, so it empties the whole back stack rather than just this screen's.
            findNavController().navigate(NavGraphDirections.actionGlobalToLogin())
        }
    }

    /** Phase 7 replaces this with the real profile content; the copy differs per mode. */
    private fun renderEmptyState() {
        stateRenderer.showEmpty(
            titleRes = if (isOwnProfile) {
                R.string.profile_empty_title
            } else {
                R.string.profile_empty_title_other
            },
            bodyRes = if (isOwnProfile) {
                R.string.profile_empty_body
            } else {
                R.string.profile_empty_body_other
            }
        )
    }
}
