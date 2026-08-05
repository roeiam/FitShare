package com.roeiamor.fitshare.data.repository

import android.net.Uri
import com.roeiamor.fitshare.data.model.User
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.remote.AuthDataSource
import com.roeiamor.fitshare.data.remote.ImageUploader
import com.roeiamor.fitshare.data.remote.UserDataSource
import com.roeiamor.fitshare.data.remote.WorkoutDataSource
import com.roeiamor.fitshare.util.FIRESTORE_TIMEOUT_MS
import com.roeiamor.fitshare.util.NetworkGuard
import com.roeiamor.fitshare.util.UPLOAD_TIMEOUT_MS
import kotlinx.coroutines.flow.Flow

/**
 * Profiles: reading one, and editing your own.
 *
 * Separate from [AuthRepository], which owns *sessions* - creating an account, signing in and out.
 * Once you are signed in, your name, bio and avatar are ordinary profile data that a screen with no
 * interest in authentication needs to read, so they live here.
 */
interface UserRepository {

    /** The signed-in user's uid, so a screen can tell its own profile from someone else's. */
    val currentUserId: String?

    /** Watches one user's profile document. Emits null when there is no such user. */
    fun observeUser(uid: String): Flow<Result<User?>>

    /** Watches everything one user has published, newest first. Drives the profile grid. */
    fun observeUserWorkouts(uid: String): Flow<Result<List<Workout>>>

    /**
     * Saves the signed-in user's profile.
     *
     * @param displayName the new name.
     * @param bio the new bio; may be empty.
     * @param avatarUri a newly picked photo, or null to keep the current avatar.
     */
    suspend fun updateProfile(displayName: String, bio: String, avatarUri: Uri?): Result<Unit>
}

/**
 * The only implementation.
 *
 * @param userDataSource reads and writes `users/{uid}`.
 * @param workoutDataSource supplies the grid, and rewrites the denormalized author on an edit.
 * @param authDataSource identifies the signed-in user.
 * @param imageUploader compresses and uploads a new avatar.
 * @param networkGuard fails fast offline and bounds every call.
 */
class UserRepositoryImpl(
    private val userDataSource: UserDataSource,
    private val workoutDataSource: WorkoutDataSource,
    private val authDataSource: AuthDataSource,
    private val imageUploader: ImageUploader,
    private val networkGuard: NetworkGuard
) : UserRepository {

    override val currentUserId: String? get() = authDataSource.currentUserId

    override fun observeUser(uid: String): Flow<Result<User?>> = userDataSource.observeUser(uid)

    override fun observeUserWorkouts(uid: String): Flow<Result<List<Workout>>> =
        workoutDataSource.observeUserWorkouts(uid)

    /**
     * Saves the profile, then repairs every copy of the author that is now out of date.
     *
     * The upload timeout applies only when there is actually an avatar to upload; otherwise this is
     * two plain Firestore writes and should fail in fifteen seconds, not two minutes.
     */
    override suspend fun updateProfile(
        displayName: String,
        bio: String,
        avatarUri: Uri?
    ): Result<Unit> {
        val timeout = if (avatarUri == null) FIRESTORE_TIMEOUT_MS else UPLOAD_TIMEOUT_MS
        return networkGuard.run(timeout) { updateProfileInternal(displayName, bio, avatarUri) }
    }

    /**
     * The real save, kept separate so [NetworkGuard] can wrap its early returns - it takes a
     * non-inline suspend block, which a `return` cannot cross.
     *
     * **The second write is the interesting one.** `authorName` and `authorPhotoUrl` are copied onto
     * every workout when it is published, so the feed renders from a single query (SPEC section 3).
     * The cost of that choice is paid here: renaming yourself has to rewrite those copies, or the
     * feed keeps showing your old name forever. They go out as one `WriteBatch`, so the feed can
     * never show a mixture of the old name and the new one.
     *
     * If the profile saves but the rewrite fails, the failure is reported and the user can save
     * again - the second attempt rewrites the same workouts, because the operation is idempotent.
     */
    private suspend fun updateProfileInternal(
        displayName: String,
        bio: String,
        avatarUri: Uri?
    ): Result<Unit> {
        val userId = currentUserId
            ?: return Result.failure(IllegalStateException("Editing a profile requires a session"))

        val existing = userDataSource.getUser(userId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalStateException("No profile document for $userId"))

        val photoUrl = if (avatarUri == null) {
            existing.photoUrl
        } else {
            imageUploader.upload(avatarUri).getOrElse { return Result.failure(it) }
        }

        val name = displayName.trim()
        val fields = mapOf(
            FIELD_DISPLAY_NAME to name,
            FIELD_BIO to bio.trim(),
            FIELD_PHOTO_URL to photoUrl
        )
        userDataSource.updateUser(userId, fields).getOrElse { return Result.failure(it) }

        // Nothing to repair when neither the name nor the avatar actually changed.
        if (name == existing.displayName && photoUrl == existing.photoUrl) {
            return Result.success(Unit)
        }

        return workoutDataSource.updateDenormalizedAuthor(userId, name, photoUrl).map { }
    }

    private companion object {
        const val FIELD_DISPLAY_NAME = "displayName"
        const val FIELD_BIO = "bio"
        const val FIELD_PHOTO_URL = "photoUrl"
    }
}
