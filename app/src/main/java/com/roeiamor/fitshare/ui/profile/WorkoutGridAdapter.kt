package com.roeiamor.fitshare.ui.profile

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.databinding.ItemWorkoutGridBinding
import com.roeiamor.fitshare.util.loadGridWorkoutImage

/**
 * Renders the grid of a user's own workouts on the profile screen.
 *
 * A second adapter rather than reusing [com.roeiamor.fitshare.ui.feed.WorkoutAdapter], because the
 * two draw genuinely different things: a feed card is full width with an author row, chips and
 * counters, while a grid cell is a third of that width and can only carry a photo and a title.
 * Forcing one adapter to do both would mean a layout full of conditionally hidden views, which is
 * harder to read than two short adapters.
 *
 * A [ListAdapter] with a [DiffUtil.ItemCallback], never `notifyDataSetChanged` - the grid is backed
 * by a live listener, so publishing or liking a workout re-emits the whole list.
 *
 * @param onWorkoutClick invoked with the tapped workout, so the Fragment owns navigation.
 */
class WorkoutGridAdapter(
    private val onWorkoutClick: (Workout) -> Unit
) : ListAdapter<Workout, WorkoutGridAdapter.GridViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridViewHolder {
        val binding = ItemWorkoutGridBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return GridViewHolder(binding, onWorkoutClick)
    }

    override fun onBindViewHolder(holder: GridViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Holds one grid cell and knows how to fill it from a [Workout]. */
    class GridViewHolder(
        private val binding: ItemWorkoutGridBinding,
        private val onWorkoutClick: (Workout) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills the cell. Sets every view unconditionally, because views are recycled. */
        fun bind(workout: Workout) {
            binding.gridImage.loadGridWorkoutImage(workout.imageUrl)
            binding.gridTitle.text = workout.title
            binding.root.setOnClickListener { onWorkoutClick(workout) }
        }
    }

    private companion object {

        /** Identity is the document id; content uses the data class `equals`. */
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Workout>() {

            override fun areItemsTheSame(oldItem: Workout, newItem: Workout): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Workout, newItem: Workout): Boolean =
                oldItem == newItem
        }
    }
}
