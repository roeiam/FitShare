package com.roeiamor.fitshare.data.remote

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.roeiamor.fitshare.util.safeCall
import kotlinx.coroutines.tasks.await

/**
 * The only class that talks to Firebase Authentication.
 *
 * Every call returns a `Result`, so nothing above this layer has to handle a thrown exception, and
 * nothing above this layer needs to know Firebase exists. `await()` turns Firebase's callback-based
 * `Task<T>` into a suspending call, which is what keeps the repository readable.
 *
 * @param firebaseAuth the shared instance created in the ServiceLocator.
 */
class AuthDataSource(private val firebaseAuth: FirebaseAuth) {

    /** The signed-in user, or null. Read synchronously - Firebase caches the session locally. */
    val currentUser: FirebaseUser? get() = firebaseAuth.currentUser

    /** The signed-in user's uid, or null when nobody is signed in. */
    val currentUserId: String? get() = firebaseAuth.currentUser?.uid

    /**
     * Creates an account.
     *
     * @return the new [FirebaseUser], or a failure - most often
     *   `FirebaseAuthUserCollisionException` when the address is already registered.
     */
    suspend fun signUp(email: String, password: String): Result<FirebaseUser> = safeCall {
        val result = firebaseAuth.createUserWithEmailAndPassword(email, password).await()
        checkNotNull(result.user) { "Firebase returned no user after a successful sign-up" }
    }

    /**
     * Signs in with an existing account.
     *
     * @return the signed-in [FirebaseUser], or a failure - most often
     *   `FirebaseAuthInvalidCredentialsException` when the password is wrong.
     */
    suspend fun signIn(email: String, password: String): Result<FirebaseUser> = safeCall {
        val result = firebaseAuth.signInWithEmailAndPassword(email, password).await()
        checkNotNull(result.user) { "Firebase returned no user after a successful sign-in" }
    }

    /**
     * Sends a password reset email.
     *
     * Firebase reports success even for an address that has no account, so that a stranger cannot
     * use this screen to discover which addresses are registered. The UI says "if the address exists
     * we sent a link" for the same reason.
     */
    suspend fun sendPasswordReset(email: String): Result<Unit> = safeCall {
        firebaseAuth.sendPasswordResetEmail(email).await()
    }

    /**
     * Deletes the currently signed-in account.
     *
     * Used only to roll back a half-finished registration - see `AuthRepositoryImpl.register`.
     * Firebase requires a recent sign-in for this, which a just-created account satisfies.
     */
    suspend fun deleteCurrentUser(): Result<Unit> = safeCall {
        val user = checkNotNull(firebaseAuth.currentUser) { "No signed-in user to delete" }
        user.delete().await()
    }

    /** Clears the local session. Synchronous, and cannot fail. */
    fun signOut() {
        firebaseAuth.signOut()
    }
}
