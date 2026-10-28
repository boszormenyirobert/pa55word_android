package com.zerointrusion.data.repository.registration

import com.google.gson.Gson
import com.google.gson.JsonParseException
import com.zerointrusion.data.network.AccountRegistrationDekDto
import com.zerointrusion.data.network.AccountRegistrationDeviceDto
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.AccountRegistrationResponseDto
import com.zerointrusion.data.network.NamespaceDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildRegistrationUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.common.serverFailure
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import kotlinx.coroutines.CancellationException
import okio.ByteString.Companion.toByteString

internal class MobileNamespaceRegistrationHandler(
    private val registrationApi: RegistrationApi,
    private val keyStoreService: KeyStoreService,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val gson: Gson,
    private val validator: RegistrationValidator,
) {
    suspend fun register(
        state: RegistrationState,
        namespaceId: String,
        namespaceName: String,
        namespaceUrl: String,
        storedEncDek: String,
        desktopDeviceId: String? = null,
    ): MobileNamespaceRegistration {
        val validation = validator.validateNamespaceForm(namespaceName, namespaceUrl)
        if (!validation.isValid) {
            throw RegistrationFailure.Validation(
                validation.namespaceNameError ?: validation.namespaceUrlError ?: "Invalid namespace.",
            )
        }
        if (!validator.isValidNamespaceId(namespaceId)) {
            throw RegistrationFailure.Validation("The namespaceId is invalid.")
        }
        val url = namespaceUrl.trim().trimEnd('/')
        val requestUrl = buildRegistrationUrl(url)
        val request = AccountRegistrationRequestDto(
            namespace = NamespaceDto(namespaceId.trim(), namespaceName.trim(), url),
            device = AccountRegistrationDeviceDto(
                mobileDeviceId = state.mobileDeviceId,
                publicKey = keyStoreService.getPublicKey().encoded.toByteString().base64(),
                deviceName = state.deviceName,
            ),
            dek = AccountRegistrationDekDto(state.mobileDeviceId, storedEncDek),
            email = state.email,
            nonce = newRequestNonce(),
        )
        val response = try {
            registrationApi.registerAccount(requestUrl, request)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw mapNetworkException(requestUrl, exception)
        }
        if (!response.isSuccessful) {
            throw serverFailure(gson, response.errorBody()?.charStream(), response.code(), requestUrl)
        }
        val rawResponse = response.body()?.string()?.trim().orEmpty()
        if (rawResponse.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty response from $requestUrl.")
        }
        val body = try {
            gson.fromJson(rawResponse, AccountRegistrationResponseDto::class.java)
        } catch (_: JsonParseException) {
            throw RegistrationFailure.Server("The backend returned an unreadable response from $requestUrl.")
        }
        val accountId = body?.accountId?.trim().orEmpty()
        if (body?.success != true || accountId.isEmpty()) {
            throw RegistrationFailure.Server("The backend did not return a valid account ID from $requestUrl.")
        }
        if (desktopDeviceId != null && accountId.equals(desktopDeviceId.trim(), ignoreCase = true)) {
            throw RegistrationFailure.Server(
                "The backend returned desktopDeviceId as accountId from $requestUrl. accountId=$accountId desktopDeviceId=${desktopDeviceId.trim()}.",
            )
        }
        val registration = MobileNamespaceRegistration(
            accountId = accountId,
            mobileDeviceId = state.mobileDeviceId,
            namespaceId = namespaceId.trim(),
            namespaceName = namespaceName.trim(),
            namespaceUrl = url,
            encDek = storedEncDek,
        )
        connectedDevicesLocalStore.updateMobileRegistrations { it + registration }
        return registration
    }
}
