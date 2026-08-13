package com.zerointrusion.domain

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

data class NamespaceEntry(
    val namespaceName: String,
    val namespaceId: String,
    val namespaceUrl: String,
    val accountId: String,
    val deviceId: String,
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

data class NamespaceFormValidation(
    val namespaceNameError: String? = null,
    val namespaceUrlError: String? = null,
) {
    val isValid: Boolean =
        namespaceNameError == null &&
            namespaceUrlError == null
}

sealed class RegistrationFailure(message: String) : Exception(message) {
    class Validation(message: String) : RegistrationFailure(message)
    class Security(message: String) : RegistrationFailure(message)
    class Network(message: String) : RegistrationFailure(message)
    class Server(message: String) : RegistrationFailure(message)
    class Unexpected(message: String) : RegistrationFailure(message)
}