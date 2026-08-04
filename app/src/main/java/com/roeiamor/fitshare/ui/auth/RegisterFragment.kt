package com.roeiamor.fitshare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.roeiamor.fitshare.databinding.FragmentRegisterBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.util.onImeDone
import com.roeiamor.fitshare.util.setErrorRes
import com.roeiamor.fitshare.util.showSnackbar

/**
 * Account creation: four validated fields, then one call that creates the Auth account and the
 * `users/{uid}` document together.
 */
class RegisterFragment : BaseFragment<FragmentRegisterBinding>() {

    private val viewModel: RegisterViewModel by viewModels { ServiceLocator.viewModelFactory }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentRegisterBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindInputs()
        observeViewModel()
    }

    /** Sends every keystroke and tap to the ViewModel. */
    private fun bindInputs() {
        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged {
            viewModel.onPasswordChanged(it?.toString().orEmpty())
        }
        binding.confirmationInput.doAfterTextChanged {
            viewModel.onConfirmationChanged(it?.toString().orEmpty())
        }

        binding.confirmationInput.onImeDone { viewModel.onSubmit() }

        binding.registerSubmit.setOnClickListener { viewModel.onSubmit() }
        binding.backToLogin.setOnClickListener { findNavController().navigateUp() }
    }

    /** Observes state and one-shot events, always with `viewLifecycleOwner`. */
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { render(it) }

        viewModel.navigateToFeed.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            findNavController().navigate(RegisterFragmentDirections.actionRegisterToFeed())
        }

        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { showSnackbar(it) }
        }
    }

    /** Draws one state: four inline errors, button availability, and the progress indicator. */
    private fun render(state: RegisterUiState) {
        binding.nameLayout.setErrorRes(state.nameError)
        binding.emailLayout.setErrorRes(state.emailError)
        binding.passwordLayout.setErrorRes(state.passwordError)
        binding.confirmationLayout.setErrorRes(state.confirmationError)

        binding.registerSubmit.isEnabled = state.isSubmitEnabled
        binding.registerProgress.isVisible = state.isLoading

        // The form locks while the account is being created. Registration is two writes, so leaving
        // the fields live would let the user edit a request that is already halfway through.
        val editable = !state.isLoading
        binding.nameInput.isEnabled = editable
        binding.emailInput.isEnabled = editable
        binding.passwordInput.isEnabled = editable
        binding.confirmationInput.isEnabled = editable
        binding.backToLogin.isEnabled = editable
    }
}
