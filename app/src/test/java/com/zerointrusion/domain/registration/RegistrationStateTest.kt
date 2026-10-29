package com.zerointrusion.domain.registration

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationStateTest {
    private val gson = Gson()
    private val state =
        RegistrationState(
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
    fun newRegistrationDefaultsToBasic() {
        assertEquals(ServicePlan.BASIC, state.servicePlan)
    }

    @Test
    fun selectedServicePlanSurvivesSerializationWithoutChangingRegistration() {
        ServicePlan.entries.forEach { servicePlan ->
            val updated = state.copy(servicePlan = servicePlan)

            assertEquals(
                updated,
                gson.fromJson(gson.toJson(updated), RegistrationState::class.java),
            )
            assertEquals(
                servicePlan.name.lowercase(),
                gson.toJsonTree(updated).asJsonObject.get("servicePlan").asString,
            )
        }
    }

    @Test
    fun legacyPhoneIsIgnoredAndNeverSerialized() {
        val legacy =
            gson.toJsonTree(state).asJsonObject.apply { addProperty("phone", "+36123456789") }
        val restored = gson.fromJson(legacy, RegistrationState::class.java)

        assertEquals(state, restored)
        assertFalse(gson.toJsonTree(restored).asJsonObject.has("phone"))
    }

    @Test
    fun registrationValidationDoesNotRequirePhone() {
        val input =
            RegistrationFormInput(
                email = "user@example.test",
                namespaceName = "Database",
                apiUrl = "https://example.test",
            )

        assertTrue(RegistrationValidator().validateForm(input).isValid)
        assertFalse(gson.toJsonTree(input).asJsonObject.has("phone"))
    }
}
