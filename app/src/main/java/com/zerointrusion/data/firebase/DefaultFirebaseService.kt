package com.zerointrusion.data.firebase

import android.content.Context
import com.google.gson.Gson
import com.google.firebase.FirebaseApp
import com.google.firebase.messaging.FirebaseMessaging
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountErrorResponseDto
import com.zerointrusion.data.network.FcmRegistrationRequestDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.RegistrationFailure
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

@Singleton
class DefaultFirebaseService @Inject constructor(
    private val context: Context,
    private val registrationApi: RegistrationApi,
    private val registrationStateStore: RegistrationStateStore,
    private val signatureService: SignatureService,
    private val gson: Gson,
) : FirebaseService {

    override suspend fun getFcmToken(): String = withContext(Dispatchers.IO) {
        ensureFirebaseInitialized()
        suspendCancellableCoroutine { continuation ->
            FirebaseMessaging.getInstance().token
                .addOnSuccessListener { token ->
                    if (token.isNullOrBlank()) {
                        continuation.resumeWithException(
                            RegistrationFailure.Unexpected("FCM token fetch failed: Firebase returned an empty token."),
                        )
                    } else {
                        continuation.resume(token)
                    }
                }
                .addOnFailureListener { error ->
                    continuation.resumeWithException(
                        RegistrationFailure.Unexpected(
                            "FCM token fetch failed: ${error.message ?: "Firebase Messaging SDK returned an unknown error."}",
                        ),
                    )
                }
        }
    }

    override suspend fun registerFcmToken(
        accountId: String,
        deviceId: String,
        fcmToken: String,
    ) {
        withContext(Dispatchers.IO) {
            val namespaceUrl = registrationStateStore.observeState().first()?.namespaceUrl
                ?: throw RegistrationFailure.Unexpected("FCM backend upload failed: registered namespace URL is unavailable.")
            val fcmUrl = buildFcmUrl(namespaceUrl)

            try {
                val requestPayload = FcmRegistrationRequestDto(
                    accountId = accountId,
                    deviceId = deviceId,
                    fcmToken = fcmToken,
                )
                val requestBodyJson = gson.toJson(requestPayload)
                val signature = signatureService.signJsonString(requestBodyJson)

                val response = registrationApi.registerFcmToken(
                    url = fcmUrl,
                    signature = signature,
                    request = requestPayload,
                )

                if (!response.isSuccessful) {
                    val errorBody = response.errorBody()?.charStream()?.use {
                        gson.fromJson(it, AccountErrorResponseDto::class.java)
                    }
                    val backendMessage = buildString {
                        errorBody?.message?.takeIf { it.isNotBlank() }?.let { append(it) }
                        errorBody?.internalCode?.takeIf { it.isNotBlank() }?.let {
                            if (isNotEmpty()) append(" ")
                            append("[")
                            append(it)
                            append("]")
                        }
                    }.ifBlank { null }
                    throw RegistrationFailure.Server(
                        backendMessage
                            ?: "FCM backend upload failed with HTTP ${response.code()} at $fcmUrl.",
                    )
                }
            } catch (_: ConnectException) {
                throw RegistrationFailure.Network("FCM backend upload failed: the host is reachable, but not accepting connections at $fcmUrl.")
            } catch (_: UnknownHostException) {
                throw RegistrationFailure.Network("FCM backend upload failed: the host name or IP address for $fcmUrl could not be resolved from the device.")
            } catch (_: SocketTimeoutException) {
                throw RegistrationFailure.Network("FCM backend upload failed: the request to $fcmUrl timed out before the backend responded.")
            } catch (_: IOException) {
                throw RegistrationFailure.Network("FCM backend upload failed: no internet connection or the endpoint is unreachable at $fcmUrl.")
            }
        }
    }

    private fun ensureFirebaseInitialized() {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
                ?: throw RegistrationFailure.Unexpected("FCM token fetch failed: Firebase initialization failed for this application.")
        }
    }

    private fun buildFcmUrl(baseUrl: String): String =
        baseUrl.trim().trimEnd('/') + AppConstants.FCM_ENDPOINT_PATH
}