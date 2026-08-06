package com.roeiamor.fitshare.ui.details

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.databinding.ItemCommentBinding
import com.roeiamor.fitshare.ui.common.workoutTimeText
import com.roeiamor.fitshare.util.loadAvatar

/**
 * Renders a workout's comments.
 *
 * A [ListAdapter] with a [DiffUtil.ItemCallback], never `notifyDataSetChanged` - the list is backed
 * by a live listener, so one new comment re-emits the whole thread, and rebinding every row would
 * throw away the scroll position each time anybody posted anything.
 *
 * @param currentUserId used to decide which rows offer deletion; null when signed out.
 * @param onDeleteRequested invoked from the trash button, which appears only on the user's own
 *   comments. The Fragment shows the confirmation dialog, because a dialog needs a Fragment.
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
            binding.commentAuthor.text = comment.authorName
            binding.commentAvatar.loadAvatar(comment.authorPhotoUrl)
            binding.commentText.text = comment.text
            binding.commentTime.text = binding.root.context.workoutTimeText(comment.createdAt)

            // The name and the avatar open that user's profile - not the whole row. A comment is a
            // block of text people scroll past and brush against; making all of it a navigation
            // target would send them somewhere they did not ask to go.
            val openAuthor = View.OnClickListener { onAuthorClick(comment.authorId) }
            binding.commentAuthor.setOnClickListener(openAuthor)
            binding.commentAvatar.setOnClickListener(openAuthor)

            // Deleting is a visible button, and only on your own comment. It used to be a long
            // press, which nothing on the row advertised - a gesture a user has to already know
            // about is not a feature they have.
            //
            // Both the visibility and the listener are set unconditionally, because views are
            // recycled: a row that only *added* the button and its listener would hand somebody
            // else's comment a delete control inherited from the row before it.
            val isMine = currentUserId != null && currentUserId == comment.authorId
            binding.commentDelete.isVisible = isMine
            binding.commentDelete.setOnClickListener(
                if (isMine) {
                    View.OnClickListener { onDeleteRequested(comment) }
                } else {
                    null
                }
            )
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
