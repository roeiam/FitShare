package com.roeiamor.fitshare.util

import androidx.annotation.StringRes
import com.roeiamor.fitshare.R

/**
 * Client-side validation for every form in the app.
 *
 * Each function returns the string resource of the Hebrew error to show, or **null when the input is
 * valid**. Returning a resource id rather than a formatted string keeps this object free of any
 * `Context`, which means ViewModels can call it directly and it can be unit tested on the JVM
 * without Robolectric or an emulator.
 *
 * These rules are the client half of the contract. Firebase enforces its own on the server, and
 * anything it rejects comes back through [ErrorMapper]. Validating here is about telling the user
 * what is wrong before a round trip, not about security.
 */
object Validators {

    private const val MIN_NAME_LENGTH = 2
    private const val MIN_PASSWORD_LENGTH = 6

    /**
     * Checks a display name: required, and at least [MIN_NAME_LENGTH] characters after trimming, so
     * a name of only spaces does not pass.
     */
    @StringRes
    fun validateName(name: String): Int? {
        val trimmed = name.trim()
        return when {
            trimmed.isEmpty() -> R.string.error_required_field
            trimmed.length < MIN_NAME_LENGTH -> R.string.error_name_length
            else -> null
        }
    }

    /**
     * Checks an email address: required, and matching Android's own email pattern.
     *
     * The pattern is a local copy rather than `android.util.Patterns.EMAIL_ADDRESS`, because that
     * constant lives in the Android framework and reads as null in a plain JVM unit test - which
     * would make this function untestable without an emulator.
     */
    @StringRes
    fun validateEmail(email: String): Int? {
        val trimmed = email.trim()
        return when {
            trimmed.isEmpty() -> R.string.error_required_field
            !EMAIL_PATTERN.matches(trimmed) -> R.string.error_invalid_email
            else -> null
        }
    }

    /** Checks a password: required, and at least [MIN_PASSWORD_LENGTH] characters, as Firebase demands. */
    @StringRes
    fun validatePassword(password: String): Int? = when {
        password.isEmpty() -> R.string.error_required_field
        password.length < MIN_PASSWORD_LENGTH -> R.string.error_short_password
        else -> null
    }

    /** Checks that the confirmation field is filled in and identical to [password]. */
    @StringRes
    fun validatePasswordConfirmation(password: String, confirmation: String): Int? = when {
        confirmation.isEmpty() -> R.string.error_required_field
        confirmation != password -> R.string.error_password_mismatch
        else -> null
    }

    // ---- Workout form (SPEC section 3) ------------------------------------------------------

    private const val MIN_TITLE_LENGTH = 3
    private const val MAX_TITLE_LENGTH = 60
    private const val MAX_DESCRIPTION_LENGTH = 600
    private const val MIN_DURATION_MINUTES = 1
    private const val MAX_DURATION_MINUTES = 600

    /** Checks a workout title: required, 3-60 characters after trimming. */
    @StringRes
    fun validateWorkoutTitle(title: String): Int? {
        val trimmed = title.trim()
        return when {
            trimmed.isEmpty() -> R.string.error_required_field
            trimmed.length !in MIN_TITLE_LENGTH..MAX_TITLE_LENGTH -> R.string.error_title_length
            else -> null
        }
    }

    /** Checks a description: optional, but at most 600 characters. */
    @StringRes
    fun validateDescription(description: String): Int? =
        if (description.trim().length > MAX_DESCRIPTION_LENGTH) {
            R.string.error_description_length
        } else {
            null
        }

    /**
     * Checks the duration field.
     *
     * Takes the raw text rather than an Int because "not a number at all" and "a number out of
     * range" are both errors the user can make, and the field cannot produce an Int for the first.
     * `toIntOrNull` also absorbs a value too large for an Int, which a numeric keyboard allows.
     */
    @StringRes
    fun validateDuration(durationText: String): Int? {
        val trimmed = durationText.trim()
        if (trimmed.isEmpty()) return R.string.error_required_field

        val minutes = trimmed.toIntOrNull() ?: return R.string.error_duration_range
        return if (minutes in MIN_DURATION_MINUTES..MAX_DURATION_MINUTES) {
            null
        } else {
            R.string.error_duration_range
        }
    }

    /**
     * A deliberately ordinary email pattern: some characters, an @, a domain, a dot, a suffix of at
     * least two letters. It is not RFC 5322 - no client-side regex usefully is - and it does not
     * need to be, because the address is only truly validated when Firebase accepts it.
     */
    private val EMAIL_PATTERN = Regex("^[A-Za-z0-9+_.%-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
}
