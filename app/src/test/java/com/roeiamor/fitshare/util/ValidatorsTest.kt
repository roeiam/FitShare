package com.roeiamor.fitshare.util

import com.roeiamor.fitshare.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Unit tests for [Validators].
 *
 * These run on the JVM with no emulator, which is only possible because the validators return
 * string resource ids instead of formatted text and use a local email pattern rather than
 * `android.util.Patterns`.
 */
class ValidatorsTest {

    // ---- Name ------------------------------------------------------------------------------

    @Test
    fun `name is required`() {
        assertEquals(R.string.error_required_field, Validators.validateName(""))
    }

    @Test
    fun `name of only whitespace is treated as empty`() {
        assertEquals(R.string.error_required_field, Validators.validateName("   "))
    }

    @Test
    fun `name shorter than two characters is rejected`() {
        assertEquals(R.string.error_name_length, Validators.validateName("א"))
    }

    @Test
    fun `valid Hebrew name is accepted`() {
        assertNull(Validators.validateName("רועי עמור"))
    }

    // ---- Email -----------------------------------------------------------------------------

    @Test
    fun `email is required`() {
        assertEquals(R.string.error_required_field, Validators.validateEmail(""))
    }

    @Test
    fun `email without an at sign is rejected`() {
        assertEquals(R.string.error_invalid_email, Validators.validateEmail("roei.example.com"))
    }

    @Test
    fun `email without a domain suffix is rejected`() {
        assertEquals(R.string.error_invalid_email, Validators.validateEmail("roei@example"))
    }

    @Test
    fun `valid email is accepted`() {
        assertNull(Validators.validateEmail("roei@example.com"))
    }

    @Test
    fun `surrounding whitespace does not invalidate an email`() {
        assertNull(Validators.validateEmail("  roei@example.com  "))
    }

    // ---- Password --------------------------------------------------------------------------

    @Test
    fun `password is required`() {
        assertEquals(R.string.error_required_field, Validators.validatePassword(""))
    }

    @Test
    fun `password shorter than six characters is rejected`() {
        assertEquals(R.string.error_short_password, Validators.validatePassword("12345"))
    }

    @Test
    fun `password of exactly six characters is accepted`() {
        assertNull(Validators.validatePassword("123456"))
    }

    // ---- Password confirmation ---------------------------------------------------------------

    @Test
    fun `confirmation is required`() {
        assertEquals(
            R.string.error_required_field,
            Validators.validatePasswordConfirmation(password = "123456", confirmation = "")
        )
    }

    @Test
    fun `mismatched confirmation is rejected`() {
        assertEquals(
            R.string.error_password_mismatch,
            Validators.validatePasswordConfirmation(password = "123456", confirmation = "123457")
        )
    }

    @Test
    fun `matching confirmation is accepted`() {
        assertNull(
            Validators.validatePasswordConfirmation(password = "123456", confirmation = "123456")
        )
    }
}
