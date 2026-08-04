package com.roeiamor.fitshare.ui.common

import android.content.Context
import androidx.annotation.StringRes
import androidx.annotation.StyleRes
import com.google.android.material.chip.Chip
import com.google.android.material.chip.ChipDrawable
import com.google.android.material.chip.ChipGroup

/**
 * Builds single-selection chip rows from an enum.
 *
 * Three screens need the same thing - the feed's category filter, and the add-workout form's
 * category and difficulty pickers - and writing the chips into XML would duplicate each enum, and
 * its label mapping, once per screen. They would then quietly go stale the day a category is added.
 *
 * Chips are given explicit sequential ids because [ChipGroup] tracks its selection by view id, and
 * generated views otherwise get ids that change between inflations.
 */
object ChipBuilder {

    /**
     * Fills [group] with one chip per entry, plus an optional leading chip.
     *
     * @param group the ChipGroup to populate; cleared first, so this is safe to call again.
     * @param baseId the id of the first chip; the rest follow in order.
     * @param leadingLabelRes an extra chip before the entries - the feed's "הכל". Null for none.
     * @param entryLabels one label resource per entry, in order.
     * @param checkedIndex which entry starts checked, counting the leading chip as -1.
     * @param onSelected receives the index of the checked entry, or -1 for the leading chip.
     */
    fun buildSingleSelection(
        group: ChipGroup,
        baseId: Int,
        @StringRes leadingLabelRes: Int?,
        entryLabels: List<Int>,
        checkedIndex: Int,
        onSelected: (Int) -> Unit
    ) {
        val context = group.context
        group.removeAllViews()

        var nextId = baseId
        if (leadingLabelRes != null) {
            group.addView(buildChip(context, nextId, leadingLabelRes))
            nextId++
        }
        entryLabels.forEach { labelRes ->
            group.addView(buildChip(context, nextId, labelRes))
            nextId++
        }

        val leadingOffset = if (leadingLabelRes != null) 1 else 0
        group.check(baseId + leadingOffset + checkedIndex)

        group.setOnCheckedStateChangeListener { _, checkedIds ->
            val checkedId = checkedIds.firstOrNull() ?: return@setOnCheckedStateChangeListener
            onSelected(checkedId - baseId - leadingOffset)
        }
    }

    /** One filter-styled, checkable chip. */
    private fun buildChip(context: Context, id: Int, @StringRes labelRes: Int): Chip =
        Chip(context).apply {
            this.id = id
            setChipDrawable(chipDrawable(context, com.roeiamor.fitshare.R.style.Widget_FitShare_Chip_Filter))
            setText(labelRes)
            isCheckable = true
        }

    /**
     * A ChipDrawable is how a generated Chip picks up a style. Setting `style` on the constructor
     * does not work for a view created in code, so the drawable carries the appearance instead.
     */
    private fun chipDrawable(context: Context, @StyleRes styleRes: Int): ChipDrawable =
        ChipDrawable.createFromAttributes(context, null, 0, styleRes)
}
