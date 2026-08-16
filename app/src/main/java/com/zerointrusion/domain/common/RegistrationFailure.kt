package com.zerointrusion.domain.common

sealed class RegistrationFailure(message: String) : Exception(message) {
    class Validation(message: String) : RegistrationFailure(message)
    class Security(message: String) : RegistrationFailure(message)
    class Network(message: String) : RegistrationFailure(message)
    class Server(message: String) : RegistrationFailure(message)
    class Unexpected(message: String) : RegistrationFailure(message)
}