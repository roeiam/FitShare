package com.roeiamor.fitshare.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.widget.doAfterTextChanged
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.google.android.material.chip.Chip
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.databinding.FragmentFeedBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer
import com.roeiamor.fitshare.ui.common.labelRes

/**
 * The workout feed, and the app's start destination once a session exists.
 *
 * It observes one [FeedUiState] and renders it. It performs no filtering, no searching and no
 * sorting of its own - all of that happens in [FeedViewModel], which is why this class stays short.
 */
class FeedFragment : BaseFragment<FragmentFeedBinding>() {

    private val viewModel: FeedViewModel by viewModels { ServiceLocator.viewModelFactory }

    private lateinit var stateRenderer: StateRenderer
    private lateinit var workoutAdapter: WorkoutAdapter

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFeedBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateRenderer = StateRenderer(
            content = binding.content,
            loading = binding.stateLoading,
            empty = binding.stateEmpty,
            error = binding.stateError
        )

        setUpList()
        setUpCategoryChips()
        setUpSearch()
        setUpRefresh()

        viewModel.uiState.observe(viewLifecycleOwner) { render(it) }
    }

    override fun onDestroyView() {
        // The RecyclerView outlives this callback inside the binding, and an adapter that still
        // holds the click lambda would keep this Fragment reachable. Detaching is cheap insurance.
        binding.content.adapter = null
        super.onDestroyView()
    }

    private fun setUpList() {
        workoutAdapter = WorkoutAdapter(onWorkoutClick = ::openDetails)
        binding.content.adapter = workoutAdapter
        binding.content.setHasFixedSize(true)
    }

    /**
     * Builds the filter row from [WorkoutCategory] plus an "הכל" chip.
     *
     * Generated rather than written in XML so the chips cannot drift from the enum, and so every
     * label still comes from the single mapping function in `ui/common/EnumLabels.kt`.
     */
    private fun setUpCategoryChips() {
        val group = binding.categoryChips
        group.removeAllViews()

        group.addView(buildCategoryChip(id = CHIP_ID_ALL, labelRes = R.string.feed_filter_all))
        WorkoutCategory.entries.forEachIndexed { index, category ->
            group.addView(buildCategoryChip(id = CHIP_ID_ALL + 1 + index, labelRes = category.labelRes()))
        }
        group.check(CHIP_ID_ALL)

        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            val category = WorkoutCategory.entries.getOrNull(checkedId - CHIP_ID_ALL - 1)
            viewModel.onCategorySelected(category)
        }
    }

    private fun buildCategoryChip(id: Int, labelRes: Int): Chip =
        Chip(requireContext(), null, com.google.android.material.R.attr.chipStyle).apply {
            this.id = id
            setChipDrawable(
                com.google.android.material.chip.ChipDrawable.createFromAttributes(
                    requireContext(),
                    null,
                    0,
                    R.style.Widget_FitShare_Chip_Filter
                )
            )
            setText(labelRes)
            isCheckable = true
        }

    private fun setUpSearch() {
        binding.searchInput.doAfterTextChanged {
            viewModel.onSearchChanged(it?.toString().orEmpty())
        }
    }

    private fun setUpRefresh() {
        binding.swipeRefresh.setOnRefreshListener { viewModel.refresh() }
    }

    /** Draws one state. The `when` is exhaustive, so a new state cannot be forgotten here. */
    private fun render(state: FeedUiState) {
        // Any emission means the refresh finished, whatever its outcome.
        binding.swipeRefresh.isRefreshing = false

        when (state) {
            FeedUiState.Loading -> stateRenderer.showLoading()

            is FeedUiState.Content -> {
                workoutAdapter.submitList(state.workouts)
                stateRenderer.showContent()
            }

            is FeedUiState.Empty -> renderEmpty(state)

            is FeedUiState.Error -> stateRenderer.showError(
                message = getString(state.messageRes),
                onRetry = { viewModel.refresh() }
            )
        }
    }

    /**
     * Two different empty states.
     *
     * "העלה את האימון הראשון" is bad advice when the feed is full and the user simply typed a word
     * that matches nothing - and offering an "add workout" button there would be worse.
     */
    private fun renderEmpty(state: FeedUiState.Empty) {
        if (state.isFiltered) {
            stateRenderer.showEmpty(
                titleRes = R.string.feed_no_results_title,
                bodyRes = R.string.feed_no_results_body
            )
        } else {
            stateRenderer.showEmpty(
                titleRes = R.string.feed_empty_title,
                bodyRes = R.string.feed_empty_body,
                actionRes = R.string.feed_empty_action,
                onAction = {
                    findNavController().navigate(FeedFragmentDirections.actionFeedToAddWorkout())
                }
            )
        }
    }

    private fun openDetails(workout: Workout) {
        findNavController().navigate(
            FeedFragmentDirections.actionFeedToWorkoutDetails(workoutId = workout.id)
        )
    }

    private companion object {
        /**
         * A fixed id for the "הכל" chip; the category chips take the ids after it, in enum order.
         * Generated views need stable ids so ChipGroup single-selection works and so the checked
         * chip survives a configuration change.
         */
        const val CHIP_ID_ALL = 1000
    }
}
