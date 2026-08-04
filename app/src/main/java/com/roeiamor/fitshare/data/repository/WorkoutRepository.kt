package com.roeiamor.fitshare.data.repository

import android.net.Uri
import com.roeiamor.fitshare.data.model.FeedSort
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.data.model.WorkoutDraft
import com.roeiamor.fitshare.data.remote.AuthDataSource
import com.roeiamor.fitshare.data.remote.ImageUploader
import com.roeiamor.fitshare.data.remote.UserDataSource
import com.roeiamor.fitshare.data.remote.WorkoutDataSource
import kotlinx.coroutines.flow.Flow

/**
 * Everything the app can read about workouts.
 *
 * ViewModels depend on this interface, never on `FirebaseFirestore`, which is what lets a fake stand
 * in for it in tests.
 *
 * Live data comes back as `Flow<Result<T>>`, not `LiveData` (decision PHASE0_PLAN.md section 4.G):
 * the feed has to merge one live stream with three pieces of screen state, and doing that in
 * `LiveData` needs nested `MediatorLiveData`. The ViewModel combines and finishes with
 * `.asLiveData()`, so the Fragment still only ever sees `LiveData`.
 */
interface WorkoutRepository {

    /**
     * Watches the feed. Emits again on every server-side change, so the feed updates live.
     *
     * @param category filter to one category, or null for everything.
     * @param sort ordering.
     */
    fun observeFeed(category: WorkoutCategory?, sort: FeedSort): Flow<Result<List<Workout>>>

    /** Reads one workout; null when it no longer exists. */
    suspend fun getWorkout(id: String): Result<Workout?>

    /**
     * Publishes a workout: uploads the photo if there is one, denormalizes the author onto the
     * document, and writes it together with the author's `workoutsCount`.
     *
     * @param draft what the user filled in.
     * @param imageUri the chosen photo, or null - a workout without one is valid.
     */
    suspend fun createWorkout(draft: WorkoutDraft, imageUri: Uri?): Result<Unit>

    /** Watches one workout, so the details screen reflects likes and comments as they happen. */
    fun observeWorkout(id: String): Flow<Result<Workout?>>

    /**
     * Deletes a workout and decrements its author's counter.
     *
     * @param workout the workout to delete; the author is taken from it rather than from the
     *   session, so the two can be compared before anything is written.
     */
    suspend fun deleteWorkout(workout: Workout): Result<Unit>

    /** Updates the fields the add/edit form owns, leaving counters and timestamps alone. */
    suspend fun updateWorkout(workoutId: String, draft: WorkoutDraft, imageUri: Uri?): Result<Unit>
}

/**
 * The only implementation.
 *
 * Reading is a straight pass-through - the data source already returns domain models and already
 * wraps failures. Publishing is not: it spans three collaborators, and coordinating them is exactly
 * what a repository is for, which keeps that orchestration out of the ViewModel.
 *
 * @param workoutDataSource reads and writes the `workouts` collection.
 * @param userDataSource supplies the author's profile for denormalization.
 * @param authDataSource identifies who is publishing.
 * @param imageUploader compresses and uploads the photo.
 */
class WorkoutRepositoryImpl(
    private val workoutDataSource: WorkoutDataSource,
    private val userDataSource: UserDataSource,
    private val authDataSource: AuthDataSource,
    private val imageUploader: ImageUploader
) : WorkoutRepository {

    override fun observeFeed(
        category: WorkoutCategory?,
        sort: FeedSort
    ): Flow<Result<List<Workout>>> = workoutDataSource.observeFeed(category, sort)

    override suspend fun getWorkout(id: String): Result<Workout?> =
        workoutDataSource.getWorkout(id)

    override fun observeWorkout(id: String): Flow<Result<Workout?>> =
        workoutDataSource.observeWorkout(id)

    /**
     * Refuses to delete somebody else's workout.
     *
     * The security rules enforce this on the server too, and the UI only offers delete to the owner
     * - but a check here means a bug in either of those cannot turn into a lost document, and the
     * failure is a clear message rather than a permission error from Firestore.
     */
    override suspend fun deleteWorkout(workout: Workout): Result<Unit> {
        val userId = authDataSource.currentUserId
        if (userId == null || userId != workout.authorId) {
            return Result.failure(IllegalStateException("Only the author can delete a workout"))
        }
        return workoutDataSource.deleteWorkout(workout.id, workout.authorId)
    }

    /**
     * Applies an edit.
     *
     * A new photo is uploaded first, exactly as on create. When [imageUri] is null the existing
     * `imageUrl` is left untouched - the field is simply not part of the update - so editing the
     * title of a workout does not silently remove its photo.
     */
    override suspend fun updateWorkout(
        workoutId: String,
        draft: WorkoutDraft,
        imageUri: Uri?
    ): Result<Unit> {
        val fields = mutableMapOf<String, Any?>(
            FIELD_TITLE to draft.title.trim(),
            FIELD_DESCRIPTION to draft.description.trim(),
            FIELD_CATEGORY to draft.category.name,
            FIELD_DURATION to draft.durationMinutes,
            FIELD_DIFFICULTY to draft.difficulty.name
        )

        if (imageUri != null) {
            val imageUrl = imageUploader.upload(imageUri).getOrElse { return Result.failure(it) }
            fields[FIELD_IMAGE_URL] = imageUrl
        }

        return workoutDataSource.updateWorkout(workoutId, fields)
    }

    private companion object {
        const val FIELD_TITLE = "title"
        const val FIELD_DESCRIPTION = "description"
        const val FIELD_CATEGORY = "category"
        const val FIELD_DURATION = "durationMinutes"
        const val FIELD_DIFFICULTY = "difficulty"
        const val FIELD_IMAGE_URL = "imageUrl"
    }

    /**
     * Publishing, in four steps that stop at the first failure.
     *
     * The image is uploaded **before** the document is written. Doing it the other way round would
     * put a workout in everyone's feed with no photo and then try to patch it - and a failure there
     * would leave that hole permanently. Uploading first means the only thing a failure costs is an
     * orphaned image in Cloudinary, which nobody sees.
     *
     * The author's name and avatar are copied onto the workout on purpose (SPEC section 3): the feed
     * renders from one query, and looking the author up per card would turn a 20-card feed into 21
     * reads.
     */
    override suspend fun createWorkout(draft: WorkoutDraft, imageUri: Uri?): Result<Unit> {
        val authorId = authDataSource.currentUserId
            ?: return Result.failure(IllegalStateException("Publishing requires a signed-in user"))

        val author = userDataSource.getUser(authorId).getOrElse { return Result.failure(it) }
            ?: return Result.failure(IllegalStateException("No profile document for $authorId"))

        val imageUrl = if (imageUri == null) {
            null
        } else {
            imageUploader.upload(imageUri).getOrElse { return Result.failure(it) }
        }

        val workout = Workout(
            authorId = author.uid,
            authorName = author.displayName,
            authorPhotoUrl = author.photoUrl,
            title = draft.title.trim(),
            description = draft.description.trim(),
            category = draft.category.name,
            durationMinutes = draft.durationMinutes,
            difficulty = draft.difficulty.name,
            imageUrl = imageUrl
            // id and createdAt are left to the data source and the server respectively.
        )

        return workoutDataSource.createWorkout(workout)
    }
}
