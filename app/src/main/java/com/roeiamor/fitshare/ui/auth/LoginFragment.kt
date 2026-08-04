package com.roeiamor.fitshare.ui.auth

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.roeiamor.fitshare.databinding.FragmentLoginBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.util.onImeDone
import com.roeiamor.fitshare.util.setErrorRes
import com.roeiamor.fitshare.util.showSnackbar

/**
 * The sign-in screen, and the app's start destination when no session exists.
 *
 * It inflates the layout, forwards keystrokes and taps to [LoginViewModel], and renders whatever
 * state comes back. It performs no validation and makes no Firebase call of its own.
 */
class LoginFragment : BaseFragment<FragmentLoginBinding>() {

    private val viewModel: LoginViewModel by viewModels { ServiceLocator.viewModelFactory }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentLoginBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindInputs()
        observeViewModel()
    }

    /** Sends every keystroke and tap to the ViewModel. */
    private fun bindInputs() {
        binding.emailInput.doAfterTextChanged { viewModel.onEmailChanged(it?.toString().orEmpty()) }
        binding.passwordInput.doAfterTextChanged {
            viewModel.onPasswordChanged(it?.toString().orEmpty())
        }

        // The last field submits, so a user who has just typed their password does not have to
        // dismiss the keyboard and hunt for the button.
        binding.passwordInput.onImeDone { viewModel.onSubmit() }

        binding.loginSubmit.setOnClickListener { viewModel.onSubmit() }
        binding.goToRegister.setOnClickListener {
            findNavController().navigate(LoginFragmentDirections.actionLoginToRegister())
        }
        binding.goToForgotPassword.setOnClickListener {
            findNavController().navigate(LoginFragmentDirections.actionLoginToForgotPassword())
        }
    }

    /**
     * Observes state and one-shot events.
     *
     * Everything is observed with `viewLifecycleOwner`, not `this`. A Fragment outlives its view, so
     * observing with the Fragment would keep delivering to views that no longer exist.
     */
    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { render(it) }

        viewModel.navigateToFeed.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            // The action pops the auth flow, so Back from the feed exits instead of coming back here.
            findNavController().navigate(LoginFragmentDirections.actionLoginToFeed())
        }

        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { showSnackbar(it) }
        }
    }

    /** Draws one state: inline errors, button availability, and the progress indicator. */
    private fun render(state: LoginUiState) {
        binding.emailLayout.setErrorRes(state.emailError)
        binding.passwordLayout.setErrorRes(state.passwordError)

        binding.loginSubmit.isEnabled = state.isSubmitEnabled
        binding.loginProgress.isVisible = state.isLoading

        // Fields lock while the request runs, so the form cannot change under an in-flight sign-in.
        binding.emailInput.isEnabled = !state.isLoading
        binding.passwordInput.isEnabled = !state.isLoading
        binding.goToRegister.isEnabled = !state.isLoading
        binding.goToForgotPassword.isEnabled = !state.isLoading
    }
}
