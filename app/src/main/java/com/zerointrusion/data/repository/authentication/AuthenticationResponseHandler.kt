package com.zerointrusion.data.repository.authentication

import android.util.Log
import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationResponseRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.REG_LOG_TAG
import com.zerointrusion.data.repository.common.buildAuthenticationResponseUrl
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

internal class AuthenticationResponseHandler(
    private val registrationApi: RegistrationApi,
    private val namespaceStore: NamespaceStore,
    private val stateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    ) = withContext(Dispatchers.IO) {
        val registrationState = stateStore.observeState().first()
            ?: throw RegistrationFailure.Validation("No registered account is stored on this device.")

        val targetNamespace = namespaceStore.observeNamespaces().first()
            .firstOrNull { it.deviceId == deviceId && it.accountId == accountId }
            ?: throw RegistrationFailure.Validation(
                "No locally stored namespace matches deviceId=$deviceId and accountId=$accountId for authentication response.",
            )
        Log.d(REG_LOG_TAG, "Authentication response start. namespaceDeviceId=${targetNamespace.deviceId} signingDeviceId=${registrationState.deviceId} accountId=${targetNamespace.accountId} approved=$approved")

        val responseUrl = buildAuthenticationResponseUrl(targetNamespace.namespaceUrl)
        val requestPayload = AuthenticationResponseRequestDto(
            notificationId = notificationId,
            accountId = targetNamespace.accountId,
            deviceId = registrationState.deviceId,
            approved = approved,
        )
        val requestBodyJson = gson.toJson(requestPayload)
        val signature = signatureService.signJsonString(requestBodyJson)

        val response = registrationApi.sendAuthenticationResponse(
            url = responseUrl,
            signature = signature,
            request = requestPayload,
        )

        if (!response.isSuccessful) {
            val errorBody = response.errorBody()?.string()
            throw RegistrationFailure.Server(
                errorBody ?: "Authentication response failed with HTTP ${response.code()} at $responseUrl.",
            )
        }
    }

}