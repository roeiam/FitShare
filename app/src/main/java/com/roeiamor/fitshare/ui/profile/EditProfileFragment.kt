package com.roeiamor.fitshare.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import com.roeiamor.fitshare.databinding.FragmentEditProfileBinding
import com.roeiamor.fitshare.ui.common.BaseFragment

/**
 * Edits the signed-in user's name, bio and avatar.
 *
 * Phase 2 provides the destination. Phase 7 adds the fields and the avatar upload.
 */
class EditProfileFragment : BaseFragment<FragmentEditProfileBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentEditProfileBinding.inflate(inflater, container, false)
}
