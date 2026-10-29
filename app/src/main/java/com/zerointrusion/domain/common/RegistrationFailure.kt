package com.zerointrusion.domain.common

sealed class RegistrationFailure(message: String) : Exception(message) {
    class Validation(message: String) : RegistrationFailure(message)

    class Security(message: String) : RegistrationFailure(message)

    class BiometricAuthenticationRequired :
        RegistrationFailure(
            "SECURITY_BIOMETRIC_REQUIRED: Authenticate with a strong biometric to continue."
        )

    class DeviceKeyInvalidated :
        RegistrationFailure(
            "SECURITY_KEY_REENROLLMENT_REQUIRED: The device key is missing, invalidated, or does not meet the biometric policy. Register the device again."
        )

    class Network(message: String) : RegistrationFailure(message)

    class Server(message: String) : RegistrationFailure(message)

    class Unexpected(message: String) : RegistrationFailure(message)
}
