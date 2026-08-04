package com.roeiamor.fitshare.util

import android.graphics.Rect
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.bumptech.glide.Glide
import com.google.android.material.snackbar.Snackbar
import com.google.android.material.textfield.TextInputLayout
import com.roeiamor.fitshare.R

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

/**
 * Hides the soft keyboard.
 *
 * Takes the window token from this view, which is what tells the input method *which* window to
 * close the keyboard for - a plain `hideSoftInputFromWindow(null, 0)` does nothing.
 */
fun View.hideKeyboard() {
    val inputMethodManager =
        ContextCompat.getSystemService(context, InputMethodManager::class.java) ?: return
    inputMethodManager.hideSoftInputFromWindow(windowToken, 0)
}

/**
 * Asks the nearest scrolling parent to bring this view fully above the keyboard.
 *
 * Posted, because the caller is usually reacting to something that has not finished laying out yet -
 * a focus change, or the IME inset arriving - and scrolling against a stale layout aims at the wrong
 * place. [KEYBOARD_CLEARANCE_PX] is asked for on top of the view's own height so the field does not
 * end up flush against the top of the keyboard.
 */
fun View.requestVisibleAboveKeyboard() {
    post {
        if (!isAttachedToWindow) return@post
        requestRectangleOnScreen(Rect(0, 0, width, height + KEYBOARD_CLEARANCE_PX), false)
    }
}

/** Breathing room between the focused field and the top of the keyboard. */
private const val KEYBOARD_CLEARANCE_PX = 96

/**
 * Runs [action] when the user presses the keyboard's Done key on this field.
 *
 * Lets the last field in a form submit it, rather than only closing the keyboard and leaving the
 * user to find the button. Returns false so the IME still performs its normal dismissal.
 *
 * @param action usually the same call the submit button makes; it is expected to ignore an invalid
 *   form itself, so this does not need to check.
 */
fun EditText.onImeDone(action: () -> Unit) {
    setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) action()
        false
    }
}

/**
 * Loads a workout photo, or shows the placeholder when there is none.
 *
 * A workout without a photo is normal, not an error - the card still has to look right - so a null
 * or blank URL clears any previous request and shows the placeholder rather than attempting a load
 * that would fail.
 *
 * Clearing matters because views are recycled: without it, a card with no image would briefly show
 * whichever photo the recycled view was displaying before.
 *
 * @param url a Cloudinary URL, or null.
 */
fun ImageView.loadWorkoutImage(url: String?) {
    if (url.isNullOrBlank()) {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.placeholder_workout)
        return
    }
    Glide.with(this)
        .load(url)
        .placeholder(R.drawable.placeholder_workout)
        .error(R.drawable.image_error)
        .into(this)
}

/**
 * Loads a circular avatar, or shows the placeholder when the user has none.
 *
 * `circleCrop` rather than a shaped ImageView alone, so the bitmap Glide caches is already circular
 * and the shape cannot disagree with the loaded image while a new photo is arriving.
 *
 * @param url a Cloudinary URL, or null.
 */
fun ImageView.loadAvatar(url: String?) {
    if (url.isNullOrBlank()) {
        Glide.with(this).clear(this)
        setImageResource(R.drawable.placeholder_avatar)
        return
    }
    Glide.with(this)
        .load(url)
        .circleCrop()
        .placeholder(R.drawable.placeholder_avatar)
        .error(R.drawable.placeholder_avatar)
        .into(this)
}
