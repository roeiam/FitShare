package com.roeiamor.fitshare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.roeiamor.fitshare.databinding.FragmentRegisterBinding
import com.roeiamor.fitshare.ui.common.BaseFragment

/**
 * Account creation.
 *
 * Phase 2 wires navigation only. Phase 3 adds the name, email, password and confirm fields,
 * Hebrew validation messages and the real call to AuthRepository.
 */
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.registerSubmit.setOnClickListener {
            findNavController().navigate(RegisterFragmentDirections.actionRegisterToFeed())
        }
        binding.backToLogin.setOnClickListener {
            findNavController().navigateUp()
        }
    }
}
