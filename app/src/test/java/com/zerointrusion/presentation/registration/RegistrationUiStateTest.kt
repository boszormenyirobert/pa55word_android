package com.zerointrusion.presentation.registration

import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.registration.RegistrationFormInput
import com.zerointrusion.domain.registration.RegistrationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RegistrationUiStateTest {
    @Test
    fun registrationInputUsesProductionDefaultsAndPreservesEnteredEmail() {
        val state = RegistrationUiState(email = " test@example.com ")

        assertEquals(
            RegistrationFormInput(
                email = state.email,
                namespaceName = "Pa55word basic",
                apiUrl = AppConstants.PRODUCTION_API_BASE_URL,
            ),
            state.toRegistrationInput(),
        )
        assertEquals("https://prod-api.pa55word.com", state.toRegistrationInput().apiUrl)
    }

    @Test
    fun productionRegistrationInputPassesExistingValidation() {
        val input = RegistrationUiState(email = "test@example.com").toRegistrationInput()

        assertTrue(RegistrationValidator().validateForm(input).isValid)
    }

    @Test
    fun missingEmailStillFailsValidationWithoutNamespaceOrApiUrlErrors() {
        val validation =
            RegistrationValidator().validateForm(RegistrationUiState().toRegistrationInput())

        assertFalse(validation.isValid)
        assertEquals("Email is required.", validation.emailError)
        assertNull(validation.namespaceNameError)
        assertNull(validation.apiUrlError)
    }
}
