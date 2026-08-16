package com.zerointrusion.domain.authentication

interface AuthenticationRepository {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    )
}