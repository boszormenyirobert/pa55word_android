package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.qr.ScannedQrPayload
import com.zerointrusion.domain.registration.RegistrationValidator

internal class QrPayloadParser(
    private val gson: Gson,
    private val validator: RegistrationValidator,
) {
    private companion object {
        const val DEVICE_REGISTRATION_TYPE = "deviceRegistration"
        const val NAMESPACE_TYPE = "namespace"

        val DEVICE_ID_REGEX = Regex("^[A-Za-z0-9]{10}$")
    }

    private data class RawQrRegistrationPayload(
        val type: String?,
        val deviceId: String?,
        val publicKey: String?,
        val namespaceId: String?,
        val namespaceName: String?,
        val namespaceUrl: String?,
    )

    fun parse(qrPayload: String): ScannedQrPayload {
        val payload = parseRawPayload(qrPayload)
        val type = payload.type?.trim().orEmpty()
        val deviceId = payload.deviceId?.trim().orEmpty()
        val publicKey = payload.publicKey?.trim().orEmpty()

        validateDeviceId(deviceId)
        validatePublicKey(publicKey)

        return when (type) {
            DEVICE_REGISTRATION_TYPE -> DeviceRegistrationQrPayload(
                deviceId = deviceId,
                publicKey = publicKey,
            )

            NAMESPACE_TYPE -> {
                val namespaceId = payload.namespaceId?.trim().orEmpty()
                val namespaceName = payload.namespaceName?.trim().orEmpty()
                val namespaceUrl = payload.namespaceUrl?.trim().orEmpty()
                if (namespaceId.isEmpty()) {
                    throw RegistrationFailure.Validation("The scanned QR payload does not contain a namespaceId.")
                }
                if (!validator.isValidNamespaceId(namespaceId)) {
                    throw RegistrationFailure.Validation("The scanned QR payload namespaceId is invalid.")
                }
                val validation = validator.validateNamespaceForm(namespaceName, namespaceUrl)
                if (!validation.isValid) {
                    throw RegistrationFailure.Validation(
                        validation.namespaceNameError ?: validation.namespaceUrlError ?: "QR namespace payload is invalid.",
                    )
                }

                NamespaceQrPayload(
                    deviceId = deviceId,
                    publicKey = publicKey,
                    namespaceId = namespaceId,
                    namespaceName = namespaceName,
                    namespaceUrl = namespaceUrl,
                )
            }

            else -> throw RegistrationFailure.Validation(
                "The scanned QR payload type must be either deviceRegistration or namespace.",
            )
        }
    }

    private fun parseRawPayload(qrPayload: String): RawQrRegistrationPayload {
        val trimmedPayload = qrPayload.trim()
        if (trimmedPayload.isBlank()) {
            throw RegistrationFailure.Validation("The scanned QR code did not contain a payload.")
        }

        val payload = try {
            gson.fromJson(trimmedPayload, RawQrRegistrationPayload::class.java)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Validation("The scanned QR code is not valid JSON.")
        }

        return payload ?: throw RegistrationFailure.Validation("The scanned QR code is empty or unreadable.")
    }

    private fun validateDeviceId(deviceId: String) {
        if (deviceId.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a deviceId.")
        }
        if (!DEVICE_ID_REGEX.matches(deviceId)) {
            throw RegistrationFailure.Validation("The scanned QR payload deviceId must be exactly 10 alphanumeric characters.")
        }
    }

    private fun validatePublicKey(publicKey: String) {
        if (publicKey.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a publicKey.")
        }
        if (publicKey.contains("BEGIN PUBLIC KEY") || publicKey.contains("END PUBLIC KEY")) {
            throw RegistrationFailure.Validation("The scanned QR payload publicKey must be Base64 DER data without PEM headers.")
        }
    }
}