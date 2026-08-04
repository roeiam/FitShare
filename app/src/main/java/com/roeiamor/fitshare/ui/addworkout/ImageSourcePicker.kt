package com.roeiamor.fitshare.ui.addworkout

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.roeiamor.fitshare.databinding.BottomSheetImageSourceBinding

/**
 * Asks whether the workout photo should come from the camera or the gallery.
 *
 * The answer travels back through the Fragment Result API rather than a callback held by the caller.
 * A dialog fragment is destroyed and recreated by the system on a configuration change, and a
 * lambda captured at construction would not survive that - the sheet would come back alive with a
 * dead listener and the buttons would silently do nothing.
 */
class ImageSourcePicker : BottomSheetDialogFragment() {

    private var _binding: BottomSheetImageSourceBinding? = null
    private val binding get() = checkNotNull(_binding) { "Binding accessed outside the view lifecycle" }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = BottomSheetImageSourceBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.fromCamera.setOnClickListener { reportChoice(SOURCE_CAMERA) }
        binding.fromGallery.setOnClickListener { reportChoice(SOURCE_GALLERY) }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun reportChoice(source: String) {
        setFragmentResult(REQUEST_KEY, bundleOf(RESULT_SOURCE to source))
        dismiss()
    }

    companion object {
        /** The key the host Fragment listens on. */
        const val REQUEST_KEY = "image_source_request"

        /** The bundle key holding the chosen source. */
        const val RESULT_SOURCE = "image_source"

        /** The user chose the camera. */
        const val SOURCE_CAMERA = "camera"

        /** The user chose the gallery. */
        const val SOURCE_GALLERY = "gallery"

        /** Tag used when showing the sheet. */
        const val TAG = "ImageSourcePicker"
    }
}
