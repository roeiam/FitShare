package com.roeiamor.fitshare.data.model

/**
 * A workout the user has filled in but not yet published.
 *
 * Separate from [Workout] on purpose: the fields the user actually types are the only ones here.
 * The id, the author, the counters and `createdAt` are decided by the repository and the server, and
 * a draft carrying empty placeholders for them would invite someone to set them from the UI.
 */
data class WorkoutDraft(
    val title: String,
    val description: String,
    val category: WorkoutCategory,
    val durationMinutes: Int,
    val difficulty: Difficulty
)
