package com.roeiamor.fitshare.ui.addworkout

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.data.model.WorkoutDraft
import com.roeiamor.fitshare.databinding.FragmentAddWorkoutBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.ChipBuilder
import com.roeiamor.fitshare.ui.common.labelRes
import com.roeiamor.fitshare.util.loadWorkoutImage
import com.roeiamor.fitshare.util.showSnackbar

/**
 * Publishes a workout: a photo, a validated form, and one call that uploads and writes.
 *
 * It owns the picker contracts - which have to be registered on a Fragment - and nothing else. All
 * validation and the publish itself live in [AddWorkoutViewModel].
 */
class AddWorkoutFragment : BaseFragment<FragmentAddWorkoutBinding>() {

    /** Null when publishing something new, a real id when editing an existing workout. */
    private val args: AddWorkoutFragmentArgs by navArgs()

    private val viewModel: AddWorkoutViewModel by viewModels {
        ServiceLocator.viewModelFactory(args.workoutId)
    }

    /**
     * The system photo picker.
     *
     * `PickVisualMedia` needs no storage permission at all: the user chooses in a system UI and the
     * app receives access to exactly that one item. Asking for READ_MEDIA_IMAGES to do the same job
     * would be requesting far more than the feature needs.
     *
     * A cancelled picker delivers null, and that is deliberately ignored rather than clearing the
     * selection - backing out of the picker should leave the photo you already chose alone.
     */
    private val pickImage = registerForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) viewModel.onImageSelected(uri)
    }

    /**
     * Where the camera writes its photo.
     *
     * Held as a field because [takePicture] reports only success or failure - the Uri it wrote to is
     * the one we handed it, so it has to survive between launching and the result arriving.
     */
    private var pendingCameraUri: Uri? = null

    /**
     * The camera.
     *
     * `TakePicture` returns a boolean, not an image: the photo goes to the Uri passed in. A false
     * result means the user backed out, in which case the empty file is deleted rather than left in
     * the cache directory.
     */
    private val takePicture = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { saved ->
        val uri = pendingCameraUri
        pendingCameraUri = null

        if (saved && uri != null) {
            viewModel.onImageSelected(uri)
        } else {
            uri?.let { CameraPhotoFile.delete(requireContext(), it) }
        }
    }

    /**
     * The runtime CAMERA permission.
     *
     * Denial is not an error and does not block anything: the user is told the gallery still works
     * and the form carries on. The app must stay fully usable with gallery only.
     */
    private val requestCameraPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera() else showSnackbar(R.string.error_camera_permission)
    }

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentAddWorkoutBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        bindInputs()
        buildChips()
        listenForImageSource()
        observeViewModel()
    }

    /** Receives the camera-or-gallery choice from [ImageSourcePicker]. */
    private fun listenForImageSource() {
        childFragmentManager.setFragmentResultListener(
            ImageSourcePicker.REQUEST_KEY,
            viewLifecycleOwner
        ) { _, bundle ->
            when (bundle.getString(ImageSourcePicker.RESULT_SOURCE)) {
                ImageSourcePicker.SOURCE_GALLERY -> launchPhotoPicker()
                ImageSourcePicker.SOURCE_CAMERA -> startCameraFlow()
            }
        }
    }

    /**
     * Asks for the CAMERA permission if it is not already granted, then opens the camera.
     *
     * The permission is requested here rather than when the screen opens, so the prompt arrives with
     * an obvious reason attached - the user has just asked to take a photo.
     */
    private fun startCameraFlow() {
        val granted = ContextCompat.checkSelfPermission(
            requireContext(),
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

        if (granted) launchCamera() else requestCameraPermission.launch(Manifest.permission.CAMERA)
    }

    /**
     * Creates the destination file and launches the camera.
     *
     * The camera app is a different process and cannot read our files, so the file is handed over as
     * a `content://` Uri from the FileProvider. A raw `file://` Uri throws FileUriExposedException
     * from API 24 up, which is this project's minSdk.
     */
    private fun launchCamera() {
        val uri = runCatching { CameraPhotoFile.create(requireContext()) }.getOrElse {
            showSnackbar(R.string.error_generic)
            return
        }
        pendingCameraUri = uri
        takePicture.launch(uri)
    }

    private fun bindInputs() {
        binding.titleInput.doAfterTextChanged { viewModel.onTitleChanged(it?.toString().orEmpty()) }
        binding.descriptionInput.doAfterTextChanged {
            viewModel.onDescriptionChanged(it?.toString().orEmpty())
        }
        binding.durationInput.doAfterTextChanged {
            viewModel.onDurationChanged(it?.toString().orEmpty())
        }

        // Duration is the last field, and its Done key only dismisses the keyboard. It deliberately
        // does not publish: a number is the last thing typed here, and finishing it is not the same
        // as deciding the whole workout is ready to go out.

        binding.choosePhoto.setOnClickListener { showImageSourcePicker() }
        binding.workoutImage.setOnClickListener { showImageSourcePicker() }
        binding.publishWorkout.setOnClickListener { viewModel.onPublish() }
    }

    /** Offers camera or gallery. Shown from the child manager so the result listener matches. */
    private fun showImageSourcePicker() {
        ImageSourcePicker().show(childFragmentManager, ImageSourcePicker.TAG)
    }

    /** Opens the system photo picker, restricted to images. */
    private fun launchPhotoPicker() {
        pickImage.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    /** Both chip rows are generated from their enums, so the options cannot drift from the model. */
    private fun buildChips() {
        ChipBuilder.buildSingleSelection(
            group = binding.categoryChips,
            baseId = CATEGORY_CHIP_BASE_ID,
            leadingLabelRes = null,
            entryLabels = WorkoutCategory.entries.map { it.labelRes() },
            checkedIndex = WorkoutCategory.entries.indexOf(DEFAULT_CATEGORY),
            onSelected = { index ->
                WorkoutCategory.entries.getOrNull(index)?.let(viewModel::onCategorySelected)
            }
        )

        ChipBuilder.buildSingleSelection(
            group = binding.difficultyChips,
            baseId = DIFFICULTY_CHIP_BASE_ID,
            leadingLabelRes = null,
            entryLabels = Difficulty.entries.map { it.labelRes() },
            checkedIndex = Difficulty.entries.indexOf(DEFAULT_DIFFICULTY),
            onSelected = { index ->
                Difficulty.entries.getOrNull(index)?.let(viewModel::onDifficultySelected)
            }
        )
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { render(it) }

        // Fires once, when an existing workout has loaded. Setting the text calls back into the
        // ViewModel through the watchers, which is fine - the values are identical - but it must
        // not be re-delivered, or a rotation would overwrite anything typed since.
        viewModel.prefill.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { prefillForm(it) }
        }

        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { showSnackbar(it) }
        }

        viewModel.navigateToFeed.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            findNavController().navigate(R.id.feedFragment)
        }
    }

    /**
     * Fills the form from an existing workout, when the screen opened as the editor.
     *
     * `SetTextI18n` is suppressed for one line and one reason: the duration goes back into the
     * validator as a number, and a locale-aware format would group it as "1,200" and stop parsing.
     * That check is about text a user reads; this is a value a user edits.
     */
    @SuppressLint("SetTextI18n")
    private fun prefillForm(draft: WorkoutDraft) {
        binding.titleInput.setText(draft.title)
        binding.descriptionInput.setText(draft.description)
        binding.durationInput.setText(draft.durationMinutes.toString())
        binding.categoryChips.check(
            CATEGORY_CHIP_BASE_ID + WorkoutCategory.entries.indexOf(draft.category)
        )
        binding.difficultyChips.check(
            DIFFICULTY_CHIP_BASE_ID + Difficulty.entries.indexOf(draft.difficulty)
        )
    }

    /** Draws one state: the photo, the three inline errors, and the upload progress. */
    private fun render(state: AddWorkoutUiState) {
        // A newly picked photo wins; otherwise the one the workout already has, when editing.
        binding.workoutImage.loadWorkoutImage(
            state.imageUri?.toString() ?: state.existingImageUrl
        )

        binding.screenTitle.setText(
            if (viewModel.isEditing) R.string.details_edit_title else R.string.add_title
        )
        binding.publishWorkout.setText(
            if (viewModel.isEditing) R.string.details_edit_save else R.string.add_publish
        )

        binding.titleLayout.error = state.titleError?.let(::getString)
        binding.descriptionLayout.error = state.descriptionError?.let(::getString)
        binding.durationLayout.error = state.durationError?.let(::getString)

        binding.publishWorkout.isEnabled = state.isSubmitEnabled
        binding.uploadProgress.isVisible = state.isUploading

        // The whole form locks during the upload: it is two network calls, and letting the user
        // edit a request that is already in flight would publish something they did not see.
        val editable = !state.isUploading
        binding.titleInput.isEnabled = editable
        binding.descriptionInput.isEnabled = editable
        binding.durationInput.isEnabled = editable
        binding.choosePhoto.isEnabled = editable
        binding.workoutImage.isEnabled = editable
        binding.categoryChips.isEnabled = editable
        binding.difficultyChips.isEnabled = editable
    }

    private companion object {
        val DEFAULT_CATEGORY = WorkoutCategory.STRENGTH
        val DEFAULT_DIFFICULTY = Difficulty.MEDIUM

        /** Stable, non-overlapping id ranges for the two generated chip rows. */
        const val CATEGORY_CHIP_BASE_ID = 2000
        const val DIFFICULTY_CHIP_BASE_ID = 3000
    }
}
