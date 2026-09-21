package com.zerointrusion.presentation.registration

data class RegistrationUiState(
    val email: String = "",
    val phone: String = "",
    val namespaceName: String = "",
    val apiUrl: String = "",
    val additionalApiUrl: String = "",
    val thirdApiUrl: String = "",
    val emailError: String? = null,
    val phoneError: String? = null,
    val namespaceNameError: String? = null,
    val apiUrlError: String? = null,
    val additionalApiUrlError: String? = null,
    val thirdApiUrlError: String? = null,
    val isSubmitting: Boolean = false,
    val isAwaitingMobileRegistration: Boolean = false,
    val mobileRegistrationQrPayload: String? = null,
    val errorMessage: String? = null,
)