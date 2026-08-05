package com.roeiamor.fitshare.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import androidx.core.content.getSystemService
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

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
     * Returns true when the system service is somehow unavailable: refusing to write because a
     * platform service is missing would be worse than attempting the write and letting the timeout
     * catch it.
     */
    val isOnline: Boolean
        get() {
            val manager = connectivityManager ?: return true
            val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
            return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
        }

    /**
     * Emits the connection state, and again on every change.
     *
     * The same `callbackFlow` plus `awaitClose` shape the Firestore listeners use, and for the same
     * reason: a registered `NetworkCallback` stays registered until it is unregistered, so without
     * that block every subscription would leak one.
     *
     * `distinctUntilChanged` because the system reports several capability changes for what a user
     * would call one event - joining a wifi network fires available, then validated - and the banner
     * must not flicker through them.
     */
    fun observe(): Flow<Boolean> = callbackFlow {
        val manager = connectivityManager
        if (manager == null) {
            trySend(true)
            awaitClose { }
            return@callbackFlow
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                trySend(isOnline)
            }

            override fun onLost(network: Network) {
                trySend(isOnline)
            }

            override fun onCapabilitiesChanged(
                network: Network,
                networkCapabilities: NetworkCapabilities
            ) {
                trySend(isOnline)
            }
        }

        trySend(isOnline)
        manager.registerDefaultNetworkCallback(callback)

        awaitClose { manager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}
