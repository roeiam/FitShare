package com.roeiamor.fitshare.ui.common

import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.data.model.Difficulty
import com.roeiamor.fitshare.data.model.Workout
import com.roeiamor.fitshare.data.model.WorkoutCategory
import com.roeiamor.fitshare.databinding.ViewMetaChipsBinding

/**
 * Fills the three meta chips - category, duration, difficulty - from a [Workout].
 *
 * The feed card and the details screen show the same three facts, and both used to build them with
 * their own copy of these six lines. One copy could then start rounding the duration differently, or
 * miss a new category, without the other noticing. Written once here, both screens change together.
 *
 * It is an extension on the generated binding of `view_meta_chips.xml`, so the caller cannot pass the
 * wrong views in, and the whole component - layout plus the code that fills it - stays one unit.
 *
 * The duration goes through `plurals` rather than a `%d` string: Hebrew inflects for one, so
 * "1 דקות" would read as broken Hebrew.
 */
fun ViewMetaChipsBinding.bind(workout: Workout) {
    categoryChip.setText(WorkoutCategory.fromName(workout.category).labelRes())

    durationChip.text = root.resources.getQuantityString(
        R.plurals.unit_minutes,
        workout.durationMinutes,
        workout.durationMinutes
    )

    difficultyChip.setText(Difficulty.fromName(workout.difficulty).labelRes())
}
