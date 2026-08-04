package com.roeiamor.fitshare.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.roeiamor.fitshare.data.model.Comment
import com.roeiamor.fitshare.data.model.FavoriteWorkout
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.util.safeCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * Likes, comments and favourites: everything that changes a workout without editing it.
 *
 * The counter rule from SPEC section 3 lives here. `likesCount` and `commentsCount` are never
 * touched on their own - each one moves inside the same transaction as the subcollection document
 * that justifies it.
 *
 * **Why a transaction rather than two writes.** The like document and the counter are two different
 * documents. As two separate calls, any failure between them - the process dying, the network
 * dropping, the user force-closing the app - leaves them disagreeing: a like that exists but was
 * never counted, or a count with no like behind it. Nothing would ever repair it, because the
 * counter only moves by a delta and no later write knows the earlier one was lost. And because the
 * like document is what decides whether the heart shows as filled, the user would see a filled heart
 * next to a number that does not include them.
 *
 * A transaction also fixes something two writes cannot: **rapid repeated tapping**. Firestore reruns
 * the transaction body when the document changes underneath it, so the read of "is it already
 * liked?" and the write are decided together. Two independent taps cannot both read "not liked" and
 * both increment.
 *
 * @param firestore the shared instance created in the ServiceLocator.
 */
class InteractionDataSource(private val firestore: FirebaseFirestore) {

    // ---- Likes -------------------------------------------------------------------------------

    /**
     * Toggles the current user's like, and moves `likesCount` with it, atomically.
     *
     * The transaction reads the like document first and decides from that, so the result does not
     * depend on what the UI believed. Tapping twice quickly cannot double-count: the second
     * transaction sees the first one's write and takes the other branch.
     *
     * @return true if the workout is liked after this call, false if the like was removed.
     */
    suspend fun toggleLike(workoutId: String, userId: String): Result<Boolean> = safeCall {
        val workoutRef = workoutDocument(workoutId)
        val likeRef = workoutRef.collection(COLLECTION_LIKES).document(userId)

        firestore.runTransaction { transaction ->
            val alreadyLiked = transaction.get(likeRef).exists()

            if (alreadyLiked) {
                transaction.delete(likeRef)
                transaction.update(workoutRef, FIELD_LIKES_COUNT, FieldValue.increment(-1))
            } else {
                transaction.set(
                    likeRef,
                    mapOf(FIELD_UID to userId, FIELD_CREATED_AT to FieldValue.serverTimestamp())
                )
                transaction.update(workoutRef, FIELD_LIKES_COUNT, FieldValue.increment(1))
            }

            !alreadyLiked
        }.await()
    }

    /**
     * Watches whether the current user has liked this workout.
     *
     * A listener rather than a one-off read so the heart is correct even when the same account is
     * open on another device.
     */
    fun observeIsLiked(workoutId: String, userId: String): Flow<Result<Boolean>> = callbackFlow {
        val registration = workoutDocument(workoutId)
            .collection(COLLECTION_LIKES)
            .document(userId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                trySend(Result.success(snapshot?.exists() == true))
            }

        awaitClose { registration.remove() }
    }

    // ---- Comments ----------------------------------------------------------------------------

    /** Watches a workout's comments, oldest first, so a conversation reads top to bottom. */
    fun observeComments(workoutId: String): Flow<Result<List<Comment>>> = callbackFlow {
        val registration = workoutDocument(workoutId)
            .collection(COLLECTION_COMMENTS)
            .orderBy(FIELD_CREATED_AT, Query.Direction.ASCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                val comments = snapshot?.documents.orEmpty().mapNotNull { document ->
                    document.toObject(Comment::class.java)
                }
                trySend(Result.success(comments))
            }

        awaitClose { registration.remove() }
    }

    /** Adds a comment and increments `commentsCount` in the same transaction. */
    suspend fun addComment(workoutId: String, comment: Comment): Result<Unit> = safeCall {
        val workoutRef = workoutDocument(workoutId)
        val commentRef = workoutRef.collection(COLLECTION_COMMENTS).document()

        firestore.runTransaction { transaction ->
            transaction.set(commentRef, comment.copy(id = commentRef.id))
            transaction.update(workoutRef, FIELD_COMMENTS_COUNT, FieldValue.increment(1))
        }.await()
    }

    /**
     * Deletes a comment and decrements `commentsCount` in the same transaction.
     *
     * The delete is conditional on the document still existing, so deleting the same comment twice -
     * two taps, or two devices - cannot drive the counter below the number of comments.
     */
    suspend fun deleteComment(workoutId: String, commentId: String): Result<Unit> = safeCall {
        val workoutRef = workoutDocument(workoutId)
        val commentRef = workoutRef.collection(COLLECTION_COMMENTS).document(commentId)

        firestore.runTransaction { transaction ->
            if (!transaction.get(commentRef).exists()) return@runTransaction

            transaction.delete(commentRef)
            transaction.update(workoutRef, FIELD_COMMENTS_COUNT, FieldValue.increment(-1))
        }.await()
    }

    // ---- Favourites --------------------------------------------------------------------------

    /**
     * Toggles the workout in the user's favourites.
     *
     * No counter is involved, so no transaction is needed - but the read and the write still go
     * together to keep the toggle correct under rapid tapping.
     *
     * Saving writes a denormalized snapshot (SPEC section 3) so the Favorites screen renders from
     * one query.
     *
     * @return true if the workout is saved after this call.
     */
    suspend fun toggleFavorite(workout: Workout, userId: String): Result<Boolean> = safeCall {
        val favoriteRef = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_FAVORITES)
            .document(workout.id)

        firestore.runTransaction { transaction ->
            val alreadySaved = transaction.get(favoriteRef).exists()

            if (alreadySaved) {
                transaction.delete(favoriteRef)
            } else {
                transaction.set(
                    favoriteRef,
                    FavoriteWorkout(
                        workoutId = workout.id,
                        title = workout.title,
                        imageUrl = workout.imageUrl,
                        authorName = workout.authorName,
                        category = workout.category
                    )
                )
            }

            !alreadySaved
        }.await()
    }

    /** Watches whether this workout is in the user's favourites. */
    fun observeIsFavorite(workoutId: String, userId: String): Flow<Result<Boolean>> = callbackFlow {
        val registration = firestore.collection(COLLECTION_USERS)
            .document(userId)
            .collection(COLLECTION_FAVORITES)
            .document(workoutId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                trySend(Result.success(snapshot?.exists() == true))
            }

        awaitClose { registration.remove() }
    }

    private fun workoutDocument(workoutId: String) =
        firestore.collection(COLLECTION_WORKOUTS).document(workoutId)

    private companion object {
        const val COLLECTION_WORKOUTS = "workouts"
        const val COLLECTION_USERS = "users"
        const val COLLECTION_LIKES = "likes"
        const val COLLECTION_COMMENTS = "comments"
        const val COLLECTION_FAVORITES = "favorites"

        const val FIELD_LIKES_COUNT = "likesCount"
        const val FIELD_COMMENTS_COUNT = "commentsCount"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_UID = "uid"
    }
}
