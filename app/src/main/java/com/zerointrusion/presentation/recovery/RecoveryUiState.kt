package com.zerointrusion.presentation.recovery

import com.zerointrusion.domain.recovery.RecoveryQrPayload
import com.zerointrusion.domain.recovery.RecoveryStage

enum class RecoveryStep {
    SCAN,
    CONFIRM_SERVER,
    WAITING,
    COMPLETED,
}

enum class RecoveryErrorCode {
    RECOVERY_QR_INVALID,
    RECOVERY_CAMERA_FAILED,
    RECOVERY_START_FAILED,
    RECOVERY_CANCEL_FAILED,
    RECOVERY_STATUS_FAILED,
    RECOVERY_PUSH_FAILED,
}

data class RecoveryUiState(
    val scannedQr: RecoveryQrPayload? = null,
    val stage: RecoveryStage? = null,
    val pairingCode: String? = null,
    val expiresAt: String? = null,
    val isWorking: Boolean = false,
    val isCompleted: Boolean = false,
    val errorCode: RecoveryErrorCode? = null,
) {
    val step: RecoveryStep
        get() = when {
            isCompleted -> RecoveryStep.COMPLETED
            stage != null -> RecoveryStep.WAITING
            scannedQr != null -> RecoveryStep.CONFIRM_SERVER
            else -> RecoveryStep.SCAN
        }
}