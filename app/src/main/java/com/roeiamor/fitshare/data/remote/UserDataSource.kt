package com.roeiamor.fitshare.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.util.safeCall
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

/**
 * The only class that reads and writes the `users/{uid}` documents (SPEC section 3).
 *
 * Every call returns a `Result`, so a caller never has to catch anything.
 *
 * @param firestore the shared instance created in the ServiceLocator.
 */
class UserDataSource(private val firestore: FirebaseFirestore) {

    private val usersCollection get() = firestore.collection(COLLECTION_USERS)

    /**
     * Writes the profile document for a newly registered user.
     *
     * The document id is the Auth uid, which is what lets the security rules say "only the owner may
     * write this document" without an extra lookup.
     */
    suspend fun createUser(user: User): Result<Unit> = safeCall {
        usersCollection.document(user.uid).set(user).await()
    }

    /**
     * Loads one user's profile.
     *
     * @return the [User], or null when no document exists for that uid. A missing document is a
     *   normal outcome rather than an error, so it is not reported as a failure.
     */
    suspend fun getUser(uid: String): Result<User?> = safeCall {
        usersCollection.document(uid).get().await().toObject(User::class.java)
    }

    /** Deletes a profile document. Used to roll back a half-finished registration. */
    suspend fun deleteUser(uid: String): Result<Unit> = safeCall {
        usersCollection.document(uid).delete().await()
    }

    /**
     * Watches one user's profile.
     *
     * A listener rather than a one-off read so the profile screen reflects an edit immediately, and
     * so `workoutsCount` moves on screen the moment a workout is published or deleted.
     *
     * Emits null when no document exists, which the screen renders as its empty state rather than an
     * error.
     */
    fun observeUser(uid: String): Flow<Result<User?>> = callbackFlow {
        val registration = usersCollection.document(uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    trySend(Result.failure(error))
                    return@addSnapshotListener
                }
                trySend(Result.success(snapshot?.toObject(User::class.java)))
            }

        awaitClose { registration.remove() }
    }

    /**
     * Updates only the fields the edit-profile form owns.
     *
     * A field-level update rather than a whole-object `set`, for the same reason as on a workout: a
     * `set` would reset `workoutsCount` and `createdAt` to the defaults of whatever object the UI
     * happened to build, silently losing them.
     */
    suspend fun updateUser(uid: String, fields: Map<String, Any?>): Result<Unit> = safeCall {
        usersCollection.document(uid).update(fields).await()
    }

    private companion object {
        const val COLLECTION_USERS = "users"
    }
}
