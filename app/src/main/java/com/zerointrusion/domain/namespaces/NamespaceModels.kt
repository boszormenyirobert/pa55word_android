package com.zerointrusion.domain.namespaces

data class NamespaceEntry(
    val namespaceName: String,
    val namespaceId: String,
    val namespaceUrl: String,
    val accountId: String,
    val deviceId: String,
    val attestationId: String? = null,
    val deviceName: String? = null,
    val publicKey: String? = null,
)

data class NamespaceFormValidation(
    val namespaceNameError: String? = null,
    val namespaceUrlError: String? = null,
) {
    val isValid: Boolean =
        namespaceNameError == null &&
            namespaceUrlError == null
}