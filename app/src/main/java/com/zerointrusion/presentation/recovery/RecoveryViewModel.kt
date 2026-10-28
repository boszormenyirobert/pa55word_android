package com.zerointrusion.presentation.recovery

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerointrusion.data.qr.RecoveryQrPayloadParser
import com.zerointrusion.domain.recovery.RecoveryProgress
import com.zerointrusion.domain.recovery.RecoveryRepository
import com.zerointrusion.domain.recovery.RecoveryStage
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val repository: RecoveryRepository,
    private val qrParser: RecoveryQrPayloadParser,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryUiState(isWorking = true))
    val uiState = _uiState.asStateFlow()
    private var pollingJob: Job? = null
    private var initializationJob: Job? = null
    private var actionJob: Job? = null

    fun refreshOnScreenOpened() {
        initializationJob?.cancel()
        _uiState.update { it.copy(isWorking = true) }
        initializationJob = viewModelScope.launch {
            try {
                val pending = repository.current()
                if (pending == null) {
                    _uiState.value = RecoveryUiState()
                    return@launch
                }
                _uiState.update {
                    it.copy(
                        stage = pending.stage,
                        pairingCode = pending.pairingCode.takeIf { pending.stage == RecoveryStage.WAITING_FOR_SELECTION },
                        expiresAt = pending.expiresAt,
                        isWorking = false,
                        isCompleted = false,
                        errorCode = null,
                    )
                }
                poll()
            } catch (exception: Exception) {
                showFailure(RecoveryErrorCode.RECOVERY_STATUS_FAILED, exception)
            }
        }
    }

    fun onScreenExited() {
        initializationJob?.cancel()
        actionJob?.cancel()
        pollingJob?.cancel()
        _uiState.value = RecoveryUiState(isWorking = true)
    }

    fun onQrScanned(rawPayload: String) {
        if (_uiState.value.isWorking) return
        val qr = try {
            qrParser.parse(rawPayload)
        } catch (exception: Exception) {
            showFailure(RecoveryErrorCode.RECOVERY_QR_INVALID, exception)
            return
        }
        pollingJob?.cancel()
        _uiState.value = RecoveryUiState(scannedQr = qr)
    }

    fun onScannerError(message: String) {
        Log.w(RECOVERY_LOG_TAG, "RECOVERY_CAMERA_FAILED: Camera reported a scanner error (${message.length} characters).")
        _uiState.update { it.copy(errorCode = RecoveryErrorCode.RECOVERY_CAMERA_FAILED) }
    }

    fun confirmServer() {
        if (_uiState.value.step != RecoveryStep.CONFIRM_SERVER) return
        val qr = _uiState.value.scannedQr ?: return
        launchAction(RecoveryErrorCode.RECOVERY_START_FAILED) {
            val progress = repository.start(qr)
            applyProgress(progress)
            if (!progress.isCompleted) poll()
        }
    }

    fun cancel() {
        if (_uiState.value.isWorking) return
        pollingJob?.cancel()
        launchAction(RecoveryErrorCode.RECOVERY_CANCEL_FAILED) {
            repository.cancel()
            _uiState.value = RecoveryUiState()
        }
    }

    fun retry() {
        if (_uiState.value.isWorking || _uiState.value.step != RecoveryStep.WAITING) return
        _uiState.update { it.copy(isWorking = true, errorCode = null) }
        poll()
    }

    fun retryFcm() {
        if (!_uiState.value.isCompleted) return
        launchAction(RecoveryErrorCode.RECOVERY_PUSH_FAILED) {
            val errorMessage = repository.retryFcm()
            _uiState.update {
                it.copy(
                    isWorking = false,
                    errorCode = errorMessage?.let { RecoveryErrorCode.RECOVERY_PUSH_FAILED },
                )
            }
        }
    }

    private fun poll() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                val progress = try {
                    repository.refresh()
                } catch (exception: CancellationException) {
                    throw exception
                } catch (failure: Exception) {
                    showFailure(RecoveryErrorCode.RECOVERY_STATUS_FAILED, failure)
                    return@launch
                }
                applyProgress(progress)
                if (progress.isCompleted) return@launch
                delay(POLL_INTERVAL_MILLIS)
            }
        }
    }

    private fun applyProgress(progress: RecoveryProgress) {
        _uiState.update {
            it.copy(
                stage = progress.stage,
                pairingCode = progress.pairingCode,
                expiresAt = progress.expiresAt,
                isWorking = false,
                isCompleted = progress.isCompleted,
                errorCode = progress.fcmErrorMessage?.let { RecoveryErrorCode.RECOVERY_PUSH_FAILED },
            )
        }
    }

    private fun launchAction(errorCode: RecoveryErrorCode, action: suspend () -> Unit) {
        if (_uiState.value.isWorking) return
        _uiState.update { it.copy(isWorking = true, errorCode = null) }
        actionJob = viewModelScope.launch {
            try {
                action()
            } catch (exception: Exception) {
                showFailure(errorCode, exception)
            }
        }
    }

    private fun showFailure(errorCode: RecoveryErrorCode, failure: Exception) {
        if (failure is CancellationException) throw failure
        Log.e(RECOVERY_LOG_TAG, "${errorCode.name}: ${failure::class.java.simpleName}")
        _uiState.update { it.copy(isWorking = false, errorCode = errorCode) }
    }

    private companion object {
        const val RECOVERY_LOG_TAG = "ZeroIntrusionRecovery"
        const val POLL_INTERVAL_MILLIS = 3_000L
    }
}