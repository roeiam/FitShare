package com.roeiamor.fitshare.ui.favorites

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.databinding.FragmentFavoritesBinding
import com.roeiamor.fitshare.ui.common.BaseFragment
import com.roeiamor.fitshare.ui.common.StateRenderer

/**
 * The workouts the signed-in user has saved.
 *
 * Phase 2 renders the empty state. Phase 7 adds the saved-workouts list, which reads from the
 * denormalized snapshot under users/{uid}/favorites so it needs a single query.
 */
class FavoritesFragment : BaseFragment<FragmentFavoritesBinding>() {

    private lateinit var stateRenderer: StateRenderer

    override fun inflateBinding(inflater: LayoutInflater, container: ViewGroup?) =
        FragmentFavoritesBinding.inflate(inflater, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        stateRenderer = StateRenderer(
            content = binding.content,
            loading = binding.stateLoading,
            empty = binding.stateEmpty,
            error = binding.stateError
        )

        stateRenderer.showEmpty(
            titleRes = R.string.favorites_empty_title,
            bodyRes = R.string.favorites_empty_body
        )
    }
}
