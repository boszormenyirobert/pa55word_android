package com.zerointrusion.presentation.registration

import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.registration.RegistrationFormInput

data class RegistrationUiState(
    val email: String = "",
    val emailError: String? = null,
    val isSubmitting: Boolean = false,
    val isAwaitingMobileRegistration: Boolean = false,
    val mobileRegistrationQrPayload: String? = null,
    val errorMessage: String? = null,
) {
    internal fun toRegistrationInput(): RegistrationFormInput =
        RegistrationFormInput(
            email = email,
            namespaceName = "Pa55word basic",
            apiUrl = AppConstants.PRODUCTION_API_BASE_URL,
        )
}
