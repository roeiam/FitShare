package com.roeiamor.fitshare.data.repository

import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.data.model.FavoriteWorkout
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.remote.AuthDataSource
import com.roeiamor.fitshare.data.remote.InteractionDataSource
import com.roeiamor.fitshare.data.remote.UserDataSource
import com.roeiamor.fitshare.util.NetworkGuard
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

/**
 * Likes, comments and favourites for the signed-in user.
 *
 * Separate from [WorkoutRepository] because these are things a user *does to* a workout rather than
 * things a workout *is*. Keeping them apart also keeps each interface small enough to read in one
 * go, which is the point of the repository layer here.
 *
 * Every method works on behalf of the current user; callers never pass a uid, so no screen can
 * accidentally like something as somebody else.
 */
interface InteractionRepository {

    /** Watches whether the current user has liked this workout. */
    fun observeIsLiked(workoutId: String): Flow<Result<Boolean>>

    /** Watches whether the current user has saved this workout. */
    fun observeIsFavorite(workoutId: String): Flow<Result<Boolean>>

    /** Watches a workout's comments, oldest first. */
    fun observeComments(workoutId: String): Flow<Result<List<Comment>>>

    /** Likes or unlikes. Returns the state afterwards. */
    suspend fun toggleLike(workoutId: String): Result<Boolean>

    /** Saves or unsaves. Returns the state afterwards. */
    suspend fun toggleFavorite(workout: Workout): Result<Boolean>

    /** Posts a comment as the current user. */
    suspend fun addComment(workoutId: String, text: String): Result<Unit>

    /** Deletes a comment. Only the author's own comments are offered for deletion in the UI. */
    suspend fun deleteComment(workoutId: String, commentId: String): Result<Unit>

    /** Watches everything the signed-in user has saved, most recently saved first. */
    fun observeFavorites(): Flow<Result<List<FavoriteWorkout>>>

    /** Removes one saved workout, by id. Used by the long press on the Favorites screen. */
    suspend fun removeFavorite(workoutId: String): Result<Unit>

    /** The signed-in user's uid, so a screen can tell which comments are its own. */
    val currentUserId: String?
}

/**
 * The only implementation.
 *
 * @param interactionDataSource the transactions that keep the counters honest.
 * @param userDataSource supplies the author snapshot denormalized onto a new comment.
 * @param authDataSource identifies the current user.
 */
class InteractionRepositoryImpl(
    private val interactionDataSource: InteractionDataSource,
    private val userDataSource: UserDataSource,
    private val authDataSource: AuthDataSource,
    private val networkGuard: NetworkGuard
) : InteractionRepository {

    override val currentUserId: String? get() = authDataSource.currentUserId

    /**
     * Emits false rather than an error when nobody is signed in.
     *
     * A signed-out user cannot have liked anything, and "not liked" is the truthful answer. Failing
     * here would put the details screen into its error state for a question that has an obvious
     * answer.
     */
    override fun observeIsLiked(workoutId: String): Flow<Result<Boolean>> {
        val userId = currentUserId ?: return flowOf(Result.success(false))
        return interactionDataSource.observeIsLiked(workoutId, userId)
    }

    override fun observeIsFavorite(workoutId: String): Flow<Result<Boolean>> {
        val userId = currentUserId ?: return flowOf(Result.success(false))
        return interactionDataSource.observeIsFavorite(workoutId, userId)
    }

    override fun observeComments(workoutId: String): Flow<Result<List<Comment>>> =
        interactionDataSource.observeComments(workoutId)

    override suspend fun toggleLike(workoutId: String): Result<Boolean> {
        val userId = currentUserId ?: return Result.failure(notSignedIn())
        return networkGuard.run { interactionDataSource.toggleLike(workoutId, userId) }
    }

    override suspend fun toggleFavorite(workout: Workout): Result<Boolean> {
        val userId = currentUserId ?: return Result.failure(notSignedIn())
        return networkGuard.run { interactionDataSource.toggleFavorite(workout, userId) }
    }

    /**
     * Posts a comment with the author's name and photo copied onto it.
     *
     * Denormalized for the same reason as on a workout: the thread renders from one query.
     */
    override suspend fun addComment(workoutId: String, text: String): Result<Unit> =
        networkGuard.run { addCommentInternal(workoutId, text) }

    /** Separate from the public method so [NetworkGuard] can wrap its early returns. */
    private suspend fun addCommentInternal(workoutId: String, text: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(notSignedIn())
        val author = userDataSource.getUser(userId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalStateException("No profile document for $userId"))

        val comment = Comment(
            authorId = author.uid,
            authorName = author.displayName,
            authorPhotoUrl = author.photoUrl,
            text = text.trim()
        )
        return interactionDataSource.addComment(workoutId, comment)
    }

    override suspend fun deleteComment(workoutId: String, commentId: String): Result<Unit> =
        networkGuard.run { interactionDataSource.deleteComment(workoutId, commentId) }

    /**
     * Emits an empty list rather than an error when nobody is signed in, for the same reason
     * [observeIsLiked] emits false: a signed-out user has no favourites, which is an answer, not a
     * failure.
     */
    override fun observeFavorites(): Flow<Result<List<FavoriteWorkout>>> {
        val userId = currentUserId ?: return flowOf(Result.success(emptyList()))
        return interactionDataSource.observeFavorites(userId)
    }

    override suspend fun removeFavorite(workoutId: String): Result<Unit> {
        val userId = currentUserId ?: return Result.failure(notSignedIn())
        return networkGuard.run { interactionDataSource.removeFavorite(userId, workoutId) }
    }

    private fun notSignedIn() = IllegalStateException("This action requires a signed-in user")
}
