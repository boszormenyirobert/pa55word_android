package com.zerointrusion.data.repository.connecteddevices

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.ACCOUNT_LOG_TAG
import com.zerointrusion.data.repository.common.RootDeviceFetchSignaturePayload
import com.zerointrusion.data.repository.common.buildConnectedDevicesUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

class ConnectedDevicesRepositoryImpl(
    private val registrationApi: RegistrationApi,
    private val stateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
) : ConnectedDevicesRepository {
    private val connectedDevicesResponseParser = ConnectedDevicesResponseParser(gson)

    override suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup> = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        if (registrationState.accountId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid accountId.")
        }
        if (registrationState.deviceId.isBlank()) {
            throw RegistrationFailure.Validation("The stored registration state does not contain a valid deviceId.")
        }
        val nonce = newRequestNonce()

        val requestUrl = buildConnectedDevicesUrl(
            baseUrl = registrationState.namespaceUrl,
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
            nonce = nonce,
        )
        val signaturePayload = RootDeviceFetchSignaturePayload(
            accountId = registrationState.accountId,
            deviceId = registrationState.deviceId,
            nonce = nonce,
        )
        val signaturePayloadJson = gson.toJson(signaturePayload)
        val signature = signatureService.signJsonString(signaturePayloadJson)

        Log.d(ACCOUNT_LOG_TAG, "ACCOUNT details fetch start. url=$requestUrl")
        val response = try {
            registrationApi.getAccountDetails(
                url = requestUrl,
                signature = signature,
            )
        } catch (exception: Exception) {
            Log.e(ACCOUNT_LOG_TAG, "ACCOUNT details fetch failed before HTTP response. url=$requestUrl", exception)
            throw mapNetworkException(requestUrl, exception)
        }

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            Log.e(ACCOUNT_LOG_TAG, "ACCOUNT details fetch failed. http=${response.code()} url=$requestUrl")
            throw RegistrationFailure.Server(
                errorBody ?: "Request failed with HTTP ${response.code()} at $requestUrl.",
            )
        }

        val body = response.body()?.string()?.trim().orEmpty()
        if (body.isEmpty()) {
            throw RegistrationFailure.Server("The backend returned an empty account response from $requestUrl.")
        }

        val groups = connectedDevicesResponseParser.parse(body)
        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details parsed response. url=$requestUrl groups=${connectedDevicesResponseParser.summarize(groups)}",
        )

        groups
    }
}