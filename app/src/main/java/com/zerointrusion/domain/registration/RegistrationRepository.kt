package com.zerointrusion.domain.registration

import kotlinx.coroutines.flow.Flow

interface RegistrationRepository {
    fun observeRegistrationState(): Flow<RegistrationState?>

    suspend fun setCorporateMode(corporateMode: Boolean)

    suspend fun register(input: RegistrationFormInput): RegistrationResult

    suspend fun prepareMobileDeviceRegistration(input: RegistrationFormInput): MobileDeviceRegistrationPreparation

    suspend fun awaitMobileDeviceRegistration(preparation: MobileDeviceRegistrationPreparation): RegistrationResult

    suspend fun syncFcmToken(state: RegistrationState): RegistrationState
}