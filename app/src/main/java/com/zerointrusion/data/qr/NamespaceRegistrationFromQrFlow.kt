package com.zerointrusion.data.qr

import android.util.Base64
import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountRegistrationDekDto
import com.zerointrusion.data.network.AccountRegistrationDeviceDto
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.AccountRegistrationResponseDto
import com.zerointrusion.data.network.NamespaceDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.UnknownServiceException

internal class NamespaceRegistrationFromQrFlow(
    private val registrationApi: RegistrationApi,
    private val keyStoreService: KeyStoreService,
    private val gson: Gson,
    private val validator: RegistrationValidator,
) {
    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
    }

    suspend fun prepare(
        registrationState: RegistrationState,
        payload: NamespaceQrPayload,
        storedEncDek: String,
    ): QrRegistrationPreparation {
        val namespaceValidation = validator.validateNamespaceForm(payload.namespaceName, payload.namespaceUrl)
        if (!namespaceValidation.isValid) {
            throw RegistrationFailure.Validation(
                namespaceValidation.namespaceNameError ?: namespaceValidation.namespaceUrlError ?: "QR namespace payload is invalid.",
            )
        }

        val normalizedNamespaceName = payload.namespaceName.trim()
        val normalizedNamespaceUrl = payload.namespaceUrl.trim().trimEnd('/')
        val namespaceId = payload.namespaceId.trim()

        val encodedPhonePublicKey = Base64.encodeToString(keyStoreService.getPublicKey().encoded, Base64.NO_WRAP)
        val phoneRegistrationUrl = buildRegistrationUrl(normalizedNamespaceUrl)
        val phoneRegistrationNonce = newRequestNonce()
        val phoneRegistrationRequest = AccountRegistrationRequestDto(
            namespace = NamespaceDto(
                namespaceId = namespaceId,
                name = normalizedNamespaceName,
                url = normalizedNamespaceUrl,
            ),
            device = AccountRegistrationDeviceDto(
                mobileDeviceId = registrationState.mobileDeviceId,
                publicKey = encodedPhonePublicKey,
                deviceName = registrationState.deviceName,
            ),
            dek = AccountRegistrationDekDto(
                mobileDeviceId = registrationState.mobileDeviceId,
                encDek = storedEncDek,
            ),
            email = registrationState.email,
            phone = registrationState.phone,
            nonce = phoneRegistrationNonce,
        )

        val phoneRegistrationResponse = try {
            Log.d(QR_LOG_TAG, "QR phone registration start. url=$phoneRegistrationUrl namespaceId=$namespaceId")
            Log.d(QR_LOG_TAG, "HTTP POST $phoneRegistrationUrl payload=${gson.toJson(phoneRegistrationRequest)}")
            registrationApi.registerAccount(
                url = phoneRegistrationUrl,
                request = phoneRegistrationRequest,
            )
        } catch (exception: Exception) {
            Log.e(QR_LOG_TAG, "QR phone registration failed before HTTP response. url=$phoneRegistrationUrl", exception)
            throw mapNetworkException(phoneRegistrationUrl, exception)
        }

        if (!phoneRegistrationResponse.isSuccessful) {
            Log.e(QR_LOG_TAG, "QR phone registration failed. http=${phoneRegistrationResponse.code()} url=$phoneRegistrationUrl")
            throw serverFailure(phoneRegistrationResponse.errorBody()?.charStream(), phoneRegistrationResponse.code(), phoneRegistrationUrl)
        }

        val rawPhoneRegistrationResponse = phoneRegistrationResponse.body()?.string()?.trim().orEmpty()
        if (rawPhoneRegistrationResponse.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty response from $phoneRegistrationUrl.")
        }
        Log.d(
            QR_LOG_TAG,
            "HTTP POST $phoneRegistrationUrl response=$rawPhoneRegistrationResponse",
        )
        val phoneRegistrationBody = runCatching {
            gson.fromJson(rawPhoneRegistrationResponse, AccountRegistrationResponseDto::class.java)
        }.getOrNull() ?: throw RegistrationFailure.Server(
            "The backend returned an unreadable response from $phoneRegistrationUrl.",
        )
        if (!phoneRegistrationBody.success || phoneRegistrationBody.accountId.isNullOrBlank()) {
            throw RegistrationFailure.Server("The backend did not return a valid account ID from $phoneRegistrationUrl.")
        }
        val targetNamespaceAccountId = phoneRegistrationBody.accountId.trim()
        if (targetNamespaceAccountId.equals(payload.desktopDeviceId.trim(), ignoreCase = true)) {
            throw RegistrationFailure.Server(
                "The backend returned desktopDeviceId as accountId from $phoneRegistrationUrl. accountId=$targetNamespaceAccountId desktopDeviceId=${payload.desktopDeviceId.trim()}.",
            )
        }
        Log.d(
            QR_LOG_TAG,
            "QR phone registration success. url=$phoneRegistrationUrl namespaceId=$namespaceId returnedAccountId=$targetNamespaceAccountId scannedDesktopDeviceId=${payload.desktopDeviceId} localMobileDeviceId=${registrationState.mobileDeviceId}",
        )

        val namespaceEntry = NamespaceEntry(
            namespaceName = normalizedNamespaceName,
            namespaceId = namespaceId,
            namespaceUrl = normalizedNamespaceUrl,
            accountId = targetNamespaceAccountId,
            desktopDeviceId = payload.desktopDeviceId,
            deviceName = payload.deviceName,
            publicKey = payload.publicKey,
            foreignAccountId = targetNamespaceAccountId,
        )

        return QrRegistrationPreparation(
            namespaceEntry = namespaceEntry,
            targetNamespaceAccountId = targetNamespaceAccountId,
            scannedDesktopDeviceId = payload.desktopDeviceId,
            scannedPublicKey = payload.publicKey,
            scannedDeviceName = payload.deviceName,
        )
    }

    private fun buildRegistrationUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.REGISTRATION_ENDPOINT_PATH

    private fun serverFailure(
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

    private fun mapNetworkException(url: String, exception: Exception): RegistrationFailure = when (exception) {
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
}
