package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.qr.AdditionMobileDeviceQrPayload
import com.zerointrusion.domain.qr.AdditionalMobileNamespace
import com.zerointrusion.domain.qr.AdditionalMobileNamespacesQrPayload
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.qr.NamespaceSyncQrPayload
import com.zerointrusion.domain.qr.ScannedQrPayload
import com.zerointrusion.domain.registration.RegistrationValidator

internal class QrPayloadParser(
    private val gson: Gson,
    private val validator: RegistrationValidator,
) {
    private companion object {
        const val DEVICE_REGISTRATION_TYPE = "deviceRegistration"
        const val NAMESPACE_TYPE = "namespace"
        const val NAMESPACE_SYNC_TYPE = "namespace_sync"
        const val ADDITION_MOBILE_DEVICE_TYPE = "addition_mobile_device"
        const val ADDITIONAL_MOBILE_NAMESPACES_TYPE = "additional_mobile_namespaces"

        val DEVICE_ID_REGEX = Regex("^[A-Za-z0-9]{10}$")
    }

    private data class RawQrRegistrationPayload(
        val type: String?,
        val desktopDeviceId: String?,
        val publicKey: String?,
        val deviceName: String?,
        val namespaceId: String?,
        val namespaceName: String?,
        val namespaceUrl: String?,
        val targetUrl: String?,
        val namespaceUrls: List<String>?,
        val mobileDeviceId: String?,
        val namespaces: List<RawAdditionalMobileNamespace>?,
    )

    private data class RawAdditionalMobileNamespace(
        val accountId: String?,
        val desktopDeviceId: String?,
        val namespaceId: String?,
        val namespace: String?,
        val url: String?,
        val root: Boolean?,
    )

    fun parse(qrPayload: String): ScannedQrPayload {
        if (qrPayload.trim().startsWith("[")) {
            return parseAdditionalMobileNamespaces(qrPayload)
        }
        val payload = parseRawPayload(qrPayload)
        val type = payload.type?.trim().orEmpty()
        return when (type) {
            DEVICE_REGISTRATION_TYPE -> {
                val desktopDeviceId = payload.desktopDeviceId?.trim().orEmpty()
                val publicKey = payload.publicKey?.trim().orEmpty()
                val deviceName = payload.deviceName?.trim().orEmpty()
                val targetUrl = payload.targetUrl?.trim().orEmpty()
                validateDesktopDeviceId(desktopDeviceId)
                validatePublicKey(publicKey)
                validateDeviceName(deviceName)
                if (targetUrl.isEmpty()) {
                    throw RegistrationFailure.Validation("The scanned QR payload does not contain a targetUrl.")
                }
                validateNamespaceUrl(targetUrl)

                DeviceRegistrationQrPayload(
                    desktopDeviceId = desktopDeviceId,
                    publicKey = publicKey,
                    deviceName = deviceName,
                    targetUrl = targetUrl,
                )
            }

            NAMESPACE_TYPE -> {
                val desktopDeviceId = payload.desktopDeviceId?.trim().orEmpty()
                val publicKey = payload.publicKey?.trim().orEmpty()
                val deviceName = payload.deviceName?.trim().orEmpty()
                val namespaceId = payload.namespaceId?.trim().orEmpty()
                val namespaceName = payload.namespaceName?.trim().orEmpty()
                val namespaceUrl = payload.namespaceUrl?.trim().orEmpty()
                validateDesktopDeviceId(desktopDeviceId)
                validatePublicKey(publicKey)
                validateDeviceName(deviceName)
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
                    desktopDeviceId = desktopDeviceId,
                    publicKey = publicKey,
                    deviceName = deviceName,
                    namespaceId = namespaceId,
                    namespaceName = namespaceName,
                    namespaceUrl = namespaceUrl,
                )
            }

            NAMESPACE_SYNC_TYPE -> {
                val desktopDeviceId = payload.desktopDeviceId?.trim().orEmpty()
                val publicKey = payload.publicKey?.trim().orEmpty()
                val deviceName = payload.deviceName?.trim().orEmpty()
                val namespaceId = payload.namespaceId?.trim().orEmpty()
                val namespaceUrl = payload.namespaceUrl?.trim().orEmpty()
                validateDesktopDeviceId(desktopDeviceId)
                validatePublicKey(publicKey)
                validateDeviceName(deviceName)
                validateNamespaceId(namespaceId)
                validateNamespaceUrl(namespaceUrl)

                NamespaceSyncQrPayload(
                    desktopDeviceId = desktopDeviceId,
                    publicKey = publicKey,
                    deviceName = deviceName,
                    namespaceId = namespaceId,
                    namespaceUrl = namespaceUrl,
                )
            }

            ADDITION_MOBILE_DEVICE_TYPE -> {
                val publicKey = payload.publicKey?.trim().orEmpty()
                val deviceName = payload.deviceName?.trim().orEmpty()
                val mobileDeviceId = payload.mobileDeviceId?.trim().orEmpty()
                val namespaceUrls = payload.namespaceUrls.orEmpty()
                    .map { it.trim().trimEnd('/') }
                    .filter { it.isNotEmpty() }
                    .ifEmpty {
                        listOfNotNull(payload.namespaceUrl?.trim()?.trimEnd('/')?.takeIf { it.isNotEmpty() })
                    }
                validateDeviceName(deviceName)
                validatePublicKey(publicKey)
                validateMobileDeviceId(mobileDeviceId)
                if (namespaceUrls.isEmpty() || namespaceUrls.size > 3) {
                    throw RegistrationFailure.Validation("The scanned QR payload must contain between 1 and 3 namespace URLs.")
                }
                namespaceUrls.forEach(::validateNamespaceUrl)

                AdditionMobileDeviceQrPayload(
                    publicKey = publicKey,
                    deviceName = deviceName,
                    mobileDeviceId = mobileDeviceId,
                    namespaceUrls = namespaceUrls,
                )
            }

            ADDITIONAL_MOBILE_NAMESPACES_TYPE -> {
                parseAdditionalMobileNamespaces(gson.toJson(payload.namespaces.orEmpty()))
            }

            else -> throw RegistrationFailure.Validation(
                "The scanned QR payload type is not supported.",
            )
        }
    }

    private fun parseAdditionalMobileNamespaces(qrPayload: String): AdditionalMobileNamespacesQrPayload {
        val listType = object : com.google.gson.reflect.TypeToken<List<RawAdditionalMobileNamespace>>() {}.type
        val rawNamespaces = try {
            gson.fromJson<List<RawAdditionalMobileNamespace>>(qrPayload.trim(), listType)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Validation("The scanned additional namespaces QR code is not valid JSON.")
        }.orEmpty()
        val namespaces = rawNamespaces.map { namespace ->
            val accountId = namespace.accountId?.trim().orEmpty()
            val desktopDeviceId = namespace.desktopDeviceId?.trim().orEmpty()
            val namespaceId = namespace.namespaceId?.trim().orEmpty()
            val namespaceName = namespace.namespace?.trim().orEmpty()
            val namespaceUrl = namespace.url?.trim()?.trimEnd('/').orEmpty()
            val root = namespace.root ?: false
            if (accountId.isEmpty()) {
                throw RegistrationFailure.Validation("The scanned QR payload contains a namespace without accountId.")
            }
            validateDesktopDeviceId(desktopDeviceId)
            validateNamespaceId(namespaceId)
            if (namespaceName.isEmpty()) {
                throw RegistrationFailure.Validation("The scanned QR payload contains a namespace without namespace.")
            }
            validateNamespaceUrl(namespaceUrl)
            AdditionalMobileNamespace(accountId, desktopDeviceId, namespaceId, namespaceName, namespaceUrl, root)
        }
        return AdditionalMobileNamespacesQrPayload(namespaces)
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

    private fun validateDesktopDeviceId(desktopDeviceId: String) {
        if (desktopDeviceId.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a desktopDeviceId.")
        }
        if (!DEVICE_ID_REGEX.matches(desktopDeviceId)) {
            throw RegistrationFailure.Validation("The scanned QR payload desktopDeviceId must be exactly 10 alphanumeric characters.")
        }
    }

    private fun validateMobileDeviceId(mobileDeviceId: String) {
        if (!DEVICE_ID_REGEX.matches(mobileDeviceId)) {
            throw RegistrationFailure.Validation("The scanned QR payload mobileDeviceId must be exactly 10 alphanumeric characters.")
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

    private fun validateDeviceName(deviceName: String) {
        if (deviceName.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a deviceName.")
        }
    }

    private fun validateNamespaceId(namespaceId: String) {
        if (namespaceId.isEmpty()) {
            throw RegistrationFailure.Validation("The scanned QR payload does not contain a namespaceId.")
        }
        if (!validator.isValidNamespaceId(namespaceId)) {
            throw RegistrationFailure.Validation("The scanned QR payload namespaceId is invalid.")
        }
    }

    private fun validateNamespaceUrl(namespaceUrl: String) {
        val validationError = validator.validateNamespaceUrl(namespaceUrl)
        if (validationError != null) {
            throw RegistrationFailure.Validation(validationError)
        }
    }
}