package com.roeiamor.fitshare.data.repository

import com.roeiamor.fitshare.data.model.FeedSort
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
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
}

/**
 * The only implementation.
 *
 * It is deliberately thin. There is no caching or mapping to do here yet - the data source already
 * returns domain models and already wraps failures - so adding a layer of forwarding that pretended
 * otherwise would be noise. It exists so ViewModels depend on an interface rather than on Firestore.
 *
 * @param workoutDataSource the only thing that knows Firestore exists.
 */
class WorkoutRepositoryImpl(
    private val workoutDataSource: WorkoutDataSource
) : WorkoutRepository {

    override fun observeFeed(
        category: WorkoutCategory?,
        sort: FeedSort
    ): Flow<Result<List<Workout>>> = workoutDataSource.observeFeed(category, sort)

    override suspend fun getWorkout(id: String): Result<Workout?> =
        workoutDataSource.getWorkout(id)
}
