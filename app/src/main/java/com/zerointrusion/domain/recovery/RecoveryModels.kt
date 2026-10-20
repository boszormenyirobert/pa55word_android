package com.zerointrusion.domain.recovery

data class RecoveryQrPayload(
    val recoveryId: String,
    val apiBaseUrl: String,
)

data class RecoveryNamespace(
    val namespaceId: String,
    val name: String,
    val url: String,
    val root: Boolean,
)

enum class RecoveryStage {
    CANDIDATE_SUBMITTING,
    WAITING_FOR_SELECTION,
    DEK_RECEIVED,
}

data class RecoveryPendingState(
    val recoveryId: String,
    val apiBaseUrl: String,
    val candidateId: String,
    val mobileDeviceId: String,
    val deviceName: String,
    val publicKey: String,
    val pairingCode: String,
    val pairingCodeHash: String,
    val candidateRequestId: String,
    val stage: RecoveryStage,
    val expiresAt: String? = null,
    val completionRequestId: String? = null,
    val accountId: String? = null,
    val encDek: String? = null,
    val email: String? = null,
    val phone: String? = null,
    val namespaces: List<RecoveryNamespace> = emptyList(),
)

data class RecoveryProgress(
    val stage: RecoveryStage,
    val pairingCode: String?,
    val expiresAt: String?,
    val isCompleted: Boolean = false,
    val fcmErrorMessage: String? = null,
)