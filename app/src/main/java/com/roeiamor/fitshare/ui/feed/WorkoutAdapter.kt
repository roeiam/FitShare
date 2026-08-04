package com.roeiamor.fitshare.ui.feed

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.databinding.ItemWorkoutBinding
import com.roeiamor.fitshare.ui.common.labelRes
import com.roeiamor.fitshare.ui.common.relativeTimeText
import com.roeiamor.fitshare.util.TimeFormatter
import com.roeiamor.fitshare.util.loadAvatar
import com.roeiamor.fitshare.util.loadWorkoutImage

/**
 * Renders the feed.
 *
 * A [ListAdapter] with a [DiffUtil.ItemCallback], never `notifyDataSetChanged`. That matters more
 * here than on a normal list: the feed is backed by a live Firestore listener, so a single like
 * elsewhere re-emits the whole list. `notifyDataSetChanged` would rebind and redraw every visible
 * card and lose the scroll position on every remote change; DiffUtil rebinds only what differs.
 *
 * @param onWorkoutClick invoked with the tapped workout, so the Fragment owns navigation.
 */
class WorkoutAdapter(
    private val onWorkoutClick: (Workout) -> Unit
) : ListAdapter<Workout, WorkoutAdapter.WorkoutViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WorkoutViewHolder {
        val binding = ItemWorkoutBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return WorkoutViewHolder(binding, onWorkoutClick)
    }

    override fun onBindViewHolder(holder: WorkoutViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Holds one card and knows how to fill it from a [Workout]. */
    class WorkoutViewHolder(
        private val binding: ItemWorkoutBinding,
        private val onWorkoutClick: (Workout) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills every view on the card. Sets each one unconditionally, because views are recycled. */
        fun bind(workout: Workout) {
            val context = binding.root.context

            binding.authorName.text = workout.authorName
            binding.authorAvatar.loadAvatar(workout.authorPhotoUrl)
            binding.createdAt.text = formatCreatedAt(workout)

            binding.workoutImage.loadWorkoutImage(workout.imageUrl)
            binding.workoutTitle.text = workout.title

            binding.categoryChip.setText(WorkoutCategory.fromName(workout.category).labelRes())
            binding.durationChip.text = context.resources.getQuantityString(
                R.plurals.unit_minutes,
                workout.durationMinutes,
                workout.durationMinutes
            )
            binding.difficultyChip.setText(Difficulty.fromName(workout.difficulty).labelRes())

            binding.likesCount.text = workout.likesCount.toString()
            binding.commentsCount.text = workout.commentsCount.toString()

            binding.root.setOnClickListener { onWorkoutClick(workout) }
        }

        /**
         * The relative time under the author's name.
         *
         * `createdAt` is null for the moment between a local write and the server acknowledging it,
         * so a just-published workout would otherwise have a blank timestamp. Treating null as
         * "now" is both true and the least surprising thing to show.
         */
        private fun formatCreatedAt(workout: Workout): String {
            val context = binding.root.context
            val createdAtMillis = workout.createdAt?.toDate()?.time
                ?: return context.getString(R.string.time_now)

            val relative = TimeFormatter.describe(createdAtMillis, System.currentTimeMillis())
            return context.relativeTimeText(relative)
        }
    }

    private companion object {

        /**
         * Identity is the document id; content uses the data class `equals`, so any field change
         * rebinds the card and nothing else.
         */
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Workout>() {

            override fun areItemsTheSame(oldItem: Workout, newItem: Workout): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Workout, newItem: Workout): Boolean =
                oldItem == newItem
        }
    }
}
