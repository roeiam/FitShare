package com.roeiamor.fitshare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import com.roeiamor.fitshare.di.ServiceLocator

/**
 * The application entry point. Runs once, before any Activity, and does two things:
 * wires up the [ServiceLocator] and selects the dark theme.
 *
 * The Hebrew locale is *not* forced here. See [MainActivity.forceHebrewLocale] for why it cannot be.
 */
class FitShareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        applyDefaultDarkTheme()
    }

    /**
     * Makes dark the default look, as the mockups in SPEC section 7 are drawn (values-night).
     * Without this the theme would follow the device, and every screen built from Phase 2 onwards
     * would be tuned against whichever mode the developer's phone happened to be in.
     *
     * This is only the *default*. From Phase 7, ThemePreferences reads the user's stored choice and
     * calls setDefaultNightMode again with it, overriding this line. Light mode stays fully usable.
     */
    private fun applyDefaultDarkTheme() {
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)
    }
}
