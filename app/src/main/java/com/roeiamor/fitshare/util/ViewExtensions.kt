package com.roeiamor.fitshare.util

import android.widget.ImageView
import androidx.annotation.StringRes
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
