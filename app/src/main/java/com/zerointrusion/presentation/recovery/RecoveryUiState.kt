package com.zerointrusion.presentation.recovery

import com.zerointrusion.domain.recovery.RecoveryQrPayload
import com.zerointrusion.domain.recovery.RecoveryStage

data class RecoveryUiState(
    val scannedQr: RecoveryQrPayload? = null,
    val stage: RecoveryStage? = null,
    val pairingCode: String? = null,
    val expiresAt: String? = null,
    val isWorking: Boolean = false,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null,
)