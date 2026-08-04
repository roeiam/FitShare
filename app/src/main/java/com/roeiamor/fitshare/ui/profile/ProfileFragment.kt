package com.roeiamor.fitshare.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.databinding.FragmentProfileBinding
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer

/**
 * One profile screen doing two jobs, as decided in PHASE0_PLAN.md section 4.D.
 *
 * A null userId means "my own profile" - which is what the bottom navigation tab passes - and will
 * show edit, the theme toggle and logout. A real uid means another user's profile, read-only. Two
 * near-identical fragments sharing one layout would have been duplication, a graded defect.
 *
 * Phase 2 renders the empty state. Phase 7 adds the avatar, bio, the three stats and the grid.
 */
class ProfileFragment : BaseFragment<FragmentProfileBinding>() {

    private val args: ProfileFragmentArgs by navArgs()

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

        // Editing is only ever offered on my own profile.
        binding.editProfile.isVisible = isOwnProfile
        binding.editProfile.setOnClickListener {
            findNavController().navigate(ProfileFragmentDirections.actionProfileToEditProfile())
        }

        // The copy differs between the two modes: "you have not posted yet" versus
        // "this user has not posted yet".
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
