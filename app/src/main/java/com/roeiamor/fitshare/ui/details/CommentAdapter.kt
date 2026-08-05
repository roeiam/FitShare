package com.roeiamor.fitshare.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.databinding.ItemCommentBinding
import com.roeiamor.fitshare.ui.common.relativeTimeText
import com.roeiamor.fitshare.util.TimeFormatter
import com.roeiamor.fitshare.util.loadAvatar

/**
 * Renders a workout's comments.
 *
 * A [ListAdapter] with a [DiffUtil.ItemCallback], never `notifyDataSetChanged` - the list is backed
 * by a live listener, so one new comment re-emits the whole thread, and rebinding every row would
 * throw away the scroll position each time anybody posted anything.
 *
 * @param currentUserId used to decide which rows offer deletion; null when signed out.
 * @param onDeleteRequested invoked on a long press of the user's own comment. The Fragment shows the
 *   confirmation dialog, because a dialog needs a Fragment.
 * @param onAuthorClick invoked with the author's uid when their name or avatar is tapped, so the
 *   Fragment can open that user's profile.
 */
class CommentAdapter(
    private val currentUserId: String?,
    private val onDeleteRequested: (Comment) -> Unit,
    private val onAuthorClick: (String) -> Unit
) : ListAdapter<Comment, CommentAdapter.CommentViewHolder>(DIFF_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CommentViewHolder {
        val binding = ItemCommentBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return CommentViewHolder(binding, currentUserId, onDeleteRequested, onAuthorClick)
    }

    override fun onBindViewHolder(holder: CommentViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    /** Holds one comment row. */
    class CommentViewHolder(
        private val binding: ItemCommentBinding,
        private val currentUserId: String?,
        private val onDeleteRequested: (Comment) -> Unit,
        private val onAuthorClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {

        /** Fills the row. Every property is set unconditionally, because views are recycled. */
        fun bind(comment: Comment) {
            val context = binding.root.context

            binding.commentAuthor.text = comment.authorName
            binding.commentAvatar.loadAvatar(comment.authorPhotoUrl)
            binding.commentText.text = comment.text
            binding.commentTime.text = formatCreatedAt(comment)

            // The name and the avatar open that user's profile. The whole row is not clickable,
            // because the row's gesture is the long press that deletes it - a tap anywhere opening a
            // profile would make an accidental brush against a comment navigate away.
            val openAuthor = View.OnClickListener { onAuthorClick(comment.authorId) }
            binding.commentAuthor.setOnClickListener(openAuthor)
            binding.commentAvatar.setOnClickListener(openAuthor)

            // Long press deletes, but only your own comment. Setting the listener to null on other
            // people's rows also clears the one a recycled view may still be carrying.
            val isMine = currentUserId != null && currentUserId == comment.authorId
            binding.root.isLongClickable = isMine
            binding.root.setOnLongClickListener(
                if (isMine) {
                    { onDeleteRequested(comment); true }
                } else {
                    null
                }
            )
        }

        /** `createdAt` is null between a local write and the server acknowledging it. */
        private fun formatCreatedAt(comment: Comment): String {
            val context = binding.root.context
            val millis = comment.createdAt?.toDate()?.time
                ?: return context.getString(R.string.time_now)

            return context.relativeTimeText(TimeFormatter.describe(millis, System.currentTimeMillis()))
        }
    }

    private companion object {
        val DIFF_CALLBACK = object : DiffUtil.ItemCallback<Comment>() {
            override fun areItemsTheSame(oldItem: Comment, newItem: Comment): Boolean =
                oldItem.id == newItem.id

            override fun areContentsTheSame(oldItem: Comment, newItem: Comment): Boolean =
                oldItem == newItem
        }
    }
}
