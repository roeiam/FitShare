package com.roeiamor.fitshare.util

import androidx.annotation.StringRes
import androidx.fragment.app.Fragment
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout

/**
 * Small view helpers shared by every screen, so the same three lines are not repeated in nine
 * fragments. Anything here must be genuinely generic - screen-specific logic belongs in a ViewModel.
 */

/**
 * Shows a Hebrew message at the bottom of the screen.
 *
 * Anchored to the Fragment's own view, so it appears above the bottom navigation bar rather than
 * underneath it.
 *
 * @param messageRes the string resource, normally produced by [ErrorMapper].
 */
fun Fragment.showSnackbar(@StringRes messageRes: Int) {
    val root = view ?: return
    Snackbar.make(root, messageRes, Snackbar.LENGTH_LONG).show()
}

/**
 * Sets or clears the inline error under a text field.
 *
 * ViewModels publish errors as string resources, never as text, so this is where a resource id
 * becomes something the user can read.
 *
 * @param messageRes the error to show, or null to clear it.
 */
fun TextInputLayout.setErrorRes(@StringRes messageRes: Int?) {
    error = messageRes?.let { context.getString(it) }
}
