package com.zerointrusion.domain.registration

import kotlinx.coroutines.flow.Flow

interface RegistrationRepository {
    fun observeRegistrationState(): Flow<RegistrationState?>

    suspend fun register(input: RegistrationFormInput): RegistrationResult

    suspend fun syncFcmToken(state: RegistrationState): RegistrationState
}