package com.zerointrusion.presentation.recovery

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.JsonParser
import com.zerointrusion.BuildConfig
import com.zerointrusion.domain.recovery.RecoveryProgress
import com.zerointrusion.domain.recovery.RecoveryQrPayload
import com.zerointrusion.domain.recovery.RecoveryRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import java.util.UUID
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class RecoveryViewModel @Inject constructor(
    private val repository: RecoveryRepository,
) : ViewModel() {
    private val _uiState = MutableStateFlow(RecoveryUiState())
    val uiState = _uiState.asStateFlow()
    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            repository.current()?.let { pending ->
                _uiState.update {
                    it.copy(
                        stage = pending.stage,
                        pairingCode = pending.pairingCode.takeIf { pending.stage.name == "WAITING_FOR_SELECTION" },
                        expiresAt = pending.expiresAt,
                    )
                }
                poll()
            }
        }
    }

    fun onQrScanned(rawPayload: String) {
        val result = runCatching { parseRecoveryQr(rawPayload) }
        _uiState.update {
            it.copy(scannedQr = result.getOrNull(), errorMessage = result.exceptionOrNull()?.message)
        }
    }

    fun onScannerError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    fun confirmServer() {
        val qr = _uiState.value.scannedQr ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            runCatching { repository.start(qr) }
                .onSuccess { progress ->
                    applyProgress(progress)
                    if (!progress.isCompleted) poll()
                }
                .onFailure { failure -> _uiState.update { it.copy(isWorking = false, errorMessage = failure.message ?: "Recovery could not start.") } }
        }
    }

    fun cancel() {
        pollingJob?.cancel()
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true, errorMessage = null) }
            runCatching { repository.cancel() }
                .onSuccess { _uiState.value = RecoveryUiState() }
                .onFailure { failure -> _uiState.update { it.copy(isWorking = false, errorMessage = failure.message ?: "Recovery could not be cancelled.") } }
        }
    }

    fun retry() = poll()

    fun retryFcm() {
        viewModelScope.launch {
            _uiState.update { it.copy(isWorking = true) }
            val errorMessage = repository.retryFcm()
            _uiState.update { it.copy(isWorking = false, errorMessage = errorMessage) }
        }
    }

    private fun poll() {
        pollingJob?.cancel()
        pollingJob = viewModelScope.launch {
            while (true) {
                val progress = runCatching { repository.refresh() }.getOrElse { failure ->
                    _uiState.update { it.copy(isWorking = false, errorMessage = failure.message ?: "Recovery status could not be refreshed.") }
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
                errorMessage = progress.fcmErrorMessage,
            )
        }
    }

    private fun parseRecoveryQr(payload: String): RecoveryQrPayload {
        val objectValue = JsonParser.parseString(payload).asJsonObject
        require(objectValue.keySet() == setOf("type", "version", "recoveryId", "apiBaseUrl")) {
            "The recovery QR code contains unsupported fields."
        }
        require(objectValue.get("type")?.asString == "recovery") { "This is not a recovery QR code." }
        require(objectValue.get("version")?.asInt == 1) { "This recovery QR code uses an unsupported version." }
        val recoveryId = objectValue.get("recoveryId")?.asString ?: error("Missing recovery ID.")
        UUID.fromString(recoveryId)
        val apiBaseUrl = objectValue.get("apiBaseUrl")?.asString ?: error("Missing recovery server URL.")
        val uri = Uri.parse(apiBaseUrl)
        require(uri.scheme == "https" || (uri.scheme == "http" && BuildConfig.DEBUG)) {
            "Recovery requires HTTPS outside development builds."
        }
        require(
            !uri.host.isNullOrBlank() &&
                uri.userInfo.isNullOrBlank() &&
                uri.query.isNullOrBlank() &&
                uri.fragment.isNullOrBlank() &&
                (uri.path.isNullOrBlank() || uri.path == "/"),
        ) {
            "The recovery server URL is invalid."
        }
        return RecoveryQrPayload(recoveryId, apiBaseUrl.trimEnd('/'))
    }

    private companion object {
        const val POLL_INTERVAL_MILLIS = 3_000L
    }
}