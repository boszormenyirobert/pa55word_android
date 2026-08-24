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

data class NamespaceAuthenticationResult(
    val namespaceId: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val success: Boolean,
    val message: String? = null,
)

data class AuthenticationResponseResult(
    val namespaceResults: List<NamespaceAuthenticationResult>,
) {
    val hasAnySuccess: Boolean
        get() = namespaceResults.any { it.success }
}

interface AuthenticationRepository {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        approved: Boolean,
    ): AuthenticationResponseResult

    suspend fun fetchActiveDesktopSessions(): List<ActiveDesktopSession>

    suspend fun revokeDesktopSession(
        attestationId: String,
        desktopDeviceId: String,
    ): DesktopSessionRevocationResult
}