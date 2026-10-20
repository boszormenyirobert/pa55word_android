package com.zerointrusion.core

object AppConstants {
    const val KEYSTORE_ALIAS = "secure_device_registration_primary"
    const val ENCRYPTED_PREFS_NAME = "zero_intrusion_secure_registration"
    const val ENCRYPTED_PREFS_STATE_KEY = "registration_state"
    const val ENCRYPTED_PREFS_CONNECTED_DEVICES_KEY = "connected_devices_local_state"
    const val ENCRYPTED_PREFS_RECOVERY_PENDING_KEY = "recovery_pending_state"

    const val HEALTH_ENDPOINT_PATH = "/api/v1/healthy"
    const val REGISTRATION_ENDPOINT_PATH = "/api/v1/account"
    const val ROOT_DEVICE_ENDPOINT_PATH = "/api/v1/root-device"
    const val MOBILE_DEVICE_ENDPOINT_PATH = "/api/v1/mobile-device"
    const val FCM_ENDPOINT_PATH = "/api/v1/fcm"
    const val AUTHENTICATION_ENDPOINT_PATH = "/api/v1/authentication"
    const val AUTHENTICATION_RESPONSE_ENDPOINT_PATH = AUTHENTICATION_ENDPOINT_PATH + "/response"
}