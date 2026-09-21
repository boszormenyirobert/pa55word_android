package com.zerointrusion.data.repository.common

import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.domain.common.RegistrationFailure
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException
import java.util.UUID

internal const val QR_LOG_TAG = "ZeroIntrusionQr"
internal const val REG_LOG_TAG = "ZeroIntrusionRegistration"
internal const val ACCOUNT_LOG_TAG = "ZeroIntrusionAccount"
internal const val AUTHENTICATION_LOG_TAG = "ZeroIntrusionAuthentication"

internal data class RootDeviceFetchSignaturePayload(
    val accountId: String,
    val deviceId: String,
    val nonce: String,
)

internal data class AuthenticationSignedRequestPayload(
    val accountId: String,
    val mobileDeviceId: String,
    val nonce: String,
)

internal data class AuthenticationSessionRevocationSignedRequestPayload(
    val accountId: String,
    val deviceId: String,
    val desktopId: String,
    val attestationId: String,
    val nonce: String,
)

internal fun buildRegistrationUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.REGISTRATION_ENDPOINT_PATH

internal fun buildAuthenticationResponseUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.AUTHENTICATION_RESPONSE_ENDPOINT_PATH

internal fun buildAuthenticationDirectResponseUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.AUTHENTICATION_DIRECT_RESPONSE_ENDPOINT_PATH

internal fun buildAuthenticationSessionsUrl(
    baseUrl: String,
    accountId: String,
    mobileDeviceId: String,
    nonce: String,
): String = baseUrl.trim().trimEnd('/') +
    AppConstants.AUTHENTICATION_ENDPOINT_PATH + "/" + accountId.trim() +
    "?mobileDeviceId=" + mobileDeviceId.trim() + "&nonce=" + nonce.trim()

internal fun buildAuthenticationSessionRevocationUrl(
    baseUrl: String,
    accountId: String,
    desktopDeviceId: String,
    attestationId: String,
    nonce: String,
): String = baseUrl.trim().trimEnd('/') +
    AppConstants.AUTHENTICATION_ENDPOINT_PATH + "/" + accountId.trim() + "/" +
    desktopDeviceId.trim() + "/" + attestationId.trim() + "?nonce=" + nonce.trim()

internal fun buildRootDeviceRegistrationUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.ROOT_DEVICE_ENDPOINT_PATH

internal fun buildMobileDeviceRegistrationUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.MOBILE_DEVICE_ENDPOINT_PATH

internal fun buildMobileDeviceBootstrapUrl(
    baseUrl: String,
    mobileDeviceId: String,
    nonce: String,
): String = baseUrl.trim().trimEnd('/') + AppConstants.MOBILE_DEVICE_ENDPOINT_PATH + "/" +
    mobileDeviceId.trim() + "/registration?nonce=" + nonce.trim()

internal fun buildRootDeviceFetchUrl(
    baseUrl: String,
    nonce: String,
): String = baseUrl.trim().trimEnd('/') +
    AppConstants.ROOT_DEVICE_ENDPOINT_PATH + "?nonce=" + nonce.trim()

internal fun buildConnectedDevicesUrl(
    baseUrl: String,
    accountId: String,
    mobileDeviceId: String,
    nonce: String,
): String = baseUrl.trim().trimEnd('/') +
    AppConstants.REGISTRATION_ENDPOINT_PATH + "/" + accountId.trim() + "/" + mobileDeviceId.trim() +
    "?nonce=" + nonce.trim()

internal fun buildSignedAccountMobileDeviceNonceJson(
    gson: Gson,
    accountId: String,
    mobileDeviceId: String,
    nonce: String,
): String = buildString {
    append('{')
    append("\"accountId\":")
    append(gson.toJson(accountId))
    append(',')
    append("\"mobileDeviceId\":")
    append(gson.toJson(mobileDeviceId))
    append(',')
    append("\"nonce\":")
    append(gson.toJson(nonce))
    append('}')
}

internal fun buildSignedAccountDeviceNonceJson(
    gson: Gson,
    accountId: String,
    deviceId: String,
    nonce: String,
): String = buildString {
    append('{')
    append("\"accountId\":")
    append(gson.toJson(accountId))
    append(',')
    append("\"deviceId\":")
    append(gson.toJson(deviceId))
    append(',')
    append("\"nonce\":")
    append(gson.toJson(nonce))
    append('}')
}

internal fun newRequestNonce(): String = UUID.randomUUID().toString().replace("-", "")

internal fun serverFailure(
    gson: Gson,
    errorBodyReader: java.io.Reader?,
    statusCode: Int,
    url: String,
): RegistrationFailure.Server {
    val errorBody = errorBodyReader?.use {
        runCatching { gson.fromJson(it, AccountErrorResponseDto::class.java) }.getOrNull()
    }
    return RegistrationFailure.Server(
        errorBody?.message ?: "Request failed with HTTP $statusCode at $url.",
    )
}

internal fun mapNetworkException(url: String, exception: Exception): RegistrationFailure = when (exception) {
    is RegistrationFailure -> exception
    is UnknownServiceException -> {
        if (exception.message?.contains("CLEARTEXT", ignoreCase = true) == true) {
            RegistrationFailure.Network(
                "HTTP cleartext traffic is blocked for $url. Use HTTPS or allow cleartext traffic for the app.",
            )
        } else {
            RegistrationFailure.Network("The server connection to $url could not be established.")
        }
    }
    is ConnectException -> RegistrationFailure.Network("The host is reachable, but the backend is not accepting connections at $url.")
    is UnknownHostException -> RegistrationFailure.Network("The host name or IP address for $url could not be resolved from the device.")
    is SocketTimeoutException -> RegistrationFailure.Network("The request to $url timed out before the backend responded.")
    is IOException -> RegistrationFailure.Network("No internet connection or the server is unreachable at $url.")
    else -> RegistrationFailure.Unexpected(exception.message ?: "Request failed unexpectedly.")
}

internal fun buildSignedMobileDeviceNonceJson(
    gson: Gson,
    mobileDeviceId: String,
    nonce: String,
): String = buildString {
    append('{')
    append("\"mobileDeviceId\":")
    append(gson.toJson(mobileDeviceId))
    append(',')
    append("\"nonce\":")
    append(gson.toJson(nonce))
    append('}')
}
