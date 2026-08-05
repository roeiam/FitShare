package com.roeiamor.fitshare.ui.profile

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.roeiamor.fitshare.databinding.FragmentEditProfileBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.util.loadAvatar
import com.roeiamor.fitshare.util.setErrorRes
import com.roeiamor.fitshare.util.showSnackbar

/**
 * Edits the signed-in user's name, bio and avatar.
 *
 * **Gallery only, no camera.** The add-workout screen offers both, because photographing a workout
 * you have just finished is the actual use case there - and that costs it a runtime CAMERA
 * permission, a FileProvider and temporary-file cleanup, about sixty lines. An avatar is almost
 * always a photo that already exists, so repeating all of that here would be duplication for a path
 * nobody would use.
 */
class EditProfileFragment : BaseFragment<FragmentEditProfileBinding>() {

    private val viewModel: EditProfileViewModel by viewModels { ServiceLocator.viewModelFactory }

    /**
     * The system photo picker.
     *
     * `PickVisualMedia` needs no storage permission at all: the user chooses in a system UI and the
     * app is handed a Uri for that one image. A cancelled pick returns null, which is left alone so
     * any previously chosen photo survives.
     */
    private val pickAvatar = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        uri?.let { viewModel.onAvatarSelected(it) }
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentEditProfileBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bindInputs()
        observeViewModel()
    }

    private fun bindInputs() {
        binding.nameInput.doAfterTextChanged { viewModel.onNameChanged(it?.toString().orEmpty()) }
        binding.bioInput.doAfterTextChanged { viewModel.onBioChanged(it?.toString().orEmpty()) }

        binding.avatar.setOnClickListener { launchAvatarPicker() }
        binding.changeAvatar.setOnClickListener { launchAvatarPicker() }
        binding.saveProfile.setOnClickListener { viewModel.onSave() }
    }

    private fun launchAvatarPicker() {
        pickAvatar.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner, ::render)

        // One-shot: writing into the fields calls back into the ViewModel, so re-delivering this on
        // rotation would overwrite whatever the user had since typed.
        viewModel.prefill.observe(viewLifecycleOwner) { event ->
            val user = event.getContentIfNotHandled() ?: return@observe
            binding.nameInput.setText(user.displayName)
            binding.bioInput.setText(user.bio)
        }

        viewModel.message.observe(viewLifecycleOwner) { event ->
            val messageRes = event.getContentIfNotHandled() ?: return@observe
            showSnackbar(messageRes)
        }

        viewModel.saved.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            findNavController().navigateUp()
        }
    }

    /**
     * Draws the current state.
     *
     * A newly picked photo wins over the stored one, so the user sees their choice immediately
     * rather than after the upload finishes.
     */
    private fun render(state: EditProfileUiState) {
        binding.nameLayout.setErrorRes(state.nameError)

        if (state.avatarUri != null) {
            binding.avatar.setImageURI(state.avatarUri)
        } else {
            binding.avatar.loadAvatar(state.existingPhotoUrl)
        }

        binding.saveProgress.isVisible = state.isSaving
        binding.saveProfile.isEnabled = state.isSaveEnabled && !state.isSaving
    }
}
