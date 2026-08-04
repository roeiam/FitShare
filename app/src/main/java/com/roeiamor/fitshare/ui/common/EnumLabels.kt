package com.roeiamor.fitshare.ui.common

import androidx.annotation.StringRes
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.WorkoutCategory

/**
 * The **only** place an enum becomes a Hebrew label (decision PHASE0_PLAN.md section 4.C).
 *
 * The enums themselves stay Android-free so `data/model` has no `R` and no `androidx` imports.
 * Mapping them anywhere else - in a Fragment, in an adapter, inline in a layout - is a defect:
 * the same `when` would end up written twice and drift.
 */

/** The Hebrew label for a workout category. */
@StringRes
fun WorkoutCategory.labelRes(): Int = when (this) {
    WorkoutCategory.STRENGTH -> R.string.category_strength
    WorkoutCategory.RUNNING -> R.string.category_running
    WorkoutCategory.HIIT -> R.string.category_hiit
    WorkoutCategory.YOGA -> R.string.category_yoga
    WorkoutCategory.CYCLING -> R.string.category_cycling
    WorkoutCategory.SWIMMING -> R.string.category_swimming
    WorkoutCategory.CROSSFIT -> R.string.category_crossfit
    WorkoutCategory.OTHER -> R.string.category_other
}

/** The Hebrew label for a difficulty level. */
@StringRes
fun Difficulty.labelRes(): Int = when (this) {
    Difficulty.EASY -> R.string.difficulty_easy
    Difficulty.MEDIUM -> R.string.difficulty_medium
    Difficulty.HARD -> R.string.difficulty_hard
}
