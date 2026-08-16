package com.zerointrusion.data.repository.common

import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.DekDto
import com.zerointrusion.data.network.DeviceDto
import com.zerointrusion.data.network.NamespaceDto
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.RegistrationPayload
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

internal const val QR_LOG_TAG = "ZeroIntrusionQr"
internal const val REG_LOG_TAG = "ZeroIntrusionRegistration"
internal const val ACCOUNT_LOG_TAG = "ZeroIntrusionAccount"

internal data class RootDeviceFetchSignaturePayload(
    val accountId: String,
    val deviceId: String,
)

internal fun buildRegistrationUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.REGISTRATION_ENDPOINT_PATH

internal fun buildAuthenticationResponseUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.AUTHENTICATION_RESPONSE_ENDPOINT_PATH

internal fun buildRootDeviceRegistrationUrl(baseUrl: String): String =
    baseUrl.trim().trimEnd('/') + AppConstants.ROOT_DEVICE_ENDPOINT_PATH

internal fun buildConnectedDevicesUrl(
    baseUrl: String,
    accountId: String,
    deviceId: String,
): String = baseUrl.trim().trimEnd('/') +
    AppConstants.REGISTRATION_ENDPOINT_PATH + "/" + accountId.trim() + "/" + deviceId.trim()

internal fun RegistrationPayload.toDto(): AccountRegistrationRequestDto = AccountRegistrationRequestDto(
    namespace = NamespaceDto(
        namespaceId = namespace.namespaceId,
        name = namespace.name,
        url = namespace.url,
    ),
    device = DeviceDto(
        deviceId = device.deviceId,
        publicKey = device.publicKey,
    ),
    dek = DekDto(
        deviceId = dek.deviceId,
        encDek = dek.encDek,
    ),
    email = email,
    phone = phone,
)

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