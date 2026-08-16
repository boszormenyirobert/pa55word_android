package com.zerointrusion.domain.registration

import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceFormValidation
import java.net.URI
import java.net.URISyntaxException
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RegistrationValidator @Inject constructor() {

    fun validateForm(input: RegistrationFormInput): RegistrationFormValidation {
        val normalizedEmail = input.email.trim()
        val normalizedPhone = input.phone.trim()
        val normalizedNamespace = input.namespaceName.trim()
        val normalizedUrl = input.apiUrl.trim()

        return RegistrationFormValidation(
            emailError = when {
                normalizedEmail.isEmpty() -> "Email is required."
                !EMAIL_REGEX.matches(normalizedEmail) -> "Enter a valid email address."
                else -> null
            },
            phoneError = when {
                normalizedPhone.isEmpty() -> "Phone is required."
                !PHONE_REGEX.matches(normalizedPhone) -> "Enter a valid international phone number."
                else -> null
            },
            namespaceNameError = when {
                normalizedNamespace.isEmpty() -> "Namespace name is required."
                else -> null
            },
            apiUrlError = when {
                normalizedUrl.isEmpty() -> "API URL is required."
                !isValidHttpUrl(normalizedUrl) -> "Enter a valid http or https URL."
                !hasSupportedPort(normalizedUrl) -> "Enter the base URL with an explicit port, for example http://192.168.178.20:3000."
                else -> null
            },
        )
    }

    fun validatePayload(payload: RegistrationPayload) {
        if (!DEVICE_ID_REGEX.matches(payload.device.deviceId)) {
            throw RegistrationFailure.Validation("Generated device ID is invalid.")
        }

        if (!NAMESPACE_ID_REGEX.matches(payload.namespace.namespaceId)) {
            throw RegistrationFailure.Validation("Generated namespace ID is invalid.")
        }

        if (payload.dek.deviceId != payload.device.deviceId) {
            throw RegistrationFailure.Validation("Encrypted DEK does not match the device ID.")
        }

        if (!BASE64_REGEX.matches(payload.device.publicKey) || payload.device.publicKey.contains("BEGIN PUBLIC KEY")) {
            throw RegistrationFailure.Validation("Generated public key format is invalid.")
        }
    }

    fun validateNamespaceForm(
        namespaceName: String,
        namespaceUrl: String,
    ): NamespaceFormValidation {
        val normalizedNamespace = namespaceName.trim()
        val normalizedUrl = namespaceUrl.trim()

        return NamespaceFormValidation(
            namespaceNameError = when {
                normalizedNamespace.isEmpty() -> "Namespace name is required."
                else -> null
            },
            namespaceUrlError = when {
                normalizedUrl.isEmpty() -> "Namespace URL is required."
                !isValidHttpUrl(normalizedUrl) -> "Enter a valid http or https URL."
                !hasSupportedPort(normalizedUrl) -> "Enter the base URL with an explicit port, for example http://192.168.178.20:3000."
                else -> null
            },
        )
    }

    fun isValidNamespaceId(namespaceId: String): Boolean = NAMESPACE_ID_REGEX.matches(namespaceId.trim())

    private fun isValidHttpUrl(value: String): Boolean = try {
        val uri = URI(value)
        val scheme = uri.scheme?.lowercase()
        !uri.host.isNullOrBlank() && (scheme == "http" || scheme == "https")
    } catch (_: URISyntaxException) {
        false
    }

    private fun hasSupportedPort(value: String): Boolean = try {
        val uri = URI(value)
        when (uri.scheme?.lowercase()) {
            "http" -> uri.port > 0
            "https" -> uri.port > 0 || uri.port == -1
            else -> false
        }
    } catch (_: URISyntaxException) {
        false
    }

    private companion object {
        val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")
        val PHONE_REGEX = Regex("^\\+?[0-9]{7,15}$")
        val DEVICE_ID_REGEX = Regex("^[A-Za-z0-9]{10}$")
        val NAMESPACE_ID_REGEX = Regex("^[0-9a-f]{10}$")
        val BASE64_REGEX = Regex("^[A-Za-z0-9+/=]+$")
    }
}