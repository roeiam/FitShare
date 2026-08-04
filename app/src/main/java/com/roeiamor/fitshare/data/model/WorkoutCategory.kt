package com.roeiamor.fitshare.data.model

/**
 * The kind of workout (SPEC section 4).
 *
 * Deliberately **plain**: no constructor arguments, no `@StringRes`, no `R`, no Android imports.
 * Turning a constant into a Hebrew label happens in exactly one place in the UI layer -
 * `ui/common/EnumLabels.kt` - which is what keeps `data/model` free of Android
 * (decision PHASE0_PLAN.md section 4.C).
 */
enum class WorkoutCategory {
    STRENGTH,
    RUNNING,
    HIIT,
    YOGA,
    CYCLING,
    SWIMMING,
    CROSSFIT,
    OTHER;

    companion object {

        /**
         * Resolves the constant a Firestore document stores as a string.
         *
         * Falls back to [OTHER] rather than throwing: a document written by a newer version of the
         * app, or edited by hand in the console, must never crash the feed.
         */
        fun fromName(name: String?): WorkoutCategory =
            entries.firstOrNull { it.name == name } ?: OTHER
    }
}
