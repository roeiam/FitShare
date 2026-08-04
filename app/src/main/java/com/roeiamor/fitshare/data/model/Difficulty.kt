package com.roeiamor.fitshare.data.model

/**
 * How hard a workout is (SPEC section 4).
 *
 * Plain for the same reason as [WorkoutCategory]: labels live in the UI layer, never here.
 */
enum class Difficulty {
    EASY,
    MEDIUM,
    HARD;

    companion object {

        /**
         * Resolves the constant a Firestore document stores as a string.
         * Falls back to [MEDIUM] so an unexpected value cannot crash the feed.
         */
        fun fromName(name: String?): Difficulty =
            entries.firstOrNull { it.name == name } ?: MEDIUM
    }
}
