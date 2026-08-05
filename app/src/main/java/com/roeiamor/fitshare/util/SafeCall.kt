package com.roeiamor.fitshare.util

import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs a suspending call and turns any failure into `Result.failure` instead of a thrown exception.
 *
 * This is what keeps the project's promise that no Firebase or network call can crash the app: every
 * data source call goes through here, so a caller always gets a `Result` to handle.
 *
 * It deliberately does **not** use `runCatching`. `runCatching` catches `Throwable`, which includes
 * `CancellationException` - the exception coroutines use to unwind a cancelled job. Swallowing it
 * would leave a cancelled coroutine believing it had merely failed, and a ViewModel cleared
 * mid-request would carry on and touch state that no longer exists. So cancellation is rethrown and
 * only real failures are captured.
 */
suspend fun <T> safeCall(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (error: Throwable) {
        Result.failure(error)
    }
