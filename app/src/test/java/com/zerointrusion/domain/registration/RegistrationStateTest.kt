package com.zerointrusion.domain.registration

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationStateTest {
    private val gson = Gson()
    private val state = RegistrationState(
        accountId = "account",
        mobileDeviceId = "mobile",
        deviceName = "Phone",
        namespaceName = "Database",
        namespaceUrl = "https://example.test",
        namespaceId = "namespace",
        email = "user@example.test",
        encDek = "encrypted-key",
    )

    @Test
    fun newRegistrationDefaultsToPrivate() {
        assertFalse(state.corporateMode)
    }

    @Test
    fun existingRegistrationDefaultsToPrivate() {
        val json = gson.toJsonTree(state).asJsonObject.apply { remove("corporateMode") }

        assertEquals(state, gson.fromJson(json, RegistrationState::class.java))
    }

    @Test
    fun selectedModeSurvivesSerializationWithoutChangingRegistration() {
        listOf(true, false).forEach { corporateMode ->
            val updated = state.copy(corporateMode = corporateMode)

            assertEquals(updated, gson.fromJson(gson.toJson(updated), RegistrationState::class.java))
        }
    }

    @Test
    fun legacyPhoneIsIgnoredAndNeverSerialized() {
        val legacy = gson.toJsonTree(state).asJsonObject.apply {
            addProperty("phone", "+36123456789")
        }
        val restored = gson.fromJson(legacy, RegistrationState::class.java)

        assertEquals(state, restored)
        assertFalse(gson.toJsonTree(restored).asJsonObject.has("phone"))
    }

    @Test
    fun registrationValidationDoesNotRequirePhone() {
        val input = RegistrationFormInput(
            email = "user@example.test",
            namespaceName = "Database",
            apiUrl = "https://example.test",
        )

        assertTrue(RegistrationValidator().validateForm(input).isValid)
        assertFalse(gson.toJsonTree(input).asJsonObject.has("phone"))
    }
}