package com.roeiamor.fitshare.util

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

/**
 * Raised when a network-bound call could not be made, or did not finish in time.
 *
 * An [IOException] on purpose: [ErrorMapper] already turns those into `error_no_network`, so both
 * failures produce the right Hebrew message without the mapper needing a special case. To the user
 * "there is no connection" and "the request never came back" are the same thing.
 */
class NoConnectionException : IOException("No usable connection, or the request timed out")

/** How long a plain Firestore read or write may take before it is treated as a failure. */
const val FIRESTORE_TIMEOUT_MS = 15_000L

/**
 * Longer, for calls that upload a photo first. Compression plus an upload over mobile data is
 * legitimately slow, and OkHttp already enforces its own timeouts on the upload leg.
 */
const val UPLOAD_TIMEOUT_MS = 120_000L

/**
 * Wraps every network-bound repository call in two protections.
 *
 * **1. Refuse to start when there is no connection.** The Firestore SDK is offline-first: a plain
 * write or a `WriteBatch` is accepted into a local queue and committed whenever the device next
 * reaches the server. `await()` on that task simply never returns while offline. Worse, the write
 * *does* land later - so an app that only used a timeout would tell the user "no internet" and then
 * publish their workout anyway a minute later, which was measured on a physical device and is the
 * reason this check exists. Asking [NetworkMonitor] first makes the message honest: nothing is
 * handed to Firestore, so nothing can be committed behind the user's back.
 *
 * Transactions behave differently - they need a server round trip and fail immediately offline
 * rather than queueing - but relying on that difference would mean every future call had to be
 * classified correctly by whoever wrote it. One rule for all of them is safer.
 *
 * **2. Give up after [timeoutMillis].** The connection can also disappear *during* a call, or be
 * validated but useless. The timeout is the backstop, so the UI can never spin forever.
 *
 * Applied at the **repository** layer rather than per screen, so every caller inherits it and a new
 * screen cannot forget it.
 *
 * @param networkMonitor answers whether a connection exists right now.
 */
class NetworkGuard(private val networkMonitor: NetworkMonitor) {

    /**
     * @param timeoutMillis how long to wait; [FIRESTORE_TIMEOUT_MS] unless the call uploads an image.
     * @param block the repository body, which already returns a `Result`.
     * @return the block's own result, or a failure carrying [NoConnectionException].
     */
    suspend fun <T> run(
        timeoutMillis: Long = FIRESTORE_TIMEOUT_MS,
        block: suspend () -> Result<T>
    ): Result<T> {
        if (!networkMonitor.isOnline) return Result.failure(NoConnectionException())

        return try {
            withTimeout(timeoutMillis) { block() }
        } catch (timeout: TimeoutCancellationException) {
            // Caught explicitly, and *before* any generic CancellationException handling: a timeout
            // is a real failure the user must see, not the ordinary cancellation that is rethrown.
            Result.failure(NoConnectionException())
        }
    }
}
