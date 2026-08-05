package com.roeiamor.fitshare.ui.details

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.databinding.FragmentWorkoutDetailsBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer
import com.roeiamor.fitshare.ui.common.labelRes
import com.roeiamor.fitshare.ui.common.relativeTimeText
import com.roeiamor.fitshare.util.TimeFormatter
import com.roeiamor.fitshare.util.loadAvatar
import com.roeiamor.fitshare.util.loadWorkoutImage
import com.roeiamor.fitshare.util.showSnackbar

/**
 * One workout in full, with its comments and everything you can do to it.
 *
 * The screen is driven entirely by live listeners through [WorkoutDetailsViewModel], so a like or a
 * comment from another device appears here without anything being refreshed - and the counters here
 * and in the feed cannot drift apart, because both are reading the same document.
 */
class WorkoutDetailsFragment : BaseFragment<FragmentWorkoutDetailsBinding>() {

    /** Which workout to show. Supplied by SafeArgs, so it cannot arrive as a missing bundle key. */
    private val args: WorkoutDetailsFragmentArgs by navArgs()

    private val viewModel: WorkoutDetailsViewModel by viewModels {
        ServiceLocator.viewModelFactory(args.workoutId)
    }

    private lateinit var stateRenderer: StateRenderer
    private var commentAdapter: CommentAdapter? = null

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentWorkoutDetailsBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateRenderer = StateRenderer(
            content = binding.content,
            loading = binding.stateLoading,
            empty = binding.stateEmpty,
            error = binding.stateError
        )

