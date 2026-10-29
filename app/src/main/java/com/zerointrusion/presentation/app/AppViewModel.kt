package com.zerointrusion.presentation.app

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerointrusion.data.network.InternetConnectivityMonitor
import com.zerointrusion.data.security.BiometricKeyAccess
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.authentication.AuthenticationRepository
import com.zerointrusion.domain.authentication.AuthenticationSessionAlreadyCompleted
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.namespaces.NamespacesRepository
import com.zerointrusion.domain.notification.BackendPushNotification
import com.zerointrusion.domain.notification.NotificationDecision
import com.zerointrusion.domain.notification.NotificationRepository
import com.zerointrusion.domain.qr.QrNamespaceRegistrationRepository
import com.zerointrusion.domain.qr.QrNamespaceRegistrationResult
import com.zerointrusion.domain.registration.RegistrationFormInput
import com.zerointrusion.domain.registration.RegistrationRepository
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import com.zerointrusion.domain.registration.ServicePlan
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@HiltViewModel
class AppViewModel
@Inject
constructor(
    private val registrationRepository: RegistrationRepository,
    private val namespacesRepository: NamespacesRepository,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
    private val qrNamespaceRegistrationRepository: QrNamespaceRegistrationRepository,
    private val authenticationRepository: AuthenticationRepository,
    private val notificationRepository: NotificationRepository,
    private val registrationValidator: RegistrationValidator,
    internetConnectivityMonitor: InternetConnectivityMonitor,
    private val keyAccess: BiometricKeyAccess,
    private val keyStoreService: KeyStoreService,
) : ViewModel() {
    private companion object {
        const val SETTINGS_LOG_TAG = "ZeroIntrusionSettings"
        const val SETTINGS_SAVE_ERROR_CODE = "SETTINGS_SAVE_FAILED"
        const val AUTHENTICATION_LOG_TAG = "ZeroIntrusionAuthentication"
        const val QR_LOG_TAG = "ZeroIntrusionQr"
        const val MOBILE_AUTHENTICATION_STATUS_POLL_MILLIS = 1_000L
        private val NAMESPACE_ID_KEYS = listOf("namespaceId", "namespace_id", "namespaceID")
        private val NAMESPACE_URL_KEYS =
            listOf("namespaceUrl", "namespace_url", "namespaceURL", "url")
    }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    val biometricRemainingSeconds: StateFlow<Int> = keyAccess.remainingSeconds
    val isInternetConnected: StateFlow<Boolean> =
        internetConnectivityMonitor
            .observe()
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(replayExpirationMillis = 0),
                initialValue = false,
            )
    private var lastFcmSyncKey: String? = null
    private var fcmSyncInFlight: Boolean = false
    private var storedNamespaces: List<NamespaceEntry> = emptyList()
    private var pendingNamespaceQrPayload: String? = null
    private var postApprovalSessionRefreshJob: Job? = null
    private var mobileAuthenticationStatusPollingJob: Job? = null
    private var mobileAuthenticationStatusNotificationId: String? = null
    private var pendingKeyAuthentication: Long? = null

    init {
        viewModelScope.launch {
            keyAccess.unlocked.collect { unlocked ->
                _uiState.update { it.copy(isAuthenticated = unlocked) }
                if (unlocked) {
                    _uiState.value.registrationState?.let(::ensureFcmTokenSync)
                    synchronizeMobileAuthenticationStatusPolling(_uiState.value.notificationOverlay)
                } else {
                    stopMobileAuthenticationStatusPolling()
                    postApprovalSessionRefreshJob?.cancel()
                }
            }
        }
        viewModelScope.launch {
            namespacesRepository.observeAccounts().collect { accounts ->
                _uiState.update { it.copy(accounts = accounts) }
            }
        }
        viewModelScope.launch {
            namespacesRepository.observeMobileRegistrations().collect { registrations ->
                _uiState.update { it.copy(mobileRegistrations = registrations) }
            }
        }
        viewModelScope.launch {
            registrationRepository.observeRegistrationState().collect { state ->
                _uiState.update {
                    it.copy(
                        isInitialized = true,
                        isRegistered = state != null,
                        registrationState = state,
                        fcmSyncState =
                            when {
                                state == null -> FcmSyncUiState.Idle
                                state.fcmToken.isNullOrBlank() -> FcmSyncUiState.Idle
                                else -> FcmSyncUiState.Success(state.fcmToken.toPreview())
                            },
                        namespaces = storedNamespaces,
                        notificationOverlay =
                            resolveNotificationOverlay(
                                notification = it.notificationOverlay.notification,
                                registrationState = state,
                                knownNamespaces = storedNamespaces,
                            ),
                    )
                }
                synchronizeMobileAuthenticationStatusPolling(_uiState.value.notificationOverlay)

                if (state != null) {
                    ensureFcmTokenSync(state)
                }
            }
        }

        viewModelScope.launch {
            connectedDevicesRepository.observeConnectedDevices().collect { groups ->
                _uiState.update { state ->
                    state.copy(connectedDevicesState = ConnectedDevicesUiState.Success(groups))
                }
                synchronizeMobileAuthenticationStatusPolling(_uiState.value.notificationOverlay)
            }
        }

        viewModelScope.launch {
            connectedDevicesRepository.observeDatabaseAvailability().collect { availability ->
                _uiState.update { state -> state.copy(databaseAvailability = availability) }
            }
        }

        viewModelScope.launch {
            namespacesRepository.observeNamespaces().collect { namespaces ->
                storedNamespaces = namespaces
                _uiState.update { state ->
                    state.copy(
                        namespaces = namespaces,
                        notificationOverlay =
                            resolveNotificationOverlay(
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
                val overlay =
                    resolveNotificationOverlay(
                        notification = notification,
                        registrationState = _uiState.value.registrationState,
                        knownNamespaces = storedNamespaces,
                    )
                _uiState.update { state ->
                    val previousNotificationTimestamp =
                        state.notificationOverlay.notification?.receivedAtEpochMillis
                    val shouldForceHomeNavigation =
                        notification != null &&
                            notification.receivedAtEpochMillis != previousNotificationTimestamp
                    state.copy(
                        homeNavigationRequestKey =
                            if (shouldForceHomeNavigation) {
                                state.homeNavigationRequestKey + 1
                            } else {
                                state.homeNavigationRequestKey
                            },
                        notificationOverlay = overlay,
                    )
                }
                synchronizeMobileAuthenticationStatusPolling(overlay)
            }
        }
    }

    suspend fun prepareAuthentication(): Boolean =
        try {
            pendingKeyAuthentication = null
            withContext(Dispatchers.IO) {
                if (_uiState.value.isRegistered && !keyStoreService.hasKeyPair()) {
                    throw RegistrationFailure.DeviceKeyInvalidated()
                }
                keyStoreService.ensureKeyPair()
            }
            pendingKeyAuthentication = keyAccess.beginAuthentication()
            _uiState.update { it.copy(authenticationError = null) }
            true
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            keyAccess.lock()
            onAuthenticationFailed(exception.message ?: "SECURITY_KEY_PREPARATION_FAILED")
            false
        }

    fun onAuthenticationSucceeded() {
        val attempt = pendingKeyAuthentication ?: return
        pendingKeyAuthentication = null
        viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) { keyStoreService.verifyBiometricAccess(attempt) }
                _uiState.update { it.copy(authenticationError = null) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                keyAccess.lock()
                onAuthenticationFailed(exception.message ?: "SECURITY_KEY_AUTHENTICATION_FAILED")
            }
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

    fun setServicePlan(servicePlan: ServicePlan) {
        viewModelScope.launch {
            try {
                registrationRepository.setServicePlan(servicePlan)
                _uiState.update { it.copy(settingsErrorCode = null) }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.e(
                    SETTINGS_LOG_TAG,
                    "$SETTINGS_SAVE_ERROR_CODE: Could not persist service plan.",
                    exception,
                )
                _uiState.update { it.copy(settingsErrorCode = SETTINGS_SAVE_ERROR_CODE) }
            }
        }
    }

    fun clearQrNamespaceRegistrationState() {
        pendingNamespaceQrPayload = null
        _uiState.update {
            it.copy(qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.Idle)
        }
    }

    fun clearAddDatabaseState() {
        if (!_uiState.value.addDatabaseState.isSubmitting) {
            _uiState.update { it.copy(addDatabaseState = AddDatabaseUiState()) }
        }
    }

    fun onDatabaseNameChanged(name: String) {
        if (_uiState.value.addDatabaseState.isSubmitting) return
        _uiState.update {
            it.copy(
                addDatabaseState =
                    it.addDatabaseState.copy(name = name, nameError = null, errorMessage = null)
            )
        }
    }

    fun onDatabaseUrlChanged(url: String) {
        if (_uiState.value.addDatabaseState.isSubmitting) return
        _uiState.update {
            it.copy(
                addDatabaseState =
                    it.addDatabaseState.copy(url = url, urlError = null, errorMessage = null)
            )
        }
    }

    fun onDatabaseEmailChanged(email: String) {
        if (_uiState.value.addDatabaseState.isSubmitting) return
        _uiState.update {
            it.copy(
                addDatabaseState =
                    it.addDatabaseState.copy(email = email, emailError = null, errorMessage = null)
            )
        }
    }

    fun addDatabase() {
        if (_uiState.value.registrationState?.servicePlan != ServicePlan.ENTERPRISE) return
        val form = _uiState.value.addDatabaseState
        if (form.isSubmitting || form.isSuccess) return
        val validation =
            registrationValidator.validateForm(
                RegistrationFormInput(form.email, form.name, form.url)
            )
        if (!validation.isValid) {
            _uiState.update {
                it.copy(
                    addDatabaseState =
                        form.copy(
                            emailError = validation.emailError,
                            nameError = validation.namespaceNameError,
                            urlError = validation.apiUrlError,
                        )
                )
            }
            return
        }
        _uiState.update {
            it.copy(addDatabaseState = form.copy(isSubmitting = true, errorMessage = null))
        }
        viewModelScope.launch {
            try {
                namespacesRepository.addDatabase(form.name, form.url, form.email)
                _uiState.update {
                    it.copy(
                        addDatabaseState =
                            it.addDatabaseState.copy(isSubmitting = false, isSuccess = true)
                    )
                }
            } catch (exception: CancellationException) {
                _uiState.update {
                    it.copy(addDatabaseState = it.addDatabaseState.copy(isSubmitting = false))
                }
                throw exception
            } catch (exception: Exception) {
                val message = exception.message ?: "Database registration failed."
                Log.e("ZeroIntrusionRegistration", "Database registration failed.", exception)
                _uiState.update {
                    it.copy(
                        addDatabaseState =
                            it.addDatabaseState.copy(isSubmitting = false, errorMessage = message)
                    )
                }
            }
        }
    }

    fun setSelectedNamespace(namespace: NamespaceEntry) {
        viewModelScope.launch {
            runCatching { namespacesRepository.setSelectedNamespace(namespace) }
                .onSuccess { _uiState.update { it.copy(namespaceSelectionErrorMessage = null) } }
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Namespace selection failed."
                    _uiState.update { it.copy(namespaceSelectionErrorMessage = message) }
                }
        }
    }

    fun deleteNamespace(namespace: NamespaceEntry) {
        viewModelScope.launch {
            runCatching { namespacesRepository.deleteNamespace(namespace) }
                .onSuccess { _uiState.update { it.copy(namespaceManagementErrorMessage = null) } }
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Namespace delete failed."
                    _uiState.update { it.copy(namespaceManagementErrorMessage = message) }
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
                .onSuccess {}
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Connected devices fetch failed."
                    _uiState.update {
                        it.copy(connectedDevicesState = ConnectedDevicesUiState.Error(message))
                    }
                }
        }
    }

    suspend fun refreshAccountDetailsSnapshot() {
        if (!_uiState.value.isAuthenticated) return
        try {
            namespacesRepository.validateStoredAccounts()
            connectedDevicesRepository.fetchConnectedDevices()
        } catch (exception: CancellationException) {
            throw exception
        } catch (_: Exception) {
            // Leave visible state untouched; background refresh is best-effort.
        }
    }

    fun refreshAccountDetailsAfterRecovery() {
        viewModelScope.launch { refreshAccountDetailsSnapshot() }
    }

    fun loadRemovableDevices() {
        if (_uiState.value.deviceRemovalState is DeviceRemovalUiState.InProgress) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(deviceRemovalState = DeviceRemovalUiState.InProgress) }
            runCatching { connectedDevicesRepository.loadRemovableDevices() }
                .onSuccess { devices ->
                    _uiState.update {
                        it.copy(deviceRemovalState = DeviceRemovalUiState.Success(devices))
                    }
                }
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "The account devices could not be loaded."
                    _uiState.update {
                        it.copy(deviceRemovalState = DeviceRemovalUiState.Error(message))
                    }
                }
        }
    }

    fun deleteDevice(device: RemovableAccountDevice) {
        val currentState =
            _uiState.value.deviceRemovalState as? DeviceRemovalUiState.Success ?: return
        if (currentState.deletingDeviceId != null) {
            return
        }

        _uiState.update {
            it.copy(
                deviceRemovalState =
                    currentState.copy(deletingDeviceId = device.id, errorMessage = null)
            )
        }
        viewModelScope.launch {
            runCatching { connectedDevicesRepository.deleteDevice(device) }
                .onSuccess {
                    if (!_uiState.value.isRegistered) {
                        _uiState.update { it.copy(deviceRemovalState = DeviceRemovalUiState.Idle) }
                    } else {
                        loadRemovableDevices()
                    }
                }
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "The account device could not be deleted."
                    _uiState.update { state ->
                        val successfulState =
                            state.deviceRemovalState as? DeviceRemovalUiState.Success
                        state.copy(
                            deviceRemovalState =
                                successfulState?.copy(
                                    deletingDeviceId = null,
                                    errorMessage = message,
                                ) ?: DeviceRemovalUiState.Error(message)
                        )
                    }
                }
        }
    }

    fun deleteAccount(namespaceUrl: String, accountId: String) {
        if (_uiState.value.registrationState?.servicePlan != ServicePlan.ENTERPRISE) return
        if (_uiState.value.accountDeletionInProgress) {
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(accountDeletionInProgress = true, accountDeletionErrorMessage = null)
            }
            runCatching { namespacesRepository.deleteAccount(namespaceUrl, accountId) }
                .onSuccess {
                    _uiState.update {
                        it.copy(
                            accountDeletionInProgress = false,
                            accountDeletionErrorMessage = null,
                        )
                    }
                }
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Account deletion failed."
                    _uiState.update {
                        it.copy(
                            accountDeletionInProgress = false,
                            accountDeletionErrorMessage = message,
                        )
                    }
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
                            desktopSessionsState =
                                DesktopSessionsUiState.Success(
                                    sessions = sessions,
                                    message = message,
                                ),
                            liveDesktopSessions = sessions,
                        )
                    }
                }
                .onFailure { throwable ->
                    val failureMessage =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Desktop sessions fetch failed."
                    _uiState.update {
                        it.copy(desktopSessionsState = DesktopSessionsUiState.Error(failureMessage))
                    }
                }
        }
    }

    fun refreshLiveDesktopSessions() {
        refreshLiveDesktopSessions(namespaceId = null, namespaceUrl = null, manually = true)
    }

    fun refreshLiveDesktopSessionsInBackground() {
        refreshLiveDesktopSessions(namespaceId = null, namespaceUrl = null)
    }

    private fun refreshLiveDesktopSessions(
        namespaceId: String?,
        namespaceUrl: String?,
        manually: Boolean = false,
    ) {
        if (!_uiState.value.isAuthenticated) return
        val inFlight = _uiState.value.homeRefreshState != HomeRefreshState.IDLE
        _uiState.update {
            it.copy(
                homeRefreshState = it.homeRefreshState.requested(manually),
                homeRefreshErrorCode = null,
            )
        }
        if (inFlight) return
        viewModelScope.launch {
            try {
                val sessions =
                    authenticationRepository.fetchActiveDesktopSessions(
                        namespaceId = namespaceId,
                        namespaceUrl = namespaceUrl,
                    )
                _uiState.update { state ->
                    state.copy(
                        liveDesktopSessions = sessions,
                        desktopSessionsState =
                            when (val desktopState = state.desktopSessionsState) {
                                is DesktopSessionsUiState.Success ->
                                    desktopState.copy(sessions = sessions)
                                else -> state.desktopSessionsState
                            },
                    )
                }
            } catch (exception: CancellationException) {
                throw exception
            } catch (exception: Exception) {
                Log.w(
                    AUTHENTICATION_LOG_TAG,
                    "HOME_REFRESH_FAILED: Availability/session refresh failed (${exception.javaClass.simpleName}).",
                )
                _uiState.update { it.copy(homeRefreshErrorCode = "HOME_REFRESH_FAILED") }
            } finally {
                _uiState.update { it.copy(homeRefreshState = HomeRefreshState.IDLE) }
            }
        }
    }

    fun revokeDesktopSession(session: ActiveDesktopSession) {
        val currentState = _uiState.value.desktopSessionsState as? DesktopSessionsUiState.Success
        if (
            _uiState.value.liveDesktopSessionRevokingAttestationId != null ||
                currentState?.revokingAttestationId != null
        ) {
            return
        }

        _uiState.update {
            it.copy(
                liveDesktopSessionRevokingAttestationId = session.attestationId,
                liveDesktopSessionRevokeErrorMessage = null,
                desktopSessionsState =
                    currentState?.copy(
                        revokingAttestationId = session.attestationId,
                        message = null,
                    ) ?: it.desktopSessionsState,
            )
        }

        viewModelScope.launch {
            runCatching { authenticationRepository.revokeDesktopSession(session) }
                .onSuccess { result ->
                    if (result.success) {
                        _uiState.update { state ->
                            val successState =
                                state.desktopSessionsState as? DesktopSessionsUiState.Success
                            state.copy(
                                liveDesktopSessions =
                                    state.liveDesktopSessions.filterNot {
                                        it.attestationId == result.attestationId
                                    },
                                liveDesktopSessionRevokingAttestationId = null,
                                desktopSessionsState =
                                    successState?.copy(
                                        sessions =
                                            successState.sessions.filterNot {
                                                it.attestationId == result.attestationId
                                            },
                                        revokingAttestationId = null,
                                        message = null,
                                    ) ?: state.desktopSessionsState,
                            )
                        }
                    } else {
                        loadDesktopSessions(
                            message =
                                "The selected desktop session was already changed on the backend. The list has been refreshed."
                        )
                    }
                }
                .onFailure { throwable ->
                    val failureMessage =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Desktop session revoke failed."
                    _uiState.update { state ->
                        val successState =
                            state.desktopSessionsState as? DesktopSessionsUiState.Success
                        state.copy(
                            liveDesktopSessionRevokingAttestationId = null,
                            liveDesktopSessionRevokeErrorMessage = failureMessage,
                            desktopSessionsState =
                                successState?.copy(
                                    revokingAttestationId = null,
                                    message = failureMessage,
                                ) ?: state.desktopSessionsState,
                        )
                    }
                }
        }
    }

    fun dismissLiveDesktopSessionRevokeError() {
        _uiState.update { state ->
            val desktopSessionsState = state.desktopSessionsState as? DesktopSessionsUiState.Success
            state.copy(
                liveDesktopSessionRevokeErrorMessage = null,
                desktopSessionsState =
                    desktopSessionsState?.copy(message = null) ?: state.desktopSessionsState,
            )
        }
    }

    fun registerNamespaceFromQr(qrPayload: String) {
        if (
            _uiState.value.qrNamespaceRegistrationState is QrNamespaceRegistrationUiState.InProgress
        ) {
            Log.d(
                QR_LOG_TAG,
                "Ignored QR namespace connection request because another registration is already in progress.",
            )
            return
        }

        submitQrNamespaceRegistration(qrPayload = qrPayload)
    }

    fun confirmQrNamespaceDeviceSelection(deviceId: String) {
        val pendingPayload = pendingNamespaceQrPayload
        if (pendingPayload.isNullOrBlank()) {
            _uiState.update {
                it.copy(
                    qrNamespaceRegistrationState =
                        QrNamespaceRegistrationUiState.Error(
                            "The scanned QR payload is no longer available. Scan the QR code again."
                        )
                )
            }
            return
        }

        val selectedOption =
            (_uiState.value.qrNamespaceRegistrationState
                    as? QrNamespaceRegistrationUiState.AwaitingDeviceSelection)
                ?.deviceOptions
                ?.firstOrNull { option -> option.deviceId == deviceId }
        if (selectedOption == null) {
            _uiState.update {
                it.copy(
                    qrNamespaceRegistrationState =
                        QrNamespaceRegistrationUiState.Error(
                            "The selected connected device is no longer available. Scan the QR code again."
                        )
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
            _uiState.update {
                it.copy(qrNamespaceRegistrationState = QrNamespaceRegistrationUiState.InProgress)
            }
            runCatching {
                    qrNamespaceRegistrationRepository.registerNamespaceFromQr(
                        qrPayload = qrPayload,
                        rootDeviceAccountId = rootDeviceAccountId,
                    )
                }
                .onSuccess { result: QrNamespaceRegistrationResult ->
                    Log.d(QR_LOG_TAG, "QR operation succeeded: ${result.message}")
                    _uiState.update {
                        it.copy(
                            qrNamespaceRegistrationState =
                                QrNamespaceRegistrationUiState.Success(
                                    message = result.message,
                                    followUpQrPayload = result.followUpQrPayload,
                                )
                        )
                    }
                }
                .onFailure { throwable ->
                    val message =
                        (throwable as? RegistrationFailure)?.message
                            ?: throwable.message
                            ?: "Namespace connection failed."
                    Log.e(QR_LOG_TAG, "QR namespace connection failed: $message", throwable)
                    _uiState.update {
                        it.copy(
                            qrNamespaceRegistrationState =
                                QrNamespaceRegistrationUiState.Error(message)
                        )
                    }
                }
        }
    }

    fun onNotificationDecision(decision: NotificationDecision) {
        val notification = _uiState.value.notificationOverlay.notification ?: return
        val isMatch = _uiState.value.notificationOverlay.canRespond
        stopMobileAuthenticationStatusPolling()
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
                }
                .onSuccess { result ->
                    notificationRepository.clearNotification()
                    _uiState.update {
                        it.copy(
                            notificationOverlay =
                                NotificationOverlayUiState(
                                    namespaceResults = result.namespaceResults
                                )
                        )
                    }
                    if (approved) {
                        postApprovalSessionRefreshJob?.cancel()
                        postApprovalSessionRefreshJob =
                            viewModelScope.launch {
                                delay(1_000)
                                refreshLiveDesktopSessionsInBackground()
                            }
                    }
                }
                .onFailure { throwable ->
                    if (throwable is AuthenticationSessionAlreadyCompleted) {
                        closeNotificationDecision(notificationId)
                        return@onFailure
                    }
                    Log.e(
                        QR_LOG_TAG,
                        "Authentication response failed: ${throwable.message}",
                        throwable,
                    )
                    notificationRepository.clearNotification()
                    _uiState.update {
                        it.copy(
                            notificationOverlay =
                                NotificationOverlayUiState(
                                    notification = notification,
                                    mismatchError =
                                        throwable.message
                                            ?: "Failed to send authentication response.",
                                    canRespond = false,
                                    namespaceResults = emptyList(),
                                )
                        )
                    }
                }
        }
    }

    fun dismissNotificationMismatch() {
        stopMobileAuthenticationStatusPolling()
        notificationRepository.clearNotification()
        _uiState.update { it.copy(notificationOverlay = NotificationOverlayUiState()) }
    }

    private fun ensureFcmTokenSync(state: RegistrationState) {
        if (!_uiState.value.isAuthenticated) return
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
                    lastFcmSyncKey =
                        syncedState.accountId + ":" + (syncedState.fcmToken ?: "missing")
                    _uiState.update {
                        it.copy(
                            fcmSyncState =
                                FcmSyncUiState.Success(
                                    syncedState.fcmToken?.toPreview() ?: "token unavailable"
                                )
                        )
                    }
                }
                .onFailure { throwable ->
                    lastFcmSyncKey = null
                    _uiState.update {
                        it.copy(
                            fcmSyncState = FcmSyncUiState.Error(buildFcmErrorMessage(throwable))
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

    private fun synchronizeMobileAuthenticationStatusPolling(overlay: NotificationOverlayUiState) {
        if (!_uiState.value.isAuthenticated) {
            stopMobileAuthenticationStatusPolling()
            return
        }
        val notification = overlay.notification
        val notificationId = notification?.data?.get("notificationId")?.trim().orEmpty()
        if (!overlay.canRespond || notification == null || notificationId.isEmpty()) {
            stopMobileAuthenticationStatusPolling()
            return
        }
        if (
            mobileAuthenticationStatusNotificationId == notificationId &&
                mobileAuthenticationStatusPollingJob?.isActive == true
        ) {
            return
        }

        val namespaceUrl =
            matchingNotificationNamespace(notification, storedNamespaces)
                ?.namespaceUrl
                ?.trim()
                ?.trimEnd('/')
                .orEmpty()
        val mobileDeviceId = _uiState.value.registrationState?.deviceId?.trim().orEmpty()
        if (namespaceUrl.isEmpty() || mobileDeviceId.isEmpty()) {
            stopMobileAuthenticationStatusPolling()
            return
        }

        stopMobileAuthenticationStatusPolling()
        mobileAuthenticationStatusNotificationId = notificationId
        mobileAuthenticationStatusPollingJob =
            viewModelScope.launch {
                while (true) {
                    try {
                        val status =
                            authenticationRepository.fetchMobileAuthenticationStatus(
                                namespaceUrl = namespaceUrl,
                                accountId = notification.accountId,
                                mobileDeviceId = mobileDeviceId,
                                notificationId = notificationId,
                            )
                        if (status.isTerminal) {
                            closeNotificationDecision(notificationId)
                            return@launch
                        }
                    } catch (exception: CancellationException) {
                        throw exception
                    } catch (exception: Exception) {
                        Log.w(
                            AUTHENTICATION_LOG_TAG,
                            "Authentication status polling failed.",
                            exception,
                        )
                    }
                    delay(MOBILE_AUTHENTICATION_STATUS_POLL_MILLIS)
                }
            }
    }

    private fun stopMobileAuthenticationStatusPolling() {
        mobileAuthenticationStatusPollingJob?.cancel()
        mobileAuthenticationStatusPollingJob = null
        mobileAuthenticationStatusNotificationId = null
    }

    private fun closeNotificationDecision(notificationId: String) {
        val activeNotificationId =
            _uiState.value.notificationOverlay.notification?.data?.get("notificationId")?.trim()
        if (activeNotificationId != notificationId) {
            return
        }
        stopMobileAuthenticationStatusPolling()
        notificationRepository.clearNotification()
        _uiState.update { it.copy(notificationOverlay = NotificationOverlayUiState()) }
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
                mismatchError =
                    "Push notification received, but no registered account is stored on this device.",
                canRespond = false,
            )
        }

        if (notification.accountId.isBlank() || notification.mobileDeviceId.isBlank()) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError =
                    "Push notification blocked because the delivered Firebase data payload did not include a non-empty accountId and mobileDeviceId. Put both values inside message.data so the app can verify the target device.",
                canRespond = false,
            )
        }

        if (notification.data["notificationId"].isNullOrBlank()) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError =
                    "Push notification blocked because the delivered Firebase data payload did not include notificationId.",
                canRespond = false,
            )
        }

        if (!registrationState.deviceId.equals(notification.mobileDeviceId, ignoreCase = true)) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError =
                    "Push notification targeted mobileDeviceId=${notification.mobileDeviceId}, but this phone is registered as deviceId=${registrationState.deviceId}.",
                canRespond = false,
            )
        }

        val targetNamespaceId = notification.data.firstNonBlankValue(NAMESPACE_ID_KEYS)
        if (targetNamespaceId.isBlank()) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError =
                    "Push notification blocked because the delivered Firebase data payload did not include namespaceId.",
                canRespond = false,
            )
        }

        val targetNamespaceUrl = notification.data.firstNonBlankValue(NAMESPACE_URL_KEYS)
        val matchingNamespace = matchingNotificationNamespace(notification, knownNamespaces)

        if (matchingNamespace == null) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError =
                    "Push notification namespaceId/accountId pair does not match any locally stored namespace for mobileDeviceId=${notification.mobileDeviceId}. Received namespaceId=$targetNamespaceId, namespaceUrl=${targetNamespaceUrl.ifBlank { "(empty)" }}, accountId=${notification.accountId}, desktopDeviceId=${notification.desktopDeviceId}.",
                canRespond = false,
            )
        }

        return NotificationOverlayUiState(
            notification = notification,
            mismatchError = null,
            canRespond = true,
        )
    }

    private fun matchingNotificationNamespace(
        notification: BackendPushNotification,
        knownNamespaces: List<NamespaceEntry>,
    ): NamespaceEntry? {
        val targetNamespaceId = notification.data.firstNonBlankValue(NAMESPACE_ID_KEYS)
        val targetNamespaceUrl = notification.data.firstNonBlankValue(NAMESPACE_URL_KEYS)
        return knownNamespaces.firstOrNull { namespace ->
            namespace.accountId.equals(notification.accountId, ignoreCase = true) &&
                namespace.namespaceId.equals(targetNamespaceId, ignoreCase = true) &&
                (targetNamespaceUrl.isBlank() ||
                    namespace.namespaceUrl
                        .trim()
                        .trimEnd('/')
                        .equals(targetNamespaceUrl.trim().trimEnd('/'), ignoreCase = true))
        }
    }

    private fun String.toPreview(): String =
        when {
            length <= 18 -> this
            else -> take(10) + "..." + takeLast(6)
        }
}

private fun Map<String, String>.firstNonBlankValue(keys: List<String>): String {
    return keys.firstNotNullOfOrNull { key -> get(key)?.takeIf { it.isNotBlank() } }.orEmpty()
}
