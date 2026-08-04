package com.roeiamor.fitshare

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import com.roeiamor.fitshare.di.ServiceLocator

/**
 * The application entry point. Runs once, before any Activity, and does exactly two things:
 * wires up the [ServiceLocator] and locks the UI language to Hebrew.
 */
class FitShareApp : Application() {

    override fun onCreate() {
        super.onCreate()
        ServiceLocator.init(this)
        forceHebrewLocale()
    }

    /**
     * Forces the Hebrew locale no matter what language the device is set to (SPEC section 9.2).
     *
     * FitShare is a Hebrew product: the copy, the layouts and the RTL direction are all designed
     * around it, so following the device language would break the design rather than help anyone.
     * This is also why values-en/strings.xml never loads - it exists to prove no text is
     * hardcoded, not to be shown.
     */
    private fun forceHebrewLocale() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("he"))
    }
}
