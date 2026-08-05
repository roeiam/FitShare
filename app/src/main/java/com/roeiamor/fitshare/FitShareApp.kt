package com.roeiamor.fitshare

import android.app.Application
import com.roeiamor.fitshare.di.ServiceLocator

/**
 * The application entry point. Runs once, before any Activity, and does two things:
 * wires up the [ServiceLocator] and applies the stored theme.
 *
 * The Hebrew locale is *not* forced here. See [MainActivity.forceHebrewLocale] for why it cannot be.
 */
class FitShareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        applyStoredTheme()
    }

    /**
     * Applies the user's stored light/dark choice, defaulting to dark.
     *
     * Dark is the default because the mockups in SPEC section 7 are drawn that way (values-night);
     * following the device instead would mean every screen had been tuned against whichever mode the
     * developer's phone happened to be in. The toggle on the profile screen overwrites the stored
     * value, and this line is what makes that choice survive a restart.
     *
     * Safe in `Application.onCreate`, unlike `setApplicationLocales` - see
     * [MainActivity.forceHebrewLocale] for why that one is not.
     */
    private fun applyStoredTheme() {
        ServiceLocator.themePreferences.applyToApp()
    }
}
