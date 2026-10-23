package com.zerointrusion.data.repository.authentication

import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationApi
import com.zerointrusion.data.repository.common.buildMobileAuthenticationStatusJson
import com.zerointrusion.data.repository.common.buildMobileAuthenticationStatusUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.data.repository.common.newRequestNonce
import com.zerointrusion.data.repository.common.serverFailure
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.domain.authentication.MobileAuthenticationStatus
import com.zerointrusion.domain.common.RegistrationFailure
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class MobileAuthenticationStatusHandler(
    private val authenticationApi: AuthenticationApi,
    private val signatureService: SignatureService,
    private val gson: Gson,
) {
    suspend fun fetch(
        namespaceUrl: String,
        accountId: String,
        mobileDeviceId: String,
        notificationId: String,
    ): MobileAuthenticationStatus = withContext(Dispatchers.IO) {
        val nonce = newRequestNonce()
        val requestUrl = buildMobileAuthenticationStatusUrl(
            baseUrl = namespaceUrl,
            accountId = accountId,
            notificationId = notificationId,
            mobileDeviceId = mobileDeviceId,
            nonce = nonce,
        )
        val requestJson = buildMobileAuthenticationStatusJson(
            gson = gson,
            accountId = accountId,
            mobileDeviceId = mobileDeviceId,
            notificationId = notificationId,
            nonce = nonce,
        )
        val signature = signatureService.signJsonString(requestJson)
        val response = try {
            authenticationApi.fetchMobileAuthenticationStatus(requestUrl, signature)
        } catch (exception: Exception) {
            if (exception is CancellationException) {
                throw exception
            }
            throw mapNetworkException(requestUrl, exception)
        }
        if (!response.isSuccessful) {
            throw serverFailure(gson, response.errorBody()?.charStream(), response.code(), requestUrl)
        }
        val body = response.body()
            ?: throw RegistrationFailure.Server("Authentication status response was empty at $requestUrl.")
        if (body.notificationId?.trim() != notificationId.trim()) {
            throw RegistrationFailure.Server("Authentication status response did not match the requested notification.")
        }
        try {
            MobileAuthenticationStatus.fromWire(body.status.orEmpty())
        } catch (exception: IllegalArgumentException) {
            throw RegistrationFailure.Server(exception.message ?: "Authentication status response was invalid.")
        }
    }
}