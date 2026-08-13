package com.zerointrusion.domain

import com.zerointrusion.data.network.NewDeviceRegistrationDto
import kotlinx.coroutines.flow.Flow

interface RegistrationRepository {
    fun observeRegistrationState(): Flow<RegistrationState?>

    fun observeNamespaces(): Flow<List<NamespaceEntry>>

    suspend fun fetchNamespaces(): List<NamespaceEntry>

    suspend fun register(input: RegistrationFormInput): RegistrationResult

    suspend fun saveNamespace(
        namespaceName: String,
        namespaceUrl: String,
    ): NamespaceEntry

    suspend fun setActiveNamespace(namespace: NamespaceEntry): RegistrationState

    suspend fun syncFcmToken(state: RegistrationState): RegistrationState

    suspend fun registerNewDeviceFromQr(qrPayload: String): NewDeviceRegistrationDto

    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    )

    suspend fun fetchConnectedDevices(): String
}