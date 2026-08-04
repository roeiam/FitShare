package com.roeiamor.fitshare.ui.addworkout

import android.view.LayoutInflater
import android.view.ViewGroup
import com.roeiamor.fitshare.databinding.FragmentAddWorkoutBinding
import com.roeiamor.fitshare.ui.common.BaseFragment

/**
 * Publishes a new workout.
 *
 * Phase 2 provides the destination. Phase 5 adds the image picker - gallery first, then camera -
 * the form fields, compression and the Cloudinary upload.
 */
class AddWorkoutFragment : BaseFragment<FragmentAddWorkoutBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAddWorkoutBinding.inflate(inflater, container, false)
}
