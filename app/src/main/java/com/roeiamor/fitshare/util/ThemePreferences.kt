package com.roeiamor.fitshare.util

import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.edit

/**
 * Remembers whether the user prefers the dark or the light theme, across launches.
 *
 * `SharedPreferences` rather than DataStore: one boolean does not justify a coroutine-based
 * key-value store and an extra dependency, and this is a file every reader already understands.
 *
 * **Dark is the default** because the mockups in SPEC section 7 are drawn dark, and because a theme
 * that changes with the device would mean every screen had been tuned against whichever mode the
 * developer's phone happened to be in. The user can still switch, and the choice survives a restart.
 *
 * @param context the application context; never an Activity.
 */
class ThemePreferences(context: Context) {

    private val preferences =
        context.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    /** True when the app should render dark. Defaults to true on a fresh install. */
    var isDarkTheme: Boolean
        get() = preferences.getBoolean(KEY_DARK_THEME, DEFAULT_DARK)
        set(value) {
            preferences.edit { putBoolean(KEY_DARK_THEME, value) }
            applyToApp()
        }

    /**
     * Pushes the stored choice into AppCompat.
     *
     * Called once at startup, and again from the setter. `setDefaultNightMode` recreates the
     * running activities itself, which is what makes the toggle take effect immediately rather than
     * on the next launch - and why the Fragment does not have to do anything after flipping it.
     */
    fun applyToApp() {
        AppCompatDelegate.setDefaultNightMode(
            if (isDarkTheme) AppCompatDelegate.MODE_NIGHT_YES else AppCompatDelegate.MODE_NIGHT_NO
        )
    }

    private companion object {
        const val PREFERENCES_NAME = "fitshare_settings"
        const val KEY_DARK_THEME = "dark_theme"
        const val DEFAULT_DARK = true
    }
}
