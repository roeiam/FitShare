package com.roeiamor.fitshare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.roeiamor.fitshare.databinding.FragmentLoginBinding
import com.roeiamor.fitshare.ui.common.BaseFragment

/**
 * The sign-in screen, and the app's start destination when no session exists.
 *
 * Phase 2 wires navigation only. Phase 3 adds the email and password fields, Hebrew validation
 * and the real call to AuthRepository.
 */
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.loginSubmit.setOnClickListener {
            // Phase 3 replaces this with a real sign-in. The action already clears the auth flow
            // off the back stack, so Back from the feed exits the app rather than returning here.
            findNavController().navigate(LoginFragmentDirections.actionLoginToFeed())
        }
        binding.goToRegister.setOnClickListener {
            findNavController().navigate(LoginFragmentDirections.actionLoginToRegister())
        }
        binding.goToForgotPassword.setOnClickListener {
            findNavController().navigate(LoginFragmentDirections.actionLoginToForgotPassword())
        }
    }
}
