package com.roeiamor.fitshare.data.remote

import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.roeiamor.fitshare.data.model.FeedSort
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.util.safeCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The only class that reads the `workouts` collection.
 *
 * @param firestore the shared instance created in the ServiceLocator.
 */
class WorkoutDataSource(private val firestore: FirebaseFirestore) {

    private val workoutsCollection get() = firestore.collection(COLLECTION_WORKOUTS)

    /**
     * Watches the feed and emits a new list every time the server data changes.
     *
     * `callbackFlow` is what bridges Firestore's listener API to a coroutine `Flow`, and the
     * **`awaitClose` block is the important line**: it removes the registration when the collector
     * stops. A Firestore listener is not garbage collected while it is registered - it holds a live
     * connection and keeps billing reads - so without that removal every visit to the feed would
     * leave another listener running for the life of the process.
     *
     * Collection is tied to the ViewModel's scope, so the listener goes away with the ViewModel.
     *
     * Errors are emitted as `Result.failure` rather than thrown, because throwing inside a
     * `callbackFlow` would cancel the flow and the screen could never recover; as a failure the
     * ViewModel can show the error state and the user can retry.
     *
     * @param category filter to one category, or null for everything.
     * @param sort ordering; see [FeedSort].
     */
    fun observeFeed(category: WorkoutCategory?, sort: FeedSort): Flow<Result<List<Workout>>> =
        callbackFlow {
            val registration = buildFeedQuery(category, sort)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        trySend(Result.failure(error))
                        return@addSnapshotListener
                    }
                    val workouts = snapshot?.documents.orEmpty().mapNotNull { document ->
                        document.toObject(Workout::class.java)
                    }
                    trySend(Result.success(workouts))
                }

            awaitClose { registration.remove() }
        }

    /**
     * Reads one workout.
     *
     * @return the [Workout], or null when the document does not exist - a deleted workout is a
     *   normal outcome, not an error.
     */
    suspend fun getWorkout(id: String): Result<Workout?> = safeCall {
        workoutsCollection.document(id).get().await().toObject(Workout::class.java)
    }

    /**
     * Publishes a workout and bumps its author's `workoutsCount`, **atomically**.
     *
     * Both writes go in one [com.google.firebase.firestore.WriteBatch]: either the workout appears
     * and the counter moves, or neither happens. Done as two separate writes, a failure between them
     * leaves the profile claiming a number of workouts that does not match the feed - and nothing
     * would ever correct it, because the counter is only ever adjusted by a delta. This is the
     * counter rule in SPEC section 3.
     *
     * A batch rather than a transaction because nothing has to be *read* first: the document is new
     * and the counter moves by a fixed delta through `FieldValue.increment`, which the server
     * applies without the client needing to know the current value. A transaction would add a read
     * and a retry loop for no benefit.
     *
     * The document id is generated locally before the write so it can be mirrored into the document
     * itself, which is what lets a feed card know its own id without a second lookup.
     */
    suspend fun createWorkout(workout: Workout): Result<Unit> = safeCall {
        val workoutRef = workoutsCollection.document()
        val authorRef = firestore.collection(COLLECTION_USERS).document(workout.authorId)

        firestore.batch()
            .set(workoutRef, workout.copy(id = workoutRef.id))
            .update(authorRef, FIELD_WORKOUTS_COUNT, FieldValue.increment(1))
            .commit()
            .await()
    }

    /**
     * Builds the feed query.
     *
     * Filtering by category *and* ordering by `createdAt` needs a composite index - Firestore
     * prints a one-click creation link the first time the query runs (SPEC section 10).
     */
    private fun buildFeedQuery(category: WorkoutCategory?, sort: FeedSort): Query {
        val filtered = if (category == null) {
            workoutsCollection
        } else {
            workoutsCollection.whereEqualTo(FIELD_CATEGORY, category.name)
        }

        return when (sort) {
            FeedSort.NEWEST -> filtered.orderBy(FIELD_CREATED_AT, Query.Direction.DESCENDING)
            FeedSort.MOST_LIKED -> filtered.orderBy(FIELD_LIKES_COUNT, Query.Direction.DESCENDING)
        }
    }

    private companion object {
        const val COLLECTION_WORKOUTS = "workouts"

        /**
         * The batch in [createWorkout] has to touch the author's document too, which is the one
         * place this class reaches outside its own collection. Atomicity requires it: the two
         * writes cannot be split across two data sources and still be one batch.
         */
        const val COLLECTION_USERS = "users"

        const val FIELD_CATEGORY = "category"
        const val FIELD_CREATED_AT = "createdAt"
        const val FIELD_LIKES_COUNT = "likesCount"
        const val FIELD_WORKOUTS_COUNT = "workoutsCount"
    }
}
