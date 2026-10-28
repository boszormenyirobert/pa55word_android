package com.zerointrusion.presentation.registration

data class RegistrationUiState(
    val email: String = "",
    val namespaceName: String = "",
    val apiUrl: String = "",
    val emailError: String? = null,
    val namespaceNameError: String? = null,
    val apiUrlError: String? = null,
    val isSubmitting: Boolean = false,
    val isAwaitingMobileRegistration: Boolean = false,
    val mobileRegistrationQrPayload: String? = null,
    val errorMessage: String? = null,
)