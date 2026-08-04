package com.roeiamor.fitshare.util

import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.roeiamor.fitshare.R
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.UnknownHostException

/**
 * Unit tests for [ErrorMapper].
 *
 * The point of these is the promise in CLAUDE.md: every failure the user can cause has a specific
 * Hebrew message, and `error_generic` is reserved for causes we genuinely do not recognise.
 */
class ErrorMapperTest {

    @Test
    fun `firebase network exception maps to no network`() {
        val error = FirebaseNetworkException("offline")
        assertEquals(R.string.error_no_network, ErrorMapper.toMessageRes(error))
    }

    @Test
    fun `io exception maps to no network`() {
        assertEquals(R.string.error_no_network, ErrorMapper.toMessageRes(IOException()))
    }

    @Test
    fun `unknown host maps to no network`() {
        assertEquals(R.string.error_no_network, ErrorMapper.toMessageRes(UnknownHostException()))
    }

    @Test
    fun `user collision maps to email already in use`() {
        val error = FirebaseAuthUserCollisionException("ERROR_EMAIL_ALREADY_IN_USE", "taken")
        assertEquals(R.string.error_email_in_use, ErrorMapper.toMessageRes(error))
    }

    @Test
    fun `invalid credentials maps to wrong credentials`() {
        val error = FirebaseAuthInvalidCredentialsException("ERROR_WRONG_PASSWORD", "bad password")
        assertEquals(R.string.error_wrong_credentials, ErrorMapper.toMessageRes(error))
    }

    @Test
    fun `rate limiting is recognised from the message text`() {
        val error = RuntimeException("TOO_MANY_ATTEMPTS_TRY_LATER : try again later")
        assertEquals(R.string.error_too_many_attempts, ErrorMapper.toMessageRes(error))
    }

    @Test
    fun `network failure reported only in text still maps to no network`() {
        val error = RuntimeException("A network error (such as NETWORK_REQUEST_FAILED) occurred")
        assertEquals(R.string.error_no_network, ErrorMapper.toMessageRes(error))
    }

    @Test
    fun `an unrecognised error falls back to the generic message`() {
        assertEquals(R.string.error_generic, ErrorMapper.toMessageRes(IllegalStateException("?")))
    }

    @Test
    fun `a null error falls back to the generic message`() {
        assertEquals(R.string.error_generic, ErrorMapper.toMessageRes(null))
    }
}
