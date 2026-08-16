package com.zerointrusion.domain.registration

data class Namespace(
    val namespaceId: String,
    val name: String,
    val url: String,
)

data class Device(
    val deviceId: String,
    val publicKey: String,
)

data class DekPayload(
    val deviceId: String,
    val encDek: String,
)

data class RegistrationPayload(
    val namespace: Namespace,
    val device: Device,
    val dek: DekPayload,
    val email: String,
    val phone: String,
)

data class RegistrationResult(
    val accountId: String,
)

data class RegistrationState(
    val accountId: String,
    val deviceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val namespaceId: String,
    val email: String,
    val phone: String,
    val encDek: String?,
    val fcmToken: String? = null,
)

data class RegistrationFormInput(
    val email: String,
    val phone: String,
    val namespaceName: String,
    val apiUrl: String,
)

data class RegistrationFormValidation(
    val emailError: String? = null,
    val phoneError: String? = null,
    val namespaceNameError: String? = null,
    val apiUrlError: String? = null,
) {
    val isValid: Boolean =
        emailError == null &&
            phoneError == null &&
            namespaceNameError == null &&
            apiUrlError == null
}