        bindActions()
        observeViewModel()
    }

    override fun onDestroyView() {
        binding.commentsList.adapter = null
        commentAdapter = null
        super.onDestroyView()
    }

    private fun bindActions() {
        binding.backButton.setOnClickListener { findNavController().navigateUp() }
        binding.likeButton.setOnClickListener { viewModel.onLikeClicked() }
        binding.favoriteButton.setOnClickListener { viewModel.onFavoriteClicked() }
        binding.shareButton.setOnClickListener { shareWorkout() }
        binding.deleteButton.setOnClickListener { confirmDeleteWorkout() }
        binding.editButton.setOnClickListener { openEditor() }

        binding.authorRow.setOnClickListener {
            val authorId = viewModel.currentWorkout()?.authorId ?: return@setOnClickListener
            openProfile(authorId)
        }

        binding.commentInput.doAfterTextChanged {
            viewModel.onCommentTextChanged(it?.toString().orEmpty())
        }
        binding.sendComment.setOnClickListener { viewModel.onSendComment() }
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner) { render(it) }

        viewModel.isCommentValid.observe(viewLifecycleOwner) { valid ->
            binding.sendComment.isEnabled = valid
        }

        viewModel.clearCommentField.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            binding.commentInput.text?.clear()
        }

        viewModel.message.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { showSnackbar(it) }
        }

        viewModel.navigateBack.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled() ?: return@observe
            findNavController().navigateUp()
        }
    }

    /** Draws one state. The `when` is exhaustive, so a new state cannot be forgotten. */
    private fun render(state: WorkoutDetailsUiState) {
        binding.commentInputRow.isVisible = state is WorkoutDetailsUiState.Content

        when (state) {
            WorkoutDetailsUiState.Loading -> stateRenderer.showLoading()

            is WorkoutDetailsUiState.Content -> {
                renderContent(state)
                stateRenderer.showContent()
            }

            // Not an error: somebody deleting their workout while you look at it is normal, and
            // "try again" would be the wrong thing to offer.
            WorkoutDetailsUiState.Deleted -> stateRenderer.showEmpty(
                titleRes = R.string.details_deleted_title,
                bodyRes = R.string.details_deleted_body,
                actionRes = R.string.action_back,
                onAction = { findNavController().navigateUp() }
            )

            is WorkoutDetailsUiState.Error -> stateRenderer.showError(
                message = getString(state.messageRes),
                onRetry = { /* The listeners retry by themselves; this just dismisses the state. */ }
            )
        }
    }

    private fun renderContent(state: WorkoutDetailsUiState.Content) {
        val workout = state.workout

        binding.heroImage.loadWorkoutImage(workout.imageUrl)
        binding.workoutTitle.text = workout.title
        binding.authorName.text = workout.authorName
        binding.authorAvatar.loadAvatar(workout.authorPhotoUrl)
        binding.createdAt.text = formatCreatedAt(workout)

        binding.categoryChip.setText(WorkoutCategory.fromName(workout.category).labelRes())
        binding.durationChip.text = resources.getQuantityString(
            R.plurals.unit_minutes,
            workout.durationMinutes,
            workout.durationMinutes
        )
        binding.difficultyChip.setText(Difficulty.fromName(workout.difficulty).labelRes())

        binding.workoutDescription.isVisible = workout.description.isNotBlank()
        binding.workoutDescription.text = workout.description

        // animate = true: this is the only place a like changes, and the state that arrives after a
        // tap is the one worth reacting to. LikeButton itself skips the animation when nothing
        // actually changed, so a re-render caused by someone else's comment does not bounce it.
        binding.likeButton.render(state.isLiked, workout.likesCount, animate = true)
        renderFavorite(state.isFavorite)

        binding.editButton.isVisible = state.isOwner
        binding.deleteButton.isVisible = state.isOwner

        renderComments(state)
    }

    /** Mint when saved, muted when not - the same visual language as the like mark. */
    private fun renderFavorite(isFavorite: Boolean) {
        val colorRes = if (isFavorite) R.color.link_text else R.color.text_muted
        binding.favoriteButton.setColorFilter(ContextCompat.getColor(requireContext(), colorRes))
        binding.favoriteButton.contentDescription = getString(
            if (isFavorite) R.string.details_unsaved else R.string.details_save
        )
    }

    /**
     * The adapter is rebuilt only when the signed-in user changes, because `currentUserId` decides
     * which rows are long-pressable and it is fixed for the life of a session.
     */
    private fun renderComments(state: WorkoutDetailsUiState.Content) {
        if (commentAdapter == null) {
            commentAdapter = CommentAdapter(
                currentUserId = state.currentUserId,
                onDeleteRequested = ::confirmDeleteComment,
                onAuthorClick = ::openProfile
            )
            binding.commentsList.adapter = commentAdapter
        }
        commentAdapter?.submitList(state.comments)
        binding.noComments.isVisible = state.comments.isEmpty()
    }

    /**
     * Opens a user's profile, read-only.
     *
     * Shared by the workout's author row and by every comment author, so the same navigation is
     * written once rather than twice.
     */
    private fun openProfile(userId: String) {
        findNavController().navigate(
            WorkoutDetailsFragmentDirections.actionWorkoutDetailsToProfile(userId = userId)
        )
    }

    private fun formatCreatedAt(workout: Workout): String {
        val millis = workout.createdAt?.toDate()?.time ?: return getString(R.string.time_now)
        return requireContext().relativeTimeText(
            TimeFormatter.describe(millis, System.currentTimeMillis())
        )
    }

    /** Shares the workout as plain text (SPEC feature 17). */
    private fun shareWorkout() {
        val workout = viewModel.currentWorkout() ?: return

        val text = getString(
            R.string.details_share_text,
            workout.title,
            workout.authorName,
            resources.getQuantityString(
                R.plurals.unit_minutes,
                workout.durationMinutes,
                workout.durationMinutes
            )
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = SHARE_MIME_TYPE
            putExtra(Intent.EXTRA_TEXT, text)
        }
        startActivity(Intent.createChooser(intent, getString(R.string.details_share)))
    }

    /** Deleting a workout cannot be undone, so it is always confirmed. */
    private fun confirmDeleteWorkout() {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.details_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.onDeleteWorkoutConfirmed()
            }
            .show()
    }

    /** Long-pressing your own comment offers to delete it, behind a confirmation. */
    private fun confirmDeleteComment(comment: Comment) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.details_comment_delete_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_delete) { _, _ ->
                viewModel.onDeleteCommentConfirmed(comment)
            }
            .show()
    }

    /** Opens the add form in edit mode, pre-filled with this workout. */
    private fun openEditor() {
        findNavController().navigate(
            WorkoutDetailsFragmentDirections.actionWorkoutDetailsToAddWorkout(
                workoutId = args.workoutId
            )
        )
    }

    private companion object {
        const val SHARE_MIME_TYPE = "text/plain"
    }
}
