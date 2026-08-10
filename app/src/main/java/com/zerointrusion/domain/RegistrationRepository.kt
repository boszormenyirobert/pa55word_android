package com.zerointrusion.domain

import com.zerointrusion.data.network.NewDeviceRegistrationDto
import kotlinx.coroutines.flow.Flow

interface RegistrationRepository {
    fun observeRegistrationState(): Flow<RegistrationState?>

    suspend fun register(input: RegistrationFormInput): RegistrationResult

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