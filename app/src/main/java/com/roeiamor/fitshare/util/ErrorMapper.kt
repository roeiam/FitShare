package com.roeiamor.fitshare.util

import androidx.annotation.StringRes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestoreException
import com.roeiamor.fitshare.R
import java.io.IOException

/**
 * Turns any exception into the Hebrew message the user should see.
 *
 * This is the **only** place in the project that decides what a failure says. A Fragment never
 * inspects an exception, and no error text is written anywhere else - that is what keeps the wording
 * consistent and makes it possible to change a message once.
 *
 * It returns a string resource rather than a `String` so it needs no `Context`: ViewModels can call
 * it, and it is unit testable on the JVM.
 *
 * The messages follow the SPEC section 8 voice rule: say what happened and what to do. A vague
 * "something went wrong" is the last resort, used only when the cause genuinely is not known.
 */
object ErrorMapper {

    /**
     * Maps [error] to the resource id of a Hebrew message.
     *
     * @return a specific message when the cause is recognised, `error_generic` otherwise.
     */
    @StringRes
    fun toMessageRes(error: Throwable?): Int = when (error) {
        // No connection. Checked first: offline is the most common failure on a phone, and several
        // of the cases below can also surface while offline.
        is FirebaseNetworkException -> R.string.error_no_network
        is IOException -> R.string.error_no_network

        // Registration: the address already belongs to an account.
        is FirebaseAuthUserCollisionException -> R.string.error_email_in_use

        // Registration: Firebase rejected the password as too weak.
        is FirebaseAuthWeakPasswordException -> R.string.error_short_password

        // Sign-in: no such account, or it has been disabled.
        is FirebaseAuthInvalidUserException -> when (error.errorCode) {
            ERROR_USER_DISABLED -> R.string.error_user_disabled
            else -> R.string.error_wrong_credentials
        }

        // Sign-in: the password is wrong, or the address is malformed. Deliberately the same message
        // as "no such user" - telling an attacker which addresses are registered is not helpful.
        is FirebaseAuthInvalidCredentialsException -> R.string.error_wrong_credentials

        // The Firestore codes are named inline rather than collected into a property. A property
        // would be initialised when this object first loads, which forces the Firebase enum to
        // initialise too - and that fails outside Android, taking every unit test with it.
        // Inside a branch they are only touched when the branch actually runs.
        is FirebaseFirestoreException -> when (error.code) {
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED -> R.string.error_no_network

            else -> R.string.error_generic
        }

        else -> mapByMessage(error)
    }

    /**
     * Last resort for exceptions that carry their reason only in text.
     *
     * Firebase Auth reports rate limiting as a plain `FirebaseException` whose message contains
     * `TOO_MANY_ATTEMPTS_TRY_LATER`, with no dedicated subclass to match on. Rather than let that
     * become a generic message, it is recognised here - and everything genuinely unknown falls
     * through to `error_generic`.
     */
    @StringRes
    private fun mapByMessage(error: Throwable?): Int {
        val message = error?.message.orEmpty()
        return when {
            message.contains(ERROR_TOO_MANY_REQUESTS, ignoreCase = true) ->
                R.string.error_too_many_attempts

            message.contains(ERROR_NETWORK_REQUEST_FAILED, ignoreCase = true) ->
                R.string.error_no_network

            else -> R.string.error_generic
        }
    }

    private const val ERROR_USER_DISABLED = "ERROR_USER_DISABLED"
    private const val ERROR_TOO_MANY_REQUESTS = "TOO_MANY_ATTEMPTS_TRY_LATER"
    private const val ERROR_NETWORK_REQUEST_FAILED = "NETWORK_REQUEST_FAILED"
}
