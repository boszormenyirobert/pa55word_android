package com.zerointrusion.core

object AppConstants {
    const val KEYSTORE_ALIAS = "secure_device_registration_primary"
    const val ENCRYPTED_PREFS_NAME = "zero_intrusion_secure_registration"
    const val ENCRYPTED_PREFS_STATE_KEY = "registration_state"
    const val ENCRYPTED_PREFS_NAMESPACES_KEY = "namespaces_state"

    const val REGISTRATION_ENDPOINT_PATH = "/api/v1/account"
    const val NAMESPACE_ENDPOINT_PATH = "/api/v1/namespace"

    const val ROOT_DEVICE_ENDPOINT_PATH = "/api/v1/root-device"
    const val FCM_ENDPOINT_PATH = "/api/v1/fcm"
    const val AUTHENTICATION_RESPONSE_ENDPOINT_PATH = "/api/v1/authentication/response"    
}