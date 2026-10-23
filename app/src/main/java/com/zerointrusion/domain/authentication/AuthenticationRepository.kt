package com.zerointrusion.domain.authentication

data class ActiveDesktopSession(
    val accountId: String,
    val attestationId: String,
    val deviceId: String,
    val deviceName: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val issuedAt: String,
    val expiresAt: String,
    // True when the backend omitted deviceId and more than one desktop device shares this namespace,
    // so deviceId/deviceName below are a best-effort guess and must not be used to group sessions together.
    val deviceIdAmbiguous: Boolean = false,
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

enum class MobileAuthenticationStatus {
    PENDING,
    AUTHORIZED,
    REJECTED,
    EXPIRED,
    NOT_FOUND;

    val isTerminal: Boolean
        get() = this != PENDING

    companion object {
        fun fromWire(value: String): MobileAuthenticationStatus = when (value.trim().lowercase()) {
            "pending" -> PENDING
            "authorized" -> AUTHORIZED
            "rejected" -> REJECTED
            "expired" -> EXPIRED
            "not_found" -> NOT_FOUND
            else -> throw IllegalArgumentException("Unsupported mobile authentication status: $value")
        }
    }
}

class AuthenticationSessionAlreadyCompleted : Exception()

interface AuthenticationRepository {
    suspend fun respondToAuthentication(
        notificationId: String,
        accountId: String,
        deviceId: String,
        requestingDeviceId: String,
        approved: Boolean,
        namespaceId: String? = null,
        namespaceUrl: String? = null,
    ): AuthenticationResponseResult

    suspend fun fetchActiveDesktopSessions(
        namespaceId: String? = null,
        namespaceUrl: String? = null,
    ): List<ActiveDesktopSession>

    suspend fun revokeDesktopSession(
        session: ActiveDesktopSession,
    ): DesktopSessionRevocationResult

    suspend fun fetchMobileAuthenticationStatus(
        namespaceUrl: String,
        accountId: String,
        mobileDeviceId: String,
        notificationId: String,
    ): MobileAuthenticationStatus
}