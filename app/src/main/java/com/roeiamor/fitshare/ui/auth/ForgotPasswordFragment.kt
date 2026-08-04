package com.roeiamor.fitshare.ui.auth

import android.view.LayoutInflater
import android.view.ViewGroup
import com.roeiamor.fitshare.databinding.FragmentForgotPasswordBinding
import com.roeiamor.fitshare.ui.common.BaseFragment

/**
 * Sends a password reset email.
 *
 * Phase 2 provides the destination so its Back behaviour can be verified. Phase 3 adds the email
 * field and the reset call.
 */
class ForgotPasswordFragment : BaseFragment<FragmentForgotPasswordBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentForgotPasswordBinding.inflate(inflater, container, false)
}
