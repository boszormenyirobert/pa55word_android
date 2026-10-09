package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.Gson
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRegistrationAvailabilityTest {
    private val gson = Gson()

    @Test
    fun `missing device response removes only the target registration`() {
        assertTrue(isRemovedAccountRegistration(401, """{"internalCode":"e_40101_2"}""", gson))
        assertTrue(isRemovedAccountRegistration(410, null, gson))
    }

    @Test
    fun `invalid signatures and nonce errors must not delete registrations`() {
        assertFalse(isRemovedAccountRegistration(401, """{"internalCode":"e_40101_1"}""", gson))
        assertFalse(isRemovedAccountRegistration(401, """{"internalCode":"e_40101_0"}""", gson))
        assertFalse(isRemovedAccountRegistration(401, null, gson))
    }

    @Test
    fun `network routing and malformed responses must remain explicit errors`() {
        assertFalse(isRemovedAccountRegistration(404, null, gson))
        assertFalse(isRemovedAccountRegistration(500, """{"internalCode":"e_40101_2"}""", gson))
        assertFalse(isRemovedAccountRegistration(401, "not-json", gson))
    }
}
