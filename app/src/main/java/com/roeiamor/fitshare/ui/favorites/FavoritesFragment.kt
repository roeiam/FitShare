package com.roeiamor.fitshare.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.FavoriteWorkout
import com.roeiamor.fitshare.databinding.FragmentFavoritesBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer
import com.roeiamor.fitshare.util.showSnackbar

/**
 * The workouts the signed-in user has saved.
 *
 * The whole screen is one Firestore query, because a favourite is stored as a denormalized snapshot
 * rather than a pointer (SPEC section 3). Long press removes one, after a confirmation.
 */
class FavoritesFragment : BaseFragment<FragmentFavoritesBinding>() {

    private val viewModel: FavoritesViewModel by viewModels { ServiceLocator.viewModelFactory }

    private lateinit var stateRenderer: StateRenderer

    private val favoriteAdapter = FavoriteAdapter(
        onFavoriteClick = ::openWorkout,
        onFavoriteLongClick = ::confirmRemove
    )

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFavoritesBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateRenderer = StateRenderer(
            // The list is the content state: it needs no container of its own.
            content = binding.favoritesList,
            loading = binding.stateLoading,
            empty = binding.stateEmpty,
            error = binding.stateError
        )

        binding.favoritesList.layoutManager = LinearLayoutManager(requireContext())
        binding.favoritesList.adapter = favoriteAdapter

        observeViewModel()
    }

    private fun observeViewModel() {
        viewModel.uiState.observe(viewLifecycleOwner, ::render)

        viewModel.message.observe(viewLifecycleOwner) { event ->
            val messageRes = event.getContentIfNotHandled() ?: return@observe
            showSnackbar(messageRes)
        }
    }

    /** Renders exactly one of the four states. Exhaustive, so a new state cannot be forgotten. */
    private fun render(state: FavoritesUiState) {
        when (state) {
            is FavoritesUiState.Loading -> stateRenderer.showLoading()

            is FavoritesUiState.Content -> {
                favoriteAdapter.submitList(state.favorites)
                stateRenderer.showContent()
            }

            // The action sends the user to the feed, because that is where something gets saved.
            is FavoritesUiState.Empty -> stateRenderer.showEmpty(
                titleRes = R.string.favorites_empty_title,
                bodyRes = R.string.favorites_empty_body,
                actionRes = R.string.favorites_empty_action,
                onAction = { findNavController().navigate(R.id.feedFragment) }
            )

            is FavoritesUiState.Error -> stateRenderer.showError(
                message = getString(state.messageRes),
                onRetry = { /* The listener is live; it recovers on its own once there is a connection. */ }
            )
        }
    }

    private fun openWorkout(favorite: FavoriteWorkout) {
        findNavController().navigate(
            FavoritesFragmentDirections.actionFavoritesToWorkoutDetails(favorite.workoutId)
        )
    }

    /**
     * Confirms before removing.
     *
     * A long press is easy to trigger by accident while scrolling, and the removal is not something
     * the user can undo from this screen, so it asks first - the same rule as deleting a workout.
     */
    private fun confirmRemove(favorite: FavoriteWorkout) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(R.string.favorites_remove_confirm)
            .setNegativeButton(R.string.action_cancel, null)
            .setPositiveButton(R.string.action_confirm) { _, _ ->
                viewModel.onRemoveConfirmed(favorite.workoutId)
            }
            .show()
    }

    /**
     * Releases the adapter with the view, so the destroyed view tree is not kept alive by the
     * RecyclerView's reference back to it.
     */
    override fun onDestroyView() {
        binding.favoritesList.adapter = null
        super.onDestroyView()
    }
}
