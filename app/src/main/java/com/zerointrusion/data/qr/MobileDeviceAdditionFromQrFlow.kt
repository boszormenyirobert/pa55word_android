package com.zerointrusion.data.qr

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.MobileDeviceAdditionDekDto
import com.zerointrusion.data.network.MobileDeviceAdditionDeviceDto
import com.zerointrusion.data.network.MobileDeviceAdditionRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildMobileDeviceRegistrationUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.common.serverFailure
import com.zerointrusion.data.repository.connecteddevices.AccountDetailsStateHolder
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.domain.qr.AdditionalMobileNamespace
import com.zerointrusion.data.storage.toNamespaceEntries
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.qr.AdditionMobileDeviceQrPayload
import com.zerointrusion.domain.qr.QrNamespaceRegistrationResult
import com.zerointrusion.domain.registration.RegistrationState
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

internal class MobileDeviceAdditionFromQrFlow(
    private val registrationApi: RegistrationApi,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val dekService: DekService,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    suspend fun register(
        registrationState: RegistrationState,
        payload: AdditionMobileDeviceQrPayload,
        storedEncDek: String,
    ): QrNamespaceRegistrationResult {
        val rawDek = dekService.decryptDek(storedEncDek)
        val encryptedDek = dekService.encryptDek(rawDek, payload.publicKey)
        val knownNamespaces = accountDetailsStateHolder.currentSnapshot()?.namespaces.orEmpty() +
            connectedDevicesLocalStore.currentEntries().toNamespaceEntries()

        payload.namespaceUrls.forEach { namespaceUrl ->
            val normalizedUrl = namespaceUrl.trim().trimEnd('/')
            val accountId = if (registrationState.namespaceUrl.trim().trimEnd('/').equals(normalizedUrl, ignoreCase = true)) {
                registrationState.accountId
            } else {
                knownNamespaces.firstOrNull {
                    it.namespaceUrl.trim().trimEnd('/').equals(normalizedUrl, ignoreCase = true)
                }?.accountId ?: throw RegistrationFailure.Validation(
                    "No existing account is stored for namespace URL $normalizedUrl.",
                )
            }
            val request = MobileDeviceAdditionRequestDto(
                accountId = accountId,
                signingMobileDeviceId = registrationState.mobileDeviceId,
                device = MobileDeviceAdditionDeviceDto(
                    mobileDeviceId = payload.mobileDeviceId,
                    publicKey = payload.publicKey,
                    deviceName = payload.deviceName,
                ),
                dek = MobileDeviceAdditionDekDto(
                    mobileDeviceId = payload.mobileDeviceId,
                    encDek = encryptedDek,
                ),
                nonce = newRequestNonce(),
            )
            val requestJson = buildMobileDeviceAdditionJson(gson, request)
            val signature = signatureService.signJsonString(requestJson)
            val url = buildMobileDeviceRegistrationUrl(normalizedUrl)
            Log.d("ZeroIntrusionQr", "HTTP POST $url payload=$requestJson")
            val response = try {
                registrationApi.registerMobileDevice(
                    url,
                    signature,
                    requestJson.toRequestBody("application/json; charset=utf-8".toMediaType()),
                )
            } catch (exception: Exception) {
                throw mapNetworkException(url, exception)
            }
            if (!response.isSuccessful) {
                throw serverFailure(gson, response.errorBody()?.charStream(), response.code(), url)
            }
        }

        val secondaryUrls = payload.namespaceUrls.drop(1)
            .map { it.trim().trimEnd('/').lowercase() }
            .toSet()
        val followUpNamespaces = knownNamespaces
            .filter { it.namespaceUrl.trim().trimEnd('/').lowercase() in secondaryUrls }
            .map { namespace ->
                AdditionalMobileNamespace(
                    accountId = namespace.accountId,
                    desktopDeviceId = namespace.desktopDeviceId,
                    namespaceId = namespace.namespaceId,
                    namespace = namespace.namespaceName,
                    url = namespace.namespaceUrl.trim().trimEnd('/'),
                    root = namespace.root,
                )
            }
            .distinct()

        return QrNamespaceRegistrationResult(
            message = "Mobile device added on ${payload.namespaceUrls.size} server(s): ${payload.deviceName} (${payload.mobileDeviceId})",
            followUpQrPayload = followUpNamespaces.takeIf { it.isNotEmpty() }?.let(gson::toJson),
        )
    }
}

internal fun buildMobileDeviceAdditionJson(
    gson: Gson,
    request: MobileDeviceAdditionRequestDto,
): String = buildString {
    append('{')
    append("\"accountId\":")
    append(gson.toJson(request.accountId))
    append(",\"signingMobileDeviceId\":")
    append(gson.toJson(request.signingMobileDeviceId))
    append(",\"device\":{")
    append("\"mobileDeviceId\":")
    append(gson.toJson(request.device.mobileDeviceId))
    append(",\"publicKey\":")
    append(gson.toJson(request.device.publicKey))
    append(",\"deviceName\":")
    append(gson.toJson(request.device.deviceName))
    append("},\"dek\":{")
    append("\"mobileDeviceId\":")
    append(gson.toJson(request.dek.mobileDeviceId))
    append(",\"encDek\":")
    append(gson.toJson(request.dek.encDek))
    append("},\"nonce\":")
    append(gson.toJson(request.nonce))
    append('}')
}