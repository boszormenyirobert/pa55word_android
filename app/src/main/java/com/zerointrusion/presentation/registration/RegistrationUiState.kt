package com.zerointrusion.presentation.registration

data class RegistrationUiState(
    val email: String = "",
    val phone: String = "",
    val namespaceName: String = "",
    val apiUrl: String = "",
    val emailError: String? = null,
    val phoneError: String? = null,
    val namespaceNameError: String? = null,
    val apiUrlError: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)