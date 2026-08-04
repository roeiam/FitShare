package com.roeiamor.fitshare.ui.details

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.navigation.fragment.navArgs
import com.roeiamor.fitshare.databinding.FragmentWorkoutDetailsBinding
import com.roeiamor.fitshare.ui.common.BaseFragment

/**
 * A single workout in full, with its comments.
 *
 * Phase 2 provides the destination and reads its argument. Phase 6 adds the hero image, the author
 * row, like, favorite, share, comments, and the owner's edit and delete actions.
 */
class WorkoutDetailsFragment : BaseFragment<FragmentWorkoutDetailsBinding>() {

    /** Which workout to show. Supplied by SafeArgs, so it cannot arrive as a missing bundle key. */
    private val args: WorkoutDetailsFragmentArgs by navArgs()

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWorkoutDetailsBinding.inflate(inflater, container, false)
}
