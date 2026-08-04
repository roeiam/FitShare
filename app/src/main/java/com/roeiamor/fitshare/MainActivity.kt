package com.roeiamor.fitshare

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isVisible
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.roeiamor.fitshare.databinding.ActivityMainBinding
import com.roeiamor.fitshare.di.ServiceLocator

/**
 * The only Activity in the app. It hosts the navigation graph and the bottom navigation bar and
 * does nothing else - every screen is a Fragment (SPEC section 5).
 *
 * Its three jobs: choose the start destination from the current session, keep the bottom bar in
 * sync with navigation, and hide that bar on the authentication screens.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var navController: NavController

    /** The auth flow destinations, where a bottom navigation bar would make no sense. */
    private val authDestinations = setOf(
        R.id.loginFragment,
        R.id.registerFragment,
        R.id.forgotPasswordFragment
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forceHebrewLocale()
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets()
        setUpNavigation()
    }

    /**
     * Forces Hebrew no matter what language the device is set to (SPEC section 9.2).
     *
     * FitShare is a Hebrew product - the copy, the layouts and the RTL direction are designed around
     * it - so following the device language would break the design rather than help anyone. This is
     * also why values-en/strings.xml never loads: it exists to prove no text is hardcoded.
     *
     * This has to run from the Activity, not from Application.onCreate as first written. From API 33
     * AppCompat forwards the call to the system LocaleManager, and it reaches that service through
     * an active Activity delegate. Called from Application.onCreate no delegate exists yet, so the
     * call is a silent no-op: no exception, no log, the app just renders in the device language.
     * That was caught on the emulator in Phase 2 - the whole UI came up in English.
     *
     * From API 33 the system remembers the choice, so this only causes a recreate on the very first
     * launch after install; every launch after that already starts in Hebrew.
     */
    private fun forceHebrewLocale() {
        AppCompatDelegate.setApplicationLocales(LocaleListCompat.forLanguageTags("he"))
    }

    /**
     * Pads the layout away from the status and navigation bars.
     *
     * The app draws edge to edge, which the system enforces from API 35, so without this the top of
     * every screen would sit under the clock and the bottom bar under the gesture handle.
     *
     * The bottom inset goes on the navigation bar itself rather than on the root. Padding the root
     * would leave a strip of background below the bar; padding the bar lets its own surface colour
     * run to the bottom of the screen while its items still sit above the gesture handle.
     */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)
            binding.bottomNav.updatePadding(bottom = bars.bottom)
            windowInsets
        }
    }

    /**
     * Inflates the graph with a start destination chosen from the current session, connects the
     * bottom bar to it, and hides that bar on the auth screens.
     *
     * Choosing the start destination - rather than navigating away after the fact - is what makes
     * Back from the feed exit the app instead of walking backwards into a login screen the user has
     * already passed (SPEC section 5).
     */
    private fun setUpNavigation() {
        val navHostFragment =
            supportFragmentManager.findFragmentById(R.id.navHostFragment) as NavHostFragment
        navController = navHostFragment.navController

        val isSignedIn = ServiceLocator.authRepository.isSignedIn
        val graph = navController.navInflater.inflate(R.navigation.nav_graph)
        graph.setStartDestination(
            if (isSignedIn) R.id.feedFragment else R.id.loginFragment
        )
        navController.graph = graph

        // Matching ids between menu_bottom_nav.xml and nav_graph.xml let NavigationUI handle tab
        // taps, the selected highlight and the tab back stack with no click listeners of our own.
        binding.bottomNav.setupWithNavController(navController)

        navController.addOnDestinationChangedListener { _, destination, _ ->
            binding.bottomNav.isVisible = destination.id !in authDestinations
        }
    }
}
