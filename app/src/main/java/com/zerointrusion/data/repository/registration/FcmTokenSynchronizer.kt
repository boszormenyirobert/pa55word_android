package com.zerointrusion.data.repository.registration

import android.util.Log
import com.zerointrusion.data.firebase.FirebaseService
import com.zerointrusion.data.repository.common.REG_LOG_TAG
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal class FcmTokenSynchronizer(
    private val firebaseService: FirebaseService,
    private val stateStore: RegistrationStateStore,
) {
    suspend fun sync(state: RegistrationState): RegistrationState = withContext(Dispatchers.IO) {
        val currentFcmToken = try {
            firebaseService.getFcmToken()
        } catch (exception: Exception) {
            throw RegistrationFailure.Unexpected(
                exception.message ?: "FCM token fetch failed: ${exception::class.java.simpleName}",
            )
        }

        try {
            firebaseService.registerFcmToken(
                namespaceUrl = state.namespaceUrl,
                accountId = state.accountId,
                mobileDeviceId = state.mobileDeviceId,
                fcmToken = currentFcmToken,
            )
        } catch (exception: Exception) {
            throw RegistrationFailure.Unexpected(
                exception.message ?: "FCM backend upload failed: ${exception::class.java.simpleName}",
            )
        }

        val syncedState = if (state.fcmToken == currentFcmToken) {
            state
        } else {
            state.copy(fcmToken = currentFcmToken)
        }

        if (syncedState != state) {
            stateStore.save(syncedState)
        }

        syncedState
    }

    suspend fun syncNamespace(
        namespaceEntry: NamespaceEntry,
        registrationState: RegistrationState,
    ) = withContext(Dispatchers.IO) {
        val currentFcmToken = runCatching { firebaseService.getFcmToken() }
            .onFailure { exception ->
                Log.w(
                    REG_LOG_TAG,
                    "NAMESPACE FCM token refresh failed. namespaceId=${namespaceEntry.namespaceId}",
                    exception,
                )
            }
            .getOrNull()
            ?: registrationState.fcmToken

        if (currentFcmToken.isNullOrBlank()) {
            Log.w(REG_LOG_TAG, "NAMESPACE FCM sync skipped: no FCM token is available for namespaceId=${namespaceEntry.namespaceId}")
            return@withContext
        }

        if (registrationState.fcmToken != currentFcmToken) {
            stateStore.save(registrationState.copy(fcmToken = currentFcmToken))
        }

        runCatching {
            firebaseService.registerFcmToken(
                namespaceUrl = namespaceEntry.namespaceUrl,
                accountId = namespaceEntry.accountId,
                mobileDeviceId = registrationState.mobileDeviceId,
                fcmToken = currentFcmToken,
            )
        }.onSuccess {
            Log.d(REG_LOG_TAG, "NAMESPACE FCM sync success. namespaceId=${namespaceEntry.namespaceId}")
        }.onFailure { exception ->
            Log.w(
                REG_LOG_TAG,
                "NAMESPACE FCM sync failed. namespaceId=${namespaceEntry.namespaceId}",
                exception,
            )
        }
    }
}