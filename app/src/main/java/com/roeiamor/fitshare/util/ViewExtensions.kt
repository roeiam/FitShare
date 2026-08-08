package com.roeiamor.fitshare.util

import android.graphics.Rect
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
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

/*
 * There is deliberately no `onImeDone` helper here any more.
 *
 * It used to let the last field in a form submit it from the keyboard's Done key. On the add-workout
 * form that made the duration field - which happens to be last - publish the workout the moment the
 * user finished typing a number, with no confirming tap and no chance to review the rest of the
 * form. Nothing in this app is submitted by a keyboard action key now: publishing, registering,
 * signing in and posting a comment all need a deliberate press on their own button.
 *
 * The Done key still does what a Done key should - it dismisses the keyboard - because that is the
 * IME's own default behaviour once nothing intercepts the action.
 */

/**
 * Loads a workout photo, or removes the image view entirely when there is none.
 *
 * A workout without a photo is normal, not an error, and the honest answer is to take the image out
 * of the layout rather than to fill it with a grey placeholder. A 16:9 block on a feed card - or a
 * 240dp one on the details screen - spent on an icon that says "there is no photo" is a third of the
 * card telling the reader nothing. `GONE` rather than `INVISIBLE`, so the space collapses and the
 * title moves up: the layout follows the content.
 *
 * **The visibility is assigned on both paths, never only when there is an image.** Views are
 * recycled, so a bind that skipped the assignment would inherit whatever the previous item left -
 * a hidden view for a workout that does have a photo, or worse, the previous workout's photo still
 * on screen. The drawable is cleared for the same reason.
 *
 * The placeholder still exists, and is still right, in the one place it was always for: the moment
 * between asking Glide for a real photo and it arriving.
 *
 * Every caller renders a workout image through this function - the feed card, the details screen,
 * the profile grid and the favourites row - so the rule holds in all four without any of them
 * needing to remember it.
 *
 * @param url a Cloudinary URL, or null.
 */
fun ImageView.loadWorkoutImage(url: String?) {
    if (url.isNullOrBlank()) {
        Glide.with(this).clear(this)
        setImageDrawable(null)
        isVisible = false
        return
    }
    isVisible = true
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
