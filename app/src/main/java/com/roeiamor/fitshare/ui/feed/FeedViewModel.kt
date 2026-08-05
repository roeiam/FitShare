package com.roeiamor.fitshare.ui.feed

import androidx.lifecycle.LiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.asLiveData
import com.roeiamor.fitshare.data.model.FeedSort
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.data.repository.WorkoutRepository
import com.roeiamor.fitshare.util.ErrorMapper
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onStart

/**
 * The parts of the feed that change the Firestore query.
 *
 * A change to any of these means a new listener, which is why they are grouped: one value changing
 * gives one re-subscription instead of several.
 *
 * @property refreshToken bumped by [FeedViewModel.refresh] purely to force a new query. The
 *   listener is already live, so pull to refresh is about giving the user a way to retry after an
 *   error and a sense of control - not about fetching data that would not arrive anyway.
 */
private data class FeedQuery(
    val category: WorkoutCategory? = null,
    val sort: FeedSort = FeedSort.NEWEST,
    val refreshToken: Int = 0
)

/**
 * Drives the feed: watches Firestore, applies the category filter, the search text and the sort,
 * and publishes one [FeedUiState].
 *
 * The repository returns `Flow<Result<T>>`; everything is combined here and the chain ends with
 * `.asLiveData()`, so the Fragment only ever sees `LiveData` (decision PHASE0_PLAN.md section 4.G).
 *
 * @param workoutRepository the interface; this class has no idea Firestore is behind it.
 */
class FeedViewModel(private val workoutRepository: WorkoutRepository) : ViewModel() {

    private val query = MutableStateFlow(FeedQuery())
    private val searchText = MutableStateFlow("")

    /**
     * What the screen should currently look like.
     *
     * `flatMapLatest` is what makes changing a chip correct: it cancels the collection of the
     * previous query - which removes the old Firestore listener through `awaitClose` - before
     * subscribing to the new one. Without it, tapping four chips would leave four listeners running.
     *
     * The search text is combined rather than folded into the query because Firestore cannot do
     * substring matching; searching is a filter over what has already arrived. That is also why
     * pagination was dropped (PHASE0_PLAN.md section 4.E) - with only part of the feed loaded, a
     * search could miss a workout that demonstrably exists.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    val uiState: LiveData<FeedUiState> = query
        .flatMapLatest { current -> workoutRepository.observeFeed(current.category, current.sort) }
        .combine(searchText) { result, search -> toUiState(result, search) }
        .onStart { emit(FeedUiState.Loading) }
        .asLiveData()

    /** Called when the user picks a category chip. Null means "הכל". */
    fun onCategorySelected(category: WorkoutCategory?) {
        query.value = query.value.copy(category = category)
    }

    /**
     * Called when the user picks a sort chip.
     *
     * Goes into [FeedQuery] rather than being applied to the loaded list, because ordering is the
     * server's job: sorting locally would only order the workouts that happen to have arrived.
     */
    fun onSortSelected(sort: FeedSort) {
        query.value = query.value.copy(sort = sort)
    }

    /** Called on every keystroke in the search field. */
    fun onSearchChanged(text: String) {
        searchText.value = text
    }

    /** Called by pull to refresh, and by the retry button on the error state. */
    fun refresh() {
        query.value = query.value.copy(refreshToken = query.value.refreshToken + 1)
    }

    /** Turns one emission plus the current search text into a state the Fragment can render. */
    private fun toUiState(result: Result<List<Workout>>, search: String): FeedUiState =
        result.fold(
            onSuccess = { workouts ->
                val visible = workouts.filter { it.matches(search) }
                when {
                    visible.isNotEmpty() -> FeedUiState.Content(visible)
                    // Distinguishes "nothing published yet" from "your filter matched nothing".
                    workouts.isNotEmpty() || search.isNotBlank() -> FeedUiState.Empty(isFiltered = true)
                    else -> FeedUiState.Empty(isFiltered = false)
                }
            },
            onFailure = { FeedUiState.Error(ErrorMapper.toMessageRes(it)) }
        )

    /**
     * Matches a workout against the search box: title or author name, case-insensitively
     * (SPEC feature 7). An empty search matches everything.
     */
    private fun Workout.matches(search: String): Boolean {
        val needle = search.trim()
        if (needle.isEmpty()) return true
        return title.contains(needle, ignoreCase = true) ||
            authorName.contains(needle, ignoreCase = true)
    }
}
