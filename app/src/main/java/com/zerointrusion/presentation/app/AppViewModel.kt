package com.zerointrusion.presentation.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.gson.Gson
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.domain.notification.NotificationDecision
import com.zerointrusion.domain.notification.NotificationRepository
import com.zerointrusion.domain.qr.QrNamespaceRegistrationResult
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.authentication.AuthenticationRepository
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.namespaces.NamespacesRepository
import com.zerointrusion.domain.qr.QrNamespaceRegistrationRepository
import com.zerointrusion.domain.registration.RegistrationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

@HiltViewModel
class AppViewModel @Inject constructor(
    private val gson: Gson,
    private val registrationRepository: RegistrationRepository,
    private val namespacesRepository: NamespacesRepository,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val qrNamespaceRegistrationRepository: QrNamespaceRegistrationRepository,
    private val authenticationRepository: AuthenticationRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
        const val NAMESPACE_QR_TYPE = "namespace"
        const val NAMESPACE_SYNC_QR_TYPE = "namespace_sync"
        private val NAMESPACE_ID_KEYS = listOf("namespaceId", "namespace_id", "namespaceID")
        private val NAMESPACE_URL_KEYS = listOf("namespaceUrl", "namespace_url", "namespaceURL", "url")
    }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var lastFcmSyncKey: String? = null
    private var fcmSyncInFlight: Boolean = false
    private var storedConnectedDeviceGroups: List<ConnectedDeviceGroup> = emptyList()
    private var storedNamespaces: List<NamespaceEntry> = emptyList()
    private var pendingNamespaceQrPayload: String? = null
    private var liveDesktopSessionsRefreshInFlight: Boolean = false
    private var postApprovalSessionRefreshJob: Job? = null

    private data class ScannedQrPayloadPreview(
        val type: String? = null,
        val namespaceName: String? = null,
        val namespaceId: String? = null,
        val namespaceUrl: String? = null,
        val desktopDeviceId: String? = null,
        val deviceName: String? = null,
    )

    init {
        viewModelScope.launch {
            registrationRepository.observeRegistrationState().collect { state ->
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        isRegistered = state != null,
                        registrationState = state,
                        fcmSyncState = when {
                            state == null -> FcmSyncUiState.Idle
                            state.fcmToken.isNullOrBlank() -> FcmSyncUiState.Idle
                            else -> FcmSyncUiState.Success(state.fcmToken.toPreview())
                        },
                        namespaces = storedNamespaces,
                        notificationOverlay = resolveNotificationOverlay(
                            notification = it.notificationOverlay.notification,
                            registrationState = state,
                            knownNamespaces = storedNamespaces,
                        ),
                    )
                }

                if (state != null) {
                    ensureFcmTokenSync(state)
                }
            }
        }

        viewModelScope.launch {
            connectedDevicesRepository.observeConnectedDevices().collect { groups ->
                storedConnectedDeviceGroups = groups
                _uiState.update { state ->
                    state.copy(
                        connectedDevicesState = ConnectedDevicesUiState.Success(groups),
                    )
                }
            }
        }

        viewModelScope.launch {
            namespacesRepository.observeNamespaces().collect { namespaces ->
                storedNamespaces = namespaces
                _uiState.update { state ->
                    state.copy(
                        namespaces = namespaces,
                        notificationOverlay = resolveNotificationOverlay(
                            notification = state.notificationOverlay.notification,
                            registrationState = state.registrationState,
                            knownNamespaces = namespaces,
                        ),
                    )
                }
            }
        }

        viewModelScope.launch {
            notificationRepository.observeNotification().collect { notification ->
                _uiState.update { state ->
                    val previousNotificationTimestamp = state.notificationOverlay.notification?.receivedAtEpochMillis
                    val shouldForceHomeNavigation = notification != null && notification.receivedAtEpochMillis != previousNotificationTimestamp
                    state.copy(
                        homeNavigationRequestKey = if (shouldForceHomeNavigation) {
                            state.homeNavigationRequestKey + 1
                        } else {
                            state.homeNavigationRequestKey
                        },
                        notificationOverlay = resolveNotificationOverlay(
                            notification = notification,
                            registrationState = state.registrationState,
                            knownNamespaces = storedNamespaces,
                        ),
                    )
                }
            }
        }
    }

    fun onAuthenticationSucceeded() {
        _uiState.update {
            it.copy(
                isAuthenticated = true,
                authenticationError = null,
            )
        }
    }

    fun onAuthenticationFailed(message: String) {
        _uiState.update { it.copy(authenticationError = message) }
    }

    fun retryFcmSync() {
        _uiState.value.registrationState?.let {
            lastFcmSyncKey = null
            ensureFcmTokenSync(it)
        }
    }

    fun clearQrNamespaceRegistrationState() {
        pendingNamespaceQrPayload = null
        _uiState.update { it.copy(qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.Idle) }
    }

    fun setSelectedNamespace(namespace: NamespaceEntry) {
        viewModelScope.launch {
            runCatching { namespacesRepository.setSelectedNamespace(namespace) }
                .onSuccess { state ->
                    _uiState.update {
                        it.copy(
                            registrationState = state,
                            namespaceSelectionErrorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "Namespace selection failed."
                    _uiState.update {
                        it.copy(namespaceSelectionErrorMessage = message)
                    }
                }
        }
    }

    fun deleteNamespace(namespace: NamespaceEntry) {
        viewModelScope.launch {
            runCatching { namespacesRepository.deleteNamespace(namespace) }
                .onSuccess {
                    _uiState.update {
                        it.copy(namespaceManagementErrorMessage = null)
                    }
                }
                .onFailure { throwable ->
                    val message = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "Namespace delete failed."
                    _uiState.update {
                        it.copy(namespaceManagementErrorMessage = message)
                    }
                }
        }
    }

    fun loadConnectedDevices() {
        if (_uiState.value.connectedDevicesState is ConnectedDevicesUiState.InProgress) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(connectedDevicesState = ConnectedDevicesUiState.InProgress) }
            runCatching { connectedDevicesRepository.fetchConnectedDevices() }
                .onSuccess { }
                .onFailure { throwable ->
                    val message = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "Connected devices fetch failed."
                    _uiState.update {
                        it.copy(connectedDevicesState = ConnectedDevicesUiState.Error(message))
                    }
                }
        }
    }

    fun refreshAccountDetailsSnapshot() {
        viewModelScope.launch {
            runCatching { connectedDevicesRepository.fetchConnectedDevices() }
                .onFailure {
                    // Leave visible state untouched; background refresh is best-effort.
                }
        }
    }

    fun loadDesktopSessions(message: String? = null) {
        if (_uiState.value.desktopSessionsState is DesktopSessionsUiState.InProgress) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(desktopSessionsState = DesktopSessionsUiState.InProgress) }
            runCatching { authenticationRepository.fetchActiveDesktopSessions() }
                .onSuccess { sessions ->
                    _uiState.update {
                        it.copy(
                            desktopSessionsState = DesktopSessionsUiState.Success(
                                sessions = sessions,
                                message = message,
                            ),
                            liveDesktopSessions = sessions,
                        )
                    }
                }
                .onFailure { throwable ->
                    val failureMessage = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "Desktop sessions fetch failed."
                    _uiState.update {
                        it.copy(desktopSessionsState = DesktopSessionsUiState.Error(failureMessage))
                    }
                }
        }
    }

    fun refreshLiveDesktopSessions() {
        refreshLiveDesktopSessions(namespaceId = null, namespaceUrl = null)
    }

    private fun refreshLiveDesktopSessions(
        namespaceId: String?,
        namespaceUrl: String?,
    ) {
        if (liveDesktopSessionsRefreshInFlight) {
            return
        }

        liveDesktopSessionsRefreshInFlight = true
        viewModelScope.launch {
            runCatching {
                authenticationRepository.fetchActiveDesktopSessions(
                    namespaceId = namespaceId,
                    namespaceUrl = namespaceUrl,
                )
            }
                .onSuccess { sessions ->
                    _uiState.update { state ->
                        state.copy(
                            liveDesktopSessions = sessions,
                            desktopSessionsState = when (val desktopState = state.desktopSessionsState) {
                                is DesktopSessionsUiState.Success -> desktopState.copy(sessions = sessions)
                                else -> state.desktopSessionsState
                            },
                        )
                    }
                }
                .onFailure {
                    // Keep the last visible main-screen live-session state if a background refresh fails.
                }
            liveDesktopSessionsRefreshInFlight = false
        }
    }

    fun revokeDesktopSession(session: ActiveDesktopSession) {
        val currentState = _uiState.value.desktopSessionsState as? DesktopSessionsUiState.Success
        if (_uiState.value.liveDesktopSessionRevokingAttestationId != null || currentState?.revokingAttestationId != null) {
            return
        }

        _uiState.update {
            it.copy(
                liveDesktopSessionRevokingAttestationId = session.attestationId,
                liveDesktopSessionRevokeErrorMessage = null,
                desktopSessionsState = currentState?.copy(
                    revokingAttestationId = session.attestationId,
                    message = null,
                ) ?: it.desktopSessionsState,
            )
        }

        viewModelScope.launch {
            runCatching {
                authenticationRepository.revokeDesktopSession(session)
            }.onSuccess { result ->
                if (result.success) {
                    _uiState.update { state ->
                        val successState = state.desktopSessionsState as? DesktopSessionsUiState.Success
                        state.copy(
                            liveDesktopSessions = state.liveDesktopSessions.filterNot { it.attestationId == result.attestationId },
                            liveDesktopSessionRevokingAttestationId = null,
                            desktopSessionsState = successState?.copy(
                                sessions = successState.sessions.filterNot { it.attestationId == result.attestationId },
                                revokingAttestationId = null,
                                message = null,
                            ) ?: state.desktopSessionsState,
                        )
                    }
                } else {
                    loadDesktopSessions(message = "The selected desktop session was already changed on the backend. The list has been refreshed.")
                }
            }.onFailure { throwable ->
                val failureMessage = (throwable as? RegistrationFailure)?.message
                    ?: throwable.message
                    ?: "Desktop session revoke failed."
                _uiState.update { state ->
                    val successState = state.desktopSessionsState as? DesktopSessionsUiState.Success
                    state.copy(
                        liveDesktopSessionRevokingAttestationId = null,
                        liveDesktopSessionRevokeErrorMessage = failureMessage,
                        desktopSessionsState = successState?.copy(
                            revokingAttestationId = null,
                            message = failureMessage,
                        ) ?: state.desktopSessionsState,
                    )
                }
            }
        }
    }

    fun registerNamespaceFromQr(qrPayload: String) {
        if (_uiState.value.qrNamespaceRegistrationState is QrNamespaceRegistrationUiState.InProgress) {
            Log.d(QR_LOG_TAG, "Ignored QR namespace connection request because another registration is already in progress.")
            return
        }

        val namespacePayload = parseNamespaceQrPreview(qrPayload)
        if (namespacePayload != null) {
            Log.d(
                QR_LOG_TAG,
                "Namespace sync QR payload recognized. type=${namespacePayload.type?.trim().orEmpty()} namespaceId=${namespacePayload.namespaceId?.trim().orEmpty()} namespaceUrl=${namespacePayload.namespaceUrl?.trim().orEmpty()} desktopDeviceId=${namespacePayload.desktopDeviceId?.trim().orEmpty()} deviceName=${namespacePayload.deviceName?.trim().orEmpty()} payloadLength=${qrPayload.length}",
            )
            submitQrNamespaceRegistration(qrPayload = qrPayload)
            return
        }

        submitQrNamespaceRegistration(qrPayload = qrPayload)
    }

    fun confirmQrNamespaceDeviceSelection(deviceId: String) {
        val pendingPayload = pendingNamespaceQrPayload
        if (pendingPayload.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.Error(
                        "The scanned QR payload is no longer available. Scan the QR code again.",
                    ),
                )
            }
            return
        }

        val selectedOption = (_uiState.value.qrNamespaceRegistrationState as? QrNamespaceRegistrationUiState.AwaitingDeviceSelection)
            ?.deviceOptions
            ?.firstOrNull { option -> option.deviceId == deviceId }
        if (selectedOption == null) {
            _uiState.update {
                it.copy(
                    qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.Error(
                        "The selected connected device is no longer available. Scan the QR code again.",
                    ),
                )
            }
            return
        }

        submitQrNamespaceRegistration(
            qrPayload = pendingPayload,
            rootDeviceAccountId = selectedOption.accountId,
        )
    }

    private fun submitQrNamespaceRegistration(
        qrPayload: String,
        rootDeviceAccountId: String? = null,
    ) {
        pendingNamespaceQrPayload = null
        viewModelScope.launch {
            Log.d(QR_LOG_TAG, "QR payload received in ViewModel. length=${qrPayload.length}")
            _uiState.update { it.copy(qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.InProgress) }
            runCatching {
                qrNamespaceRegistrationRepository.registerNamespaceFromQr(
                    qrPayload = qrPayload,
                    rootDeviceAccountId = rootDeviceAccountId,
                )
            }
                .onSuccess { result: QrNamespaceRegistrationResult ->
                    Log.d(QR_LOG_TAG, "QR namespace connection succeeded for namespace=${result.namespaceName} desktopDeviceId=${result.desktopDeviceId}")
                    _uiState.update {
                        it.copy(
                            qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.Success(
                                namespaceName = result.namespaceName,
                                desktopDeviceId = result.desktopDeviceId,
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "Namespace connection failed."
                    Log.e(QR_LOG_TAG, "QR namespace connection failed: $message", throwable)
                    _uiState.update {
                        it.copy(qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.Error(message))
                    }
                }
        }
    }

    private fun parseNamespaceQrPreview(qrPayload: String): ScannedQrPayloadPreview? {
        val preview = runCatching {
            gson.fromJson(qrPayload.trim(), ScannedQrPayloadPreview::class.java)
        }.getOrNull() ?: return null

        return preview.takeIf { previewPayload ->
            val normalizedType = previewPayload.type?.trim()
            normalizedType == NAMESPACE_SYNC_QR_TYPE
        }
    }

    private fun resolveNamespaceQrPreviewName(preview: ScannedQrPayloadPreview): String {
        val explicitName = preview.namespaceName?.trim().orEmpty()
        if (explicitName.isNotEmpty()) {
            return explicitName
        }

        val namespaceUrl = preview.namespaceUrl?.trim().orEmpty().trimEnd('/')
        val storedNamespaceName = storedNamespaces.firstOrNull { namespaceEntry ->
                namespaceEntry.namespaceUrl.trim().trimEnd('/').equals(namespaceUrl, ignoreCase = true)
        }?.namespaceName?.trim().orEmpty()

            return storedNamespaceName.ifEmpty { preview.namespaceId?.trim().orEmpty() }
    }

    private fun buildQrNamespaceDeviceOptions(): List<QrNamespaceDeviceOption> = buildQrNamespaceDeviceOptions(
        connectedDeviceGroups = storedConnectedDeviceGroups,
        storedNamespaces = storedNamespaces,
        mobileDeviceId = _uiState.value.registrationState?.deviceId.orEmpty(),
    )

    fun onNotificationDecision(decision: NotificationDecision) {
        val notification = _uiState.value.notificationOverlay.notification ?: return
        val isMatch = _uiState.value.notificationOverlay.canRespond
        if (!isMatch) {
            notificationRepository.clearNotification()
            _uiState.update { it.copy(notificationOverlay = NotificationOverlayUiState()) }
            return
        }

        viewModelScope.launch {
            val approved = decision == NotificationDecision.Accept
            val notificationId = notification.data["notificationId"].orEmpty()
            val targetNamespaceId = notification.data.firstNonBlankValue(NAMESPACE_ID_KEYS)
            val targetNamespaceUrl = notification.data.firstNonBlankValue(NAMESPACE_URL_KEYS)
            runCatching {
                authenticationRepository.respondToAuthentication(
                    notificationId = notificationId,
                    accountId = notification.accountId,
                    deviceId = notification.mobileDeviceId,
                    requestingDeviceId = notification.desktopDeviceId,
                    approved = approved,
                    namespaceId = targetNamespaceId,
                    namespaceUrl = targetNamespaceUrl,
                )
            }.onSuccess { result ->
                notificationRepository.clearNotification()
                _uiState.update {
                    it.copy(
                        notificationOverlay = NotificationOverlayUiState(
                            namespaceResults = result.namespaceResults,
                        ),
                    )
                }
                if (approved) {
                    postApprovalSessionRefreshJob?.cancel()
                    postApprovalSessionRefreshJob = viewModelScope.launch {
                        delay(1_000)
                        refreshLiveDesktopSessions()
                    }
                }
            }.onFailure { throwable ->
                Log.e(QR_LOG_TAG, "Authentication response failed: ${throwable.message}", throwable)
                notificationRepository.clearNotification()
                _uiState.update {
                    it.copy(
                        notificationOverlay = NotificationOverlayUiState(
                            notification = notification,
                            mismatchError = throwable.message ?: "Failed to send authentication response.",
                            canRespond = false,
                            namespaceResults = emptyList(),
                        ),
                    )
                }
            }
        }
    }

    fun dismissNotificationMismatch() {
        notificationRepository.clearNotification()
        _uiState.update {
            it.copy(notificationOverlay = NotificationOverlayUiState())
        }
    }

    private fun ensureFcmTokenSync(state: RegistrationState) {
        val syncKey = state.accountId + ":" + (state.fcmToken ?: "missing")
        if (fcmSyncInFlight || lastFcmSyncKey == syncKey) {
            return
        }

        fcmSyncInFlight = true
        lastFcmSyncKey = syncKey
        _uiState.update { it.copy(fcmSyncState = FcmSyncUiState.InProgress) }
        viewModelScope.launch {
            runCatching { registrationRepository.syncFcmToken(state) }
                .onSuccess { syncedState ->
                    lastFcmSyncKey = syncedState.accountId + ":" + (syncedState.fcmToken ?: "missing")
                    _uiState.update {
                        it.copy(
                            registrationState = syncedState,
                            fcmSyncState = FcmSyncUiState.Success(
                                syncedState.fcmToken?.toPreview() ?: "token unavailable",
                            ),
                        )
                    }
                }
                .onFailure { throwable ->
                    lastFcmSyncKey = null
                    _uiState.update {
                        it.copy(
                            fcmSyncState = FcmSyncUiState.Error(
                                buildFcmErrorMessage(throwable),
                            ),
                        )
                    }
                }
            fcmSyncInFlight = false
        }
    }

    private fun buildFcmErrorMessage(throwable: Throwable): String {
        val primaryMessage = (throwable as? RegistrationFailure)?.message ?: throwable.message
        if (!primaryMessage.isNullOrBlank()) {
            return primaryMessage
        }

        val causeMessage = throwable.cause?.message
        if (!causeMessage.isNullOrBlank()) {
            return "FCM sync failed: $causeMessage"
        }

        val typeName = throwable::class.java.simpleName.takeIf { it.isNotBlank() } ?: "UnknownError"
        return "FCM sync failed: $typeName"
    }

    private fun resolveNotificationOverlay(
        notification: BackendPushNotification?,
        registrationState: RegistrationState?,
        knownNamespaces: List<NamespaceEntry>,
    ): NotificationOverlayUiState {
        if (notification == null) {
            return NotificationOverlayUiState()
        }

        if (registrationState == null) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError = "Push notification received, but no registered account is stored on this device.",
                canRespond = false,
            )
        }

        if (notification.accountId.isBlank() || notification.mobileDeviceId.isBlank()) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError = "Push notification blocked because the delivered Firebase data payload did not include a non-empty accountId and mobileDeviceId. Put both values inside message.data so the app can verify the target device.",
                canRespond = false,
            )
        }

        if (!registrationState.deviceId.equals(notification.mobileDeviceId, ignoreCase = true)) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError = "Push notification targeted mobileDeviceId=${notification.mobileDeviceId}, but this phone is registered as deviceId=${registrationState.deviceId}.",
                canRespond = false,
            )
        }

        val targetNamespaceId = notification.data.firstNonBlankValue(NAMESPACE_ID_KEYS)
        if (targetNamespaceId.isBlank()) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError = "Push notification blocked because the delivered Firebase data payload did not include namespaceId.",
                canRespond = false,
            )
        }

        val targetNamespaceUrl = notification.data.firstNonBlankValue(NAMESPACE_URL_KEYS)
        val matchingNamespaces = knownNamespaces.filter {
            val accountMatches = it.accountId.equals(notification.accountId, ignoreCase = true)
            if (!accountMatches) {
                false
            } else {
                val namespaceIdMatches = it.namespaceId.equals(targetNamespaceId, ignoreCase = true)
                val namespaceUrlMatches = targetNamespaceUrl.isBlank() ||
                    it.namespaceUrl.trim().trimEnd('/').equals(targetNamespaceUrl.trim().trimEnd('/'), ignoreCase = true)
                namespaceIdMatches && namespaceUrlMatches
            }
        }

        val matchingNamespace = matchingNamespaces.firstOrNull()

        if (matchingNamespace == null) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError = "Push notification namespaceId/accountId pair does not match any locally stored namespace for mobileDeviceId=${notification.mobileDeviceId}. Received namespaceId=$targetNamespaceId, namespaceUrl=${targetNamespaceUrl.ifBlank { "(empty)" }}, accountId=${notification.accountId}, desktopDeviceId=${notification.desktopDeviceId}.",
                canRespond = false,
            )
        }

        return NotificationOverlayUiState(
            notification = notification,
            mismatchError = null,
            canRespond = true,
        )
    }

    private fun String.toPreview(): String = when {
        length <= 18 -> this
        else -> take(10) + "..." + takeLast(6)
    }
}

private fun Map<String, String>.firstNonBlankValue(keys: List<String>): String {
    return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }.orEmpty()
}

internal fun buildQrNamespaceDeviceOptions(
    connectedDeviceGroups: List<ConnectedDeviceGroup>,
    storedNamespaces: List<NamespaceEntry>,
    mobileDeviceId: String,
): List<QrNamespaceDeviceOption> = connectedDeviceGroups
    .filter { group ->
        group.deviceId.isNotBlank() &&
            !group.deviceId.trim().equals(mobileDeviceId.trim(), ignoreCase = true)
    }
    .map { group ->
        val representative = storedNamespaces.firstOrNull { namespace ->
            namespace.desktopDeviceId.trim().equals(group.deviceId.trim(), ignoreCase = true)
        }
        QrNamespaceDeviceOption(
            accountId = group.accountId,
            deviceId = group.deviceId,
            deviceName = representative?.deviceName?.trim().takeUnless { it.isNullOrEmpty() }
                ?: group.deviceName?.trim().takeUnless { it.isNullOrEmpty() }
                ?: group.deviceId,
        )
    }
    .sortedBy { option -> option.deviceName.lowercase() }