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
import androidx.navigation.navOptions
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.roeiamor.fitshare.databinding.ActivityMainBinding
import com.roeiamor.fitshare.di.ServiceLocator
import com.roeiamor.fitshare.util.hideKeyboard
import com.roeiamor.fitshare.util.offlineBannerVisibility
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
     * cancelling the flow unregisters the callback through its `awaitClose`. It also means this runs
     * again on every return to the app, which is why the first emission has to be right.
     *
     * **The banner is earned, not assumed.** It starts hidden and stays hidden until this run of the
     * collection has watched a working connection actually go away;
     * [com.roeiamor.fitshare.util.offlineBannerVisibility] holds that rule and documents it. Because
     * `repeatOnLifecycle` restarts the collection on every resume, that evidence is discarded every
     * time the app comes back - so returning to the app can never show the banner, whatever the
     * platform reports in the first moments after a resume, and however the user got back:
     * recent-apps, launcher icon, or unlocking the screen. Only a loss observed *after* that point
     * counts. Verified on a Samsung SM-A305F (Android 11), sampling the screen for seven seconds
     * after every resume: twenty consecutive returns through the recent-apps switcher, and eight
     * screen-off cycles returning through a PIN unlock. None of the twenty-eight showed it.
     *
     * The old delay is kept behind that as a second layer, for losses that happen mid-session:
     * Android routinely reports a network as available a fraction of a second before it reports it
     * as validated, and hands over between networks by losing one before gaining the next. Measured
     * on the device: two such windows during a single airplane-mode toggle, 0.69s and 1.05s, neither
     * of which the user would call "no internet". [OFFLINE_BANNER_DELAY_MS] outlasts them, and a
     * genuine disconnection still surfaces quickly - four airplane-mode toggles on the same device
     * put the banner on screen 3.5s, 3.8s, 4.1s and 4.1s after the switch, which is this delay plus
     * the time the platform itself takes to tear the connection down and report it.
     *
     * The visibility is also cleared explicitly below, before collection begins. The flow's own
     * first emission is already `false`, but the view keeps whatever visibility it had when the app
     * was stopped, and clearing it here means the banner is down from the first frame the user sees
     * rather than from the first emission they do not.
     *
     * Both layers are deliberately **presentation only**. [com.roeiamor.fitshare.util.NetworkGuard]
     * still asks [com.roeiamor.fitshare.util.NetworkMonitor.isOnline] directly and still refuses a
     * call the instant there is no validated connection, so an offline failure message appears with
     * no delay at all. The banner can afford to wait and see; a write cannot.
     */
    private fun observeConnectivity() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                binding.offlineBanner.isVisible = false

                ServiceLocator.networkMonitor.observe()
                    .offlineBannerVisibility(OFFLINE_BANNER_DELAY_MS)
                    .collect { showBanner -> binding.offlineBanner.isVisible = showBanner }
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
     *
     * **The guard is not an optimisation.** Below API 33 AppCompat stores the locale itself and
     * applies it by recreating the Activity, and it does that on every call - including the call
     * this very method makes from `onCreate` of the Activity that a *theme* change has just
     * recreated. Unguarded, one theme switch became two relaunches back to back, which is what made
     * the screen sit black for over two seconds instead of a fraction of one.
     */
    private fun forceHebrewLocale() {
        if (AppCompatDelegate.getApplicationLocales().toLanguageTags() == HEBREW_LANGUAGE_TAG) return
        AppCompatDelegate.setApplicationLocales(
            LocaleListCompat.forLanguageTags(HEBREW_LANGUAGE_TAG)
        )
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

        // Each item id in menu_bottom_nav.xml is also a destination id in nav_graph.xml, so a tab
        // tap is simply "navigate to the destination with this id".
        setUpBottomNavigation()

        navController.addOnDestinationChangedListener { _, destination, _ ->
            isAuthDestination = destination.id in authDestinations
            updateBottomNavVisibility()
            highlightTab(destination.id)
        }
    }

    /**
     * Wires the bottom bar to the graph by hand, instead of with
     * `NavigationUI.setupWithNavController`.
     *
     * That helper is written for an app whose tabs are **nested** graphs. It navigates with
     * `popUpTo(startDestination) { saveState = true }` plus `restoreState = true`, and against this
     * flat graph - where `workoutDetailsFragment` is a sibling of the tabs rather than a child of
     * one - that pair breaks the bottom bar in two ways, both reproduced on the device:
     *
     *  - **Every tab stopped working after opening a workout.** Tapping פיד while a workout was
     *    showing popped the workout off (saving it, keyed by the destination popped up *to* - the
     *    feed, which is the graph's start destination) and then, in the very same call, matched that
     *    freshly saved key with `restoreState` and pushed the workout straight back. The stack came
     *    out byte for byte identical, the helper reported the tap unhandled, and the screen never
     *    changed.
     *  - **A tab remembered the workout you left it on.** Leaving מועדפים with a workout open saved
     *    `favoritesFragment > workoutDetailsFragment`, and coming back restored both, so the tab
     *    opened on the workout instead of the list.
     *
     * So the fix is to drop `saveState`/`restoreState` entirely: a tab tap goes to that tab's root
     * and forgets what was on top of it. Nesting each tab in its own graph would have made the
     * helper's assumption true, but it would also have kept the second behaviour - which is the one
     * that was reported as a bug - and it would have needed the details and profile destinations
     * duplicated into three sub-graphs.
     */
    private fun setUpBottomNavigation() {
        binding.bottomNav.setOnItemSelectedListener { item -> openTab(item.itemId) }

        // Fires instead of the listener above when the tapped tab is already the highlighted one -
        // which is exactly the case while a workout opened from that tab is showing. Same handler,
        // so a tab always takes the user to its root rather than doing nothing.
        binding.bottomNav.setOnItemReselectedListener { item -> openTab(item.itemId) }
    }

    /**
     * Takes the bottom bar to a tab's root screen, from whatever depth the user is at.
     *
     * `popUpTo` the graph's start destination clears everything pushed on top - a workout, the
     * editor, someone else's profile - so a tab tap always lands on that tab's own screen. Back from
     * any tab then returns to the feed and exits from there, which is the behaviour Phase 2 settled
     * on and this keeps unchanged.
     *
     * Deliberately no `saveState` and no `restoreState`: that is what tells Navigation to forget the
     * screen the user was on inside a tab. Restoring it would drop them back onto a workout they had
     * already left, which is not what tapping a tab means.
     *
     * Always returns true, so the tapped tab takes the highlight.
     */
    private fun openTab(tabId: Int): Boolean {
        if (isAtTabRoot(tabId)) return true

        navController.navigate(
            tabId,
            null,
            navOptions {
                launchSingleTop = true
                popUpTo(navController.graph.startDestinationId) { inclusive = false }
            }
        )
        return true
    }

    /**
     * True when the screen already showing **is** that tab's root, so the tap has nothing to do.
     *
     * The destination id on its own is not enough to decide. Two tabs double as something else
     * behind a nullable argument - `profileFragment` also shows another user's profile,
     * `addWorkoutFragment` also edits an existing workout - and from those the tab must still
     * navigate. Every argument on a tab destination is a nullable String defaulting to null, so a
     * tab root is that destination with all of its arguments still null.
     */
    private fun isAtTabRoot(tabId: Int): Boolean {
        val entry = navController.currentBackStackEntry ?: return false
        if (entry.destination.id != tabId) return false
        val arguments = entry.arguments ?: return true
        return entry.destination.arguments.keys.all { arguments.getString(it) == null }
    }

    /**
     * Keeps the highlighted tab in step with navigation that did not come from a tab tap - signing
     * in, the feed's add button, or a card opening a workout.
     *
     * Anything that is not a tab root leaves the highlight alone, so a workout opened from the feed
     * still shows פיד as the current tab, and another user's profile does not light up פרופיל.
     * Setting `isChecked` rather than `selectedItemId` moves the highlight without pretending the
     * user tapped, so this cannot loop back into [openTab].
     */
    private fun highlightTab(destinationId: Int) {
        if (!isAtTabRoot(destinationId)) return
        binding.bottomNav.menu.findItem(destinationId)?.isChecked = true
    }

    private companion object {
        /** The only language the app ever runs in (SPEC section 9.2). */
        const val HEBREW_LANGUAGE_TAG = "he"

        /**
         * How long the connection must stay down before the banner appears.
         *
         * 1.5 seconds, chosen from measurement rather than taste: a single airplane-mode toggle on a
         * device produced two windows in which the app was momentarily "not validated" while the
         * connection was in fact fine - 0.69s and 1.05s. This outlasts both with margin, and still
         * surfaces a genuine disconnection inside two seconds.
         */
        const val OFFLINE_BANNER_DELAY_MS = 1_500L
    }
}
