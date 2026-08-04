package com.roeiamor.fitshare.ui.feed

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.databinding.FragmentFeedBinding
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer

/**
 * The workout feed, and the app's start destination once a session exists.
 *
 * Phase 2 renders the empty state, which is what actually puts the shared state layouts, the Hebrew
 * copy and the RTL mirroring on screen where they can be checked. Phase 4 adds the RecyclerView,
 * the category filter, search and pull to refresh.
 */
class FeedFragment : BaseFragment<FragmentFeedBinding>() {

    private lateinit var stateRenderer: StateRenderer

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

        // There is no data source yet, so the feed is genuinely empty rather than pretending to be.
        // Phase 4 drives this from FeedViewModel instead.
        stateRenderer.showEmpty(
            titleRes = R.string.feed_empty_title,
            bodyRes = R.string.feed_empty_body,
            actionRes = R.string.feed_empty_action,
            onAction = {
                findNavController().navigate(FeedFragmentDirections.actionFeedToAddWorkout())
            }
        )

        // Phase 2 scaffolding, removed in Phase 4 once workout cards themselves are tappable.
        binding.previewDetails.setOnClickListener {
            findNavController().navigate(
                FeedFragmentDirections.actionFeedToWorkoutDetails(workoutId = "")
            )
        }
    }
}
