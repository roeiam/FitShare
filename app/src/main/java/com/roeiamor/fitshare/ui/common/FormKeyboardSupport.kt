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
 * It fixes three things that are otherwise wrong on every screen:
 *
 *  1. **A multi-line field has no way to dismiss the keyboard.** Enter inserts a newline there,
 *     which is correct, so the IME shows no action key at all. Every multi-line field gets an
 *     explicit "done" end icon instead - and so does every numeric one, for a different reason.
 *  2. **A finishing action key does nothing.** Done, Search, Go and Send now clear focus and put the
 *     keyboard away, and that is the *only* thing any action key in this app does.
 *  3. **The focused field is not reliably visible.** When the keyboard opens over a scrolling form,
 *     the field being typed into can end up behind it. Each field asks to be scrolled into view when
 *     it gains focus.
 *
 * Together those make one promise worth stating plainly: **a keyboard key never submits anything in
 * this app.** Publishing, registering, signing in, sending a reset link and posting a comment all
 * need a deliberate press on their own button.
 *
 * Dismissing the keyboard by tapping outside a field is handled once in
 * [com.roeiamor.fitshare.MainActivity], because it needs the raw touch stream before any view
 * consumes it.
 */
object FormKeyboardSupport {

    /** Walks [root] once and applies the shared behaviour to every text field it contains. */
    fun apply(root: View) {
        forEachEditText(root) { editText ->
            addDoneIconIfNeeded(editText)
            dismissOnFinishingImeAction(editText)
            scrollIntoViewOnFocus(editText)
        }
    }

    /**
     * Makes a **finishing** IME action key put the keyboard away, and nothing else.
     *
     * This is the one rule for action keys in the whole app, and it is worth being precise about
     * what it is not. There used to be a helper that ran a form's submit action from the Done key;
     * it is deleted, because on the add-workout form it meant finishing a duration published the
     * workout. **No action key submits anything.** This listener only ever clears focus and hides
     * the keyboard - it cannot call into a ViewModel, because it is not given anything to call.
     *
     * Why it is needed at all rather than leaving the IME to its default: the feed's search field
     * declares `actionSearch`, so the keyboard shows a search key, and with nothing listening that
     * key did nothing whatsoever - the keyboard stayed up over the results the user had just
     * filtered. Search filters live on every keystroke, so the key has no query to run; getting out
     * of the way *is* the whole job.
     *
     * Only **finishing** actions are consumed. `IME_ACTION_NEXT` and `IME_ACTION_PREVIOUS` are
     * deliberately left alone: those move between fields, and swallowing them would strand the user
     * on the first field of every form.
     */
    private fun dismissOnFinishingImeAction(editText: EditText) {
        editText.setOnEditorActionListener { view, actionId, _ ->
            when (actionId) {
                EditorInfo.IME_ACTION_DONE,
                EditorInfo.IME_ACTION_SEARCH,
                EditorInfo.IME_ACTION_GO,
                EditorInfo.IME_ACTION_SEND -> {
                    view.clearFocus()
                    view.hideKeyboard()
                    // Consumed - and consuming it is the point: nothing further happens.
                    true
                }

                else -> false
            }
        }
    }

    /**
     * Gives a field a "done" end icon on its [TextInputLayout] when its keyboard has no usable
     * action key of its own.
     *
     * Two kinds of field need it:
     *
     *  - **Multi-line.** Enter inserts a newline there, which is correct, so the IME shows no action
     *    key at all.
     *  - **Numeric.** This one was measured rather than assumed. The duration field declares
     *    `IME_ACTION_DONE` and the IME receives it - `imeOptions=0x…006` in the input-method dump -
     *    but Samsung's keyboard renders a plain ↵ for a number field and tapping it performs no
     *    action whatsoever, so there is no way to close the keyboard from the key that looks like it
     *    should. An icon that always works beats an action key that works on some devices.
     *
     * Ordinary single-line text fields are left alone: their Next and Done keys behave, and an extra
     * icon there would be clutter.
     */
    private fun addDoneIconIfNeeded(editText: EditText) {
        if (!editText.isMultiline() && !editText.isNumeric()) return

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

    /** True for a number pad, whose action key is not dependable across IMEs - see above. */
    private fun EditText.isNumeric(): Boolean =
        inputType and android.text.InputType.TYPE_MASK_CLASS == android.text.InputType.TYPE_CLASS_NUMBER

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
