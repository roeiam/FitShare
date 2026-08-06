package com.roeiamor.fitshare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import com.roeiamor.fitshare.databinding.FragmentForgotPasswordBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.util.setErrorRes
import com.roeiamor.fitshare.util.showSnackbar

/**
 * Sends a password reset email.
 *
 * The screen stays put after a successful send so the confirmation snackbar is actually readable;
 * the user returns with Back. The confirmation never says whether the address is registered.
 */
class ForgotPasswordFragment : BaseFragment<FragmentForgotPasswordBinding>() {

    private val viewModel: ForgotPasswordViewModel by viewModels { ServiceLocator.viewModelFactory }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentForgotPasswordBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindInputs()
        observeViewModel()
    }

    /** Sends every keystroke and tap to the ViewModel. */
    private fun bindInputs() {
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        // The Done key only closes the keyboard; sending the reset link needs the button.
        binding.forgotSubmit.setOnClickListener { viewModel.onSubmit() }
    }

    /** Observes state and one-shot events, always with `viewLifecycleOwner`. */
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { render(it) }

        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { showSnackbar(it) }
        }
    }

    /** Draws one state: the inline error, button availability, and the progress indicator. */
    private fun render(state: ForgotPasswordUiState) {
        binding.emailLayout.setErrorRes(state.emailError)
        binding.forgotSubmit.isEnabled = state.isSubmitEnabled
        binding.forgotProgress.isVisible = state.isLoading
        binding.emailInput.isEnabled = !state.isLoading
    }
}
