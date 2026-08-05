package com.roeiamor.fitshare.ui.favorites

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.FavoriteWorkout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.databinding.ItemFavoriteBinding
import com.roeiamor.fitshare.ui.common.labelRes
import com.roeiamor.fitshare.util.loadWorkoutImage

/**
 * Renders the saved-workouts list.
 *
 * Binds a [FavoriteWorkout] - the denormalized snapshot stored under `users/{uid}/favorites` - not a
 * [com.roeiamor.fitshare.data.model.Workout], which is the whole point: every field this row needs
 * is already in the one document the screen queried (SPEC section 3).
 *
 * @param onFavoriteClick invoked on a tap, so the Fragment owns navigation to the details screen.
 * @param onFavoriteLongClick invoked on a long press, so the Fragment can confirm before removing.
 */
class FavoriteAdapter(
    private val onFavoriteClick: (FavoriteWorkout) -> Unit,
    private val onFavoriteLongClick: (FavoriteWorkout) -> Unit
) : ListAdapter<FavoriteWorkout, FavoriteAdapter.FavoriteViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FavoriteViewHolder {
        val binding = ItemFavoriteBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return FavoriteViewHolder(binding, onFavoriteClick, onFavoriteLongClick)
    }

    override fun onBindViewHolder(holder: FavoriteViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Holds one saved-workout row and knows how to fill it. */
    class FavoriteViewHolder(
        private val binding: ItemFavoriteBinding,
        private val onFavoriteClick: (FavoriteWorkout) -> Unit,
        private val onFavoriteLongClick: (FavoriteWorkout) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills the row. Sets every view unconditionally, because views are recycled. */
        fun bind(favorite: FavoriteWorkout) {
            val context = binding.root.context

            binding.favoriteImage.loadWorkoutImage(favorite.imageUrl)
            binding.favoriteTitle.text = favorite.title

            val category = context.getString(
                WorkoutCategory.fromName(favorite.category).labelRes()
            )
            binding.favoriteAuthor.text =
                context.getString(R.string.favorites_author_category, favorite.authorName, category)

            binding.root.setOnClickListener { onFavoriteClick(favorite) }
            binding.root.setOnLongClickListener {
                onFavoriteLongClick(favorite)
                // Consumed, so the long press does not also fire the click listener.
                true
            }
        }
    }

    private companion object {

        /** Identity is the saved workout's id; content uses the data class `equals`. */
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<FavoriteWorkout>() {

            override fun areItemsTheSame(
                oldItem: FavoriteWorkout,
                newItem: FavoriteWorkout
            ): Boolean = oldItem.workoutId == newItem.workoutId

            override fun areContentsTheSame(
                oldItem: FavoriteWorkout,
                newItem: FavoriteWorkout
            ): Boolean = oldItem == newItem
        }
    }
}
