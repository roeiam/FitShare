package com.roeiamor.fitshare.data.repository

import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.data.remote.AuthDataSource
import com.roeiamor.fitshare.data.remote.UserDataSource

/**
 * Everything the app can do with accounts and sessions.
 *
 * ViewModels depend on this interface, never on `FirebaseAuth`, so the UI layer has no idea which
 * backend is behind it and the repository can be replaced by a fake in tests.
 */
interface AuthRepository {

    /** True when a session already exists. Read at launch to choose the start destination. */
    val isSignedIn: Boolean

    /** The signed-in user's uid, or null. */
    val currentUserId: String?

    /**
     * Registers an account and creates its profile document.
     *
     * @return the new [User], or a failure. Never leaves the caller signed in to a half-created
     *   account - see the implementation for exactly what happens when the second step fails.
     */
    suspend fun register(name: String, email: String, password: String): Result<User>

    /** Signs in and returns the user's profile. */
    suspend fun login(email: String, password: String): Result<User>

    /** Sends a password reset email. Succeeds even for an unregistered address, by design. */
    suspend fun sendPasswordReset(email: String): Result<Unit>

    /** Ends the session. */
    fun logout()
}

/**
 * The only implementation, wiring [AuthDataSource] (Firebase Auth) to [UserDataSource] (Firestore).
 *
 * @param authDataSource creates and ends sessions.
 * @param userDataSource reads and writes `users/{uid}`.
 */
class AuthRepositoryImpl(
    private val authDataSource: AuthDataSource,
    private val userDataSource: UserDataSource
) : AuthRepository {

    override val isSignedIn: Boolean get() = authDataSource.currentUser != null

    override val currentUserId: String? get() = authDataSource.currentUserId

    /**
     * Registration is two writes that must both succeed: the Auth account, then `users/{uid}`.
     *
     * **What happens when the second one fails.** Firebase has no transaction spanning Auth and
     * Firestore, so the account can exist for a moment with no profile document - the classic
     * orphan. Leaving it would give the user an account they can sign into that has no name, no bio
     * and no stats, and a second registration attempt would then fail with "email already in use",
     * which is baffling if you believe your registration failed.
     *
     * So the Auth account is **rolled back**: it is deleted, and the original Firestore error is
     * returned. The user sees why it failed, and the address is free to try again.
     *
     * If that delete *also* fails - offline, most likely - there is nothing further this function
     * can do. It signs out so the app is not left in a half-authenticated state, and still reports
     * the failure. The account survives without a profile, and [login] repairs it on the next
     * successful sign-in. Documented in PROGRESS.md.
     */
    override suspend fun register(name: String, email: String, password: String): Result<User> {
        val signUpResult = authDataSource.signUp(email.trim(), password)
        val firebaseUser = signUpResult.getOrElse { return Result.failure(it) }

        val user = User(
            uid = firebaseUser.uid,
            displayName = name.trim(),
            email = email.trim()
        )

        val profileResult = userDataSource.createUser(user)
        if (profileResult.isSuccess) {
            return Result.success(user)
        }

        val profileError = profileResult.exceptionOrNull() ?: IllegalStateException("Profile write failed")
        rollBackAccount()
        return Result.failure(profileError)
    }

    /**
     * Signs in, then returns the profile document.
     *
     * If the document is missing it is recreated here from the Auth account. That covers the one
     * case [register] could not clean up itself, and means a user can never be stuck signed in to an
     * account with no profile.
     */
    override suspend fun login(email: String, password: String): Result<User> {
        val signInResult = authDataSource.signIn(email.trim(), password)
        val firebaseUser = signInResult.getOrElse { return Result.failure(it) }

        val existing = userDataSource.getUser(firebaseUser.uid).getOrElse { return Result.failure(it) }
        if (existing != null) {
            return Result.success(existing)
        }

        val repaired = User(
            uid = firebaseUser.uid,
            displayName = firebaseUser.displayName.orEmpty(),
            email = firebaseUser.email.orEmpty()
        )
        return userDataSource.createUser(repaired).map { repaired }
    }

    override suspend fun sendPasswordReset(email: String): Result<Unit> =
        authDataSource.sendPasswordReset(email.trim())

    override fun logout() {
        authDataSource.signOut()
    }

    /**
     * Undoes a registration whose profile write failed, so the email address is free again.
     * Falls back to signing out when the account cannot be deleted.
     */
    private suspend fun rollBackAccount() {
        if (authDataSource.deleteCurrentUser().isFailure) {
            authDataSource.signOut()
        }
    }
}
