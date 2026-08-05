package com.roeiamor.fitshare.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged

/**
 * What [NetworkMonitor] knows about the connection at a given moment.
 *
 * Three states rather than a boolean, because "not connected" and "not answered yet" are genuinely
 * different and only one of them is worth telling the user about. Collapsing them into `false` is
 * what made the offline banner flash on launch and on every return from the background: Android
 * reports a network as available slightly before it reports it as validated, and a boolean has
 * nowhere to put that gap.
 */
enum class NetworkStatus {
    /** Nothing has been reported yet. Never shown to the user - it means "ask again in a moment". */
    UNKNOWN,

    /** A validated internet connection exists. */
    ONLINE,

    /** No validated connection. The only state the banner reacts to. */
    OFFLINE
}

/**
 * Answers one question: does this device currently have usable internet?
 *
 * Two callers need it, for different reasons. The banner in `MainActivity` needs to *watch* the
 * answer so it can appear and disappear on its own. [NetworkGuard] needs to *ask once*, before a
 * write, so the app can fail immediately instead of handing Firestore a write it will silently
 * queue.
 *
 * `NET_CAPABILITY_VALIDATED` rather than merely `INTERNET`: a phone joined to a captive-portal wifi
 * has a connection that carries no traffic, and reporting that as online would be a lie the user can
 * see. Validated means Android actually reached the internet over it.
 *
 * @param context the application context; never an Activity, since this outlives any screen.
 */
class NetworkMonitor(context: Context) {

    private val connectivityManager = context.getSystemService<ConnectivityManager>()

    /**
     * True when a validated internet connection exists right now.
     *
     * The strict form, used by [NetworkGuard] before every network-bound call: anything short of a
     * validated connection is treated as no connection, so a write is never handed to Firestore to
     * queue locally and commit behind the user's back. Deliberately **not** softened by the
     * three-state model below - the banner can afford to wait and see, a write cannot.
     *
     * Returns true when the system service is somehow unavailable: refusing to write because a
     * platform service is missing would be worse than attempting the write and letting the timeout
     * catch it.
     */
    val isOnline: Boolean
        get() {
            val manager = connectivityManager ?: return true
            return manager.hasValidatedConnection()
        }

    /**
     * The connection state as it stands right now, read synchronously.
     *
     * This is what seeds [observe], so a screen that starts while the connection has been up all
     * along never passes through a false "offline" on its way to the truth.
     *
     * [NetworkStatus.UNKNOWN] only when the platform service is missing entirely - genuinely "cannot
     * tell", which is different from "no connection" and must not raise the banner.
     */
    fun currentStatus(): NetworkStatus {
        val manager = connectivityManager ?: return NetworkStatus.UNKNOWN
        return if (manager.hasValidatedConnection()) NetworkStatus.ONLINE else NetworkStatus.OFFLINE
    }

    /**
     * Emits the connection state, and again on every change.
     *
     * The same `callbackFlow` plus `awaitClose` shape the Firestore listeners use, and for the same
     * reason: a registered `NetworkCallback` stays registered until it is unregistered, so without
     * that block every subscription would leak one.
     *
     * The first emission is the **synchronous** [currentStatus], sent before the callback is even
     * registered, so a collector never begins life believing it is offline.
     *
     * `distinctUntilChanged` because the system reports several capability changes for what a user
     * would call one event - joining a network fires available, then validated - and there is no
     * point waking the collector for each.
     *
     * Note what this flow does **not** do: it does not smooth over the short windows where a
     * connection is coming up and not yet validated. It reports what the system reports, when the
     * system reports it. Deciding that a two-second gap is not worth a banner is a presentation
     * decision, and it lives in `MainActivity` where the banner does.
     */
    fun observe(): Flow<NetworkStatus> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(NetworkStatus.UNKNOWN)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(currentStatus())
            }

            override fun onLost(network: Network) {
                trySend(currentStatus())
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(currentStatus())
            }
        }

        trySend(currentStatus())
        manager.registerDefaultNetworkCallback(callback)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    /**
     * The one place the capability test is written, so [isOnline] and [currentStatus] can never
     * drift apart and start disagreeing about what "online" means.
     */
    private fun ConnectivityManager.hasValidatedConnection(): Boolean {
        val capabilities = getNetworkCapabilities(activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}

/**
 * Turns a stream of connection states into "should the offline banner be on screen?".
 *
 * The rule, in one sentence: **showing waits, hiding does not.**
 *
 * - [NetworkStatus.OFFLINE] emits `true`, but only after [delayMillis] of staying offline.
 * - [NetworkStatus.ONLINE] and [NetworkStatus.UNKNOWN] emit `false` at once.
 *
 * `collectLatest` is what makes the wait a debounce rather than a queue: when a newer status
 * arrives, the block still sitting in `delay` is cancelled outright, so a connection that dropped
 * and came back inside the window never reaches `send(true)` at all. That is the whole fix - Android
 * reports a network as available slightly before it reports it as validated, and hands over between
 * networks by losing one before gaining the next, so short bursts of "offline" are normal and are
 * not worth telling the user about.
 *
 * A `Flow` operator rather than an `if` inside the Activity, so the rule can be tested against
 * virtual time instead of against a real device's reconnect timing, which is not controllable.
 *
 * @param delayMillis how long the connection must stay down before the user is told.
 */
fun Flow<NetworkStatus>.offlineBannerVisibility(delayMillis: Long): Flow<Boolean> = channelFlow {
    collectLatest { status ->
        if (status == NetworkStatus.OFFLINE) {
            delay(delayMillis)
            send(true)
        } else {
            send(false)
        }
    }
}.distinctUntilChanged()
