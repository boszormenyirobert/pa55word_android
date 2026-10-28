package com.zerointrusion.presentation.recovery

import com.zerointrusion.domain.recovery.RecoveryQrPayload
import com.zerointrusion.domain.recovery.RecoveryStage
import org.junit.Assert.assertEquals
import org.junit.Test

class RecoveryUiStateTest {
    @Test
    fun initialStateRequiresScanning() {
        assertEquals(RecoveryStep.SCAN, RecoveryUiState().step)
    }

    @Test
    fun scannedCodeRequiresExplicitServerConfirmation() {
        val state = RecoveryUiState(scannedQr = RecoveryQrPayload("recovery-id", "https://example.test"))

        assertEquals(RecoveryStep.CONFIRM_SERVER, state.step)
    }

    @Test
    fun restoredPendingRecoveryDoesNotRequireAnotherScan() {
        RecoveryStage.entries.forEach { stage ->
            assertEquals(RecoveryStep.WAITING, RecoveryUiState(stage = stage).step)
        }
    }

    @Test
    fun completionTakesPrecedenceOverStoredStage() {
        val state = RecoveryUiState(stage = RecoveryStage.DEK_RECEIVED, isCompleted = true)

        assertEquals(RecoveryStep.COMPLETED, state.step)
    }
}