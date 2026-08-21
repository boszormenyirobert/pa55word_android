package com.zerointrusion.domain.authentication

data class ActiveDesktopSession(
    val attestationId: String,
    val deviceId: String,
    val issuedAt: String,
    val expiresAt: String,
)

data class DesktopSessionRevocationResult(
    val success: Boolean,
    val attestationId: String,
)

interface AuthenticationRepository {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    )

    suspend fun fetchActiveDesktopSessions(): List<ActiveDesktopSession>

    suspend fun revokeDesktopSession(
        attestationId: String,
        desktopDeviceId: String,
    ): DesktopSessionRevocationResult
}