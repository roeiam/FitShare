package com.roeiamor.fitshare.data.remote

import com.google.firebase.firestore.FirebaseFirestore
import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.util.safeCall
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

    private companion object {
        const val COLLECTION_USERS = "users"
    }
}
