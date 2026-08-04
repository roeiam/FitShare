package com.roeiamor.fitshare.ui.common

import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import androidx.core.view.isVisible
import com.google.android.material.textfield.TextInputLayout
import com.roeiamor.fitshare.R
import com.roeiamor.fitshare.util.hideKeyboard
import com.roeiamor.fitshare.util.requestVisibleAboveKeyboard

/**
 * Makes every form in the app behave the same way about the keyboard.
 *
 * Applied once from [BaseFragment], so a screen gets all of it by existing - there is nothing to
 * remember per form, and a screen added later cannot forget to do it. Walking the view tree once at
 * screen creation is cheap; these hierarchies are small.
 *
 * It fixes two things that are otherwise wrong on every screen:
 *
 *  1. **A multi-line field has no way to dismiss the keyboard.** Enter inserts a newline there,
 *     which is correct, so the IME shows no action key at all. Every multi-line field gets an
 *     explicit "done" end icon instead.
 *  2. **The focused field is not reliably visible.** When the keyboard opens over a scrolling form,
 *     the field being typed into can end up behind it. Each field asks to be scrolled into view when
 *     it gains focus.
 *
 * Dismissing the keyboard by tapping outside a field is handled once in
 * [com.roeiamor.fitshare.MainActivity], because it needs the raw touch stream before any view
 * consumes it.
 */
object FormKeyboardSupport {

    /** Walks [root] once and applies the shared behaviour to every text field it contains. */
    fun apply(root: View) {
        forEachEditText(root) { editText ->
            addDoneIconIfMultiline(editText)
            scrollIntoViewOnFocus(editText)
        }
    }

    /**
     * Gives a multi-line field a "done" end icon on its [TextInputLayout].
     *
     * Single-line fields already have an IME action key - [EditorInfo.IME_ACTION_NEXT] or
     * [EditorInfo.IME_ACTION_DONE] - so they are left alone; adding an icon there would be clutter.
     */
    private fun addDoneIconIfMultiline(editText: EditText) {
        if (!editText.isMultiline()) return

        val layout = editText.parentTextInputLayout() ?: return
        // Do not fight a field that already asked for a specific end icon, such as a password toggle.
        if (layout.endIconMode != TextInputLayout.END_ICON_NONE) return

        layout.endIconMode = TextInputLayout.END_ICON_CUSTOM
        layout.setEndIconDrawable(R.drawable.ic_keyboard_done)
        layout.setEndIconContentDescription(R.string.action_done_typing)
        layout.setEndIconOnClickListener {
            editText.clearFocus()
            editText.hideKeyboard()
        }
    }

    /**
     * Brings the field into view when it gains focus.
     *
     * This covers moving **between** fields while the keyboard is already open, where no inset
     * changes and so nothing else would react. The other case - the keyboard opening for the first
     * time - is handled in `MainActivity` when the IME inset actually arrives, because at the moment
     * focus is granted the keyboard has not resized anything yet and scrolling here alone lands the
     * field half behind it. Both paths call the same helper, so the clearance is defined once.
     */
    private fun scrollIntoViewOnFocus(editText: EditText) {
        editText.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus && view.isVisible) view.requestVisibleAboveKeyboard()
        }
    }

    /** True when Enter inserts a newline rather than triggering an IME action. */
    private fun EditText.isMultiline(): Boolean =
        inputType and android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE != 0

    /** The TextInputLayout wrapping this field, if it has one. */
    private fun EditText.parentTextInputLayout(): TextInputLayout? {
        var current: View? = parent as? View
        while (current != null) {
            if (current is TextInputLayout) return current
            current = current.parent as? View
        }
        return null
    }

    /** Depth-first walk over every [EditText] under [view]. */
    private fun forEachEditText(view: View, action: (EditText) -> Unit) {
        when (view) {
            is EditText -> action(view)
            is ViewGroup -> for (index in 0 until view.childCount) {
                forEachEditText(view.getChildAt(index), action)
            }
        }
    }
}
