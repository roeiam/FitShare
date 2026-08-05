package com.roeiamor.fitshare

import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.widget.EditText
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.isGone
import androidx.core.view.updatePadding
import androidx.navigation.NavController
import androidx.navigation.fragment.NavHostFragment
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.ui.setupWithNavController
import com.roeiamor.fitshare.databinding.ActivityMainBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.util.hideKeyboard
import com.roeiamor.fitshare.util.requestVisibleAboveKeyboard
import kotlinx.coroutines.launch

/**
 * The only Activity in the app. It hosts the navigation graph and the bottom navigation bar and
 * does nothing else - every screen is a Fragment (SPEC section 5).
 *
 * Its jobs: choose the start destination from the current session, keep the bottom bar in sync with
 * navigation, hide that bar on the authentication screens, and show the no-connection banner.
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

    /**
     * Two independent reasons to hide the bottom bar, kept as state rather than each listener
     * setting visibility directly. Two listeners writing the same property would fight: whichever
     * fired last would win, and opening the keyboard on the login screen would bring the bar back.
     */
    private var isAuthDestination = false
    private var isKeyboardVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        forceHebrewLocale()
        enableEdgeToEdge()
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        applySystemBarInsets()
        setUpNavigation()
        observeConnectivity()
    }

    /**
     * Shows the banner whenever the device has no usable internet (SPEC section 6).
     *
     * On the Activity rather than on each screen, because connectivity belongs to the device, not to
     * whichever fragment happens to be showing - so every screen inherits it and a new screen cannot
     * forget it.
     *
     * `repeatOnLifecycle(STARTED)` is what stops the `NetworkCallback` from staying registered while
     * the app is in the background: collection is cancelled on stop and restarted on start, and
     * cancelling the flow unregisters the callback through its `awaitClose`.
     */
    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                ServiceLocator.networkMonitor.observe().collect { isOnline ->
                    binding.offlineBanner.isVisible = !isOnline
                }
            }
        }
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
     * Pads the layout away from the system bars, and makes room for the keyboard.
     *
     * The app draws edge to edge, which the system enforces from API 35. That also means
     * `adjustResize` no longer resizes anything by itself - once the window stops fitting system
     * windows, the IME inset has to be applied by hand, which is what this does.
     *
     * Three deliberate choices, all of them fixing something that looked wrong on a real device:
     *
     *  - The bottom system-bar inset goes on the **navigation bar**, not the root. Padding the root
     *    would leave a strip of page background below the bar.
     *  - The bottom navigation is **hidden while the keyboard is open**. Keeping it stacked above
     *    the keyboard steals a row of height from an already short form and cannot be tapped
     *    meaningfully mid-typing anyway.
     *  - The IME inset is applied to the **nav host**, so the form scrolls inside the space that is
     *    left instead of the whole screen being squashed upwards.
     */
    private fun applySystemBarInsets() {
        ViewCompat.setOnApplyWindowInsetsListener(binding.main) { view, windowInsets ->
            val bars = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = windowInsets.getInsets(WindowInsetsCompat.Type.ime())
            val keyboardVisible = windowInsets.isVisible(WindowInsetsCompat.Type.ime())

            view.updatePadding(left = bars.left, top = bars.top, right = bars.right)

            binding.bottomNav.updatePadding(bottom = bars.bottom)

            isKeyboardVisible = keyboardVisible
            updateBottomNavVisibility()

            // The full IME inset, not the inset minus the system bars. While the keyboard is open
            // the bottom navigation is hidden, so the nav host runs to the true bottom of the
            // screen and nothing else is absorbing that space. Subtracting the system-bar inset
            // here - which an earlier version did - left the focused field about twenty pixels
            // behind the top of the keyboard on the physical device.
            binding.navHostFragment.updatePadding(
                bottom = if (keyboardVisible) ime.bottom else 0
            )

            // Scroll the focused field clear of the keyboard *here*, not when it gained focus.
            // This is the first moment the layout knows how tall the keyboard is; asking earlier
            // scrolls against the pre-keyboard layout and leaves the field half covered, which is
            // exactly what it did on the physical device before this was moved.
            if (keyboardVisible) currentFocus?.requestVisibleAboveKeyboard()

            windowInsets
        }
    }

    /** The bottom bar is hidden on the auth screens, and while the keyboard is up. */
    private fun updateBottomNavVisibility() {
        binding.bottomNav.isGone = isAuthDestination || isKeyboardVisible
    }

    /**
     * Dismisses the keyboard when the user taps anywhere outside the field they are typing in.
     *
     * Done here, once, rather than per screen: it needs the raw touch stream before any view has
     * consumed it, and this is the only Activity, so every screen and every dialog it hosts is
     * covered by this one override.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val focused = currentFocus
            if (focused is EditText && !focused.containsTouch(event)) {
                focused.clearFocus()
                focused.hideKeyboard()
            }
        }
        return super.dispatchTouchEvent(event)
    }

    /** True when [event] landed inside this view's bounds on screen. */
    private fun View.containsTouch(event: MotionEvent): Boolean {
        val bounds = Rect()
        getGlobalVisibleRect(bounds)
        return bounds.contains(event.rawX.toInt(), event.rawY.toInt())
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
            isAuthDestination = destination.id in authDestinations
            updateBottomNavVisibility()
        }
    }
}
