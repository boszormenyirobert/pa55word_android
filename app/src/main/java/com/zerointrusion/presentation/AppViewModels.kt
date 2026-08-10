package com.zerointrusion.presentation

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerointrusion.domain.BackendPushNotification
import com.zerointrusion.domain.NotificationDecision
import com.zerointrusion.domain.NotificationRepository
import com.zerointrusion.domain.RegistrationFailure
import com.zerointrusion.domain.RegistrationFormInput
import com.zerointrusion.domain.RegistrationRepository
import com.zerointrusion.domain.RegistrationState
import com.zerointrusion.domain.RegistrationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed class FcmSyncUiState {
    data object Idle : FcmSyncUiState()
    data object InProgress : FcmSyncUiState()
    data class Success(val tokenPreview: String) : FcmSyncUiState()
    data class Error(val message: String) : FcmSyncUiState()
}

sealed class NewDeviceRegistrationUiState {
    data object Idle : NewDeviceRegistrationUiState()
    data object InProgress : NewDeviceRegistrationUiState()
    data class Success(val deviceId: String) : NewDeviceRegistrationUiState()
    data class Error(val message: String) : NewDeviceRegistrationUiState()
}

sealed class ConnectedDevicesUiState {
    data object Idle : ConnectedDevicesUiState()
    data object InProgress : ConnectedDevicesUiState()
    data class Success(val responseBody: String) : ConnectedDevicesUiState()
    data class Error(val message: String) : ConnectedDevicesUiState()
}

data class NotificationOverlayUiState(
    val notification: BackendPushNotification? = null,
    val mismatchError: String? = null,
    val canRespond: Boolean = false,
) {
    val isVisible: Boolean = notification != null
}

data class AppUiState(
    val isInitialized: Boolean = false,
    val isAuthenticated: Boolean = false,
    val isRegistered: Boolean = false,
    val registrationState: RegistrationState? = null,
    val authenticationError: String? = null,
    val fcmSyncState: FcmSyncUiState = FcmSyncUiState.Idle,
    val newDeviceRegistrationState: NewDeviceRegistrationUiState = NewDeviceRegistrationUiState.Idle,
    val connectedDevicesState: ConnectedDevicesUiState = ConnectedDevicesUiState.Idle,
    val notificationOverlay: NotificationOverlayUiState = NotificationOverlayUiState(),
) {
    val currentRoute: String
        get() = when {
            !isInitialized -> AppRoute.Loading.route
            !isAuthenticated -> AppRoute.Security.route
            isRegistered -> AppRoute.Home.route
            else -> AppRoute.Registration.route
        }
}

data class RegistrationUiState(
    val email: String = "",
    val phone: String = "",
    val namespaceName: String = "",
    val apiUrl: String = "",
    val emailError: String? = null,
    val phoneError: String? = null,
    val namespaceNameError: String? = null,
    val apiUrlError: String? = null,
    val isSubmitting: Boolean = false,
    val errorMessage: String? = null,
)

@HiltViewModel
class AppViewModel @Inject constructor(
    private val registrationRepository: RegistrationRepository,
    private val notificationRepository: NotificationRepository,
) : ViewModel() {
    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
    }

    private val _uiState = MutableStateFlow(AppUiState())
    val uiState: StateFlow<AppUiState> = _uiState.asStateFlow()
    private var lastFcmSyncKey: String? = null
    private var fcmSyncInFlight: Boolean = false

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
                    )
                }

                if (state != null) {
                    ensureFcmTokenSync(state)
                }
            }
        }

        viewModelScope.launch {
            notificationRepository.observeNotification().collect { notification ->
                _uiState.update { state ->
                    state.copy(
                        notificationOverlay = resolveNotificationOverlay(
                            notification = notification,
                            registrationState = state.registrationState,
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

    fun clearNewDeviceRegistrationState() {
        _uiState.update { it.copy(newDeviceRegistrationState = NewDeviceRegistrationUiState.Idle) }
    }

    fun loadConnectedDevices() {
        if (_uiState.value.connectedDevicesState is ConnectedDevicesUiState.InProgress) {
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(connectedDevicesState = ConnectedDevicesUiState.InProgress) }
            runCatching { registrationRepository.fetchConnectedDevices() }
                .onSuccess { responseBody ->
                    _uiState.update {
                        it.copy(connectedDevicesState = ConnectedDevicesUiState.Success(responseBody))
                    }
                }
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

    fun registerNewDeviceFromQr(qrPayload: String) {
        if (_uiState.value.newDeviceRegistrationState is NewDeviceRegistrationUiState.InProgress) {
            Log.d(QR_LOG_TAG, "Ignored QR registration request because another registration is already in progress.")
            return
        }

        viewModelScope.launch {
            Log.d(QR_LOG_TAG, "QR payload received in ViewModel. length=${qrPayload.length}")
            _uiState.update { it.copy(newDeviceRegistrationState = NewDeviceRegistrationUiState.InProgress) }
            runCatching { registrationRepository.registerNewDeviceFromQr(qrPayload) }
                .onSuccess { dto ->
                    Log.d(QR_LOG_TAG, "QR registration succeeded for deviceId=${dto.device.deviceId}")
                    _uiState.update {
                        it.copy(
                            newDeviceRegistrationState = NewDeviceRegistrationUiState.Success(dto.device.deviceId),
                        )
                    }
                }
                .onFailure { throwable ->
                    val message = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "New device registration failed."
                    Log.e(QR_LOG_TAG, "QR registration failed: $message", throwable)
                    _uiState.update {
                        it.copy(newDeviceRegistrationState = NewDeviceRegistrationUiState.Error(message))
                    }
                }
        }
    }

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
            runCatching {
                registrationRepository.respondToAuthentication(
                    notificationId = notificationId,
                    accountId = notification.accountId,
                    deviceId = notification.deviceId,
                    approved = approved,
                )
            }.onSuccess {
                notificationRepository.clearNotification()
                _uiState.update { it.copy(notificationOverlay = NotificationOverlayUiState()) }
            }.onFailure { throwable ->
                Log.e(QR_LOG_TAG, "Authentication response failed: ${throwable.message}", throwable)
                notificationRepository.clearNotification()
                _uiState.update {
                    it.copy(
                        notificationOverlay = NotificationOverlayUiState(
                            notification = notification,
                            mismatchError = throwable.message ?: "Failed to send authentication response.",
                            canRespond = false,
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

        if (notification.accountId.isBlank() || notification.deviceId.isBlank()) {
            return NotificationOverlayUiState(
                notification = notification,
                mismatchError = "Push notification blocked because the delivered Firebase data payload did not include a non-empty accountId and deviceId. Put both values inside message.data so the app can verify the target device.",
                canRespond = false,
            )
        }

        val canRespond = notification.accountId == registrationState.accountId && notification.deviceId == registrationState.deviceId
        return NotificationOverlayUiState(
            notification = notification,
            mismatchError = if (canRespond) null else "Push notification account/device mismatch. Expected ${registrationState.accountId} / ${registrationState.deviceId}, received ${notification.accountId} / ${notification.deviceId}.",
            canRespond = canRespond,
        )
    }

    private fun String.toPreview(): String = when {
        length <= 18 -> this
        else -> take(10) + "..." + takeLast(6)
    }
}

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val registrationRepository: RegistrationRepository,
    private val validator: RegistrationValidator,
) : ViewModel() {
    private companion object {
        const val REG_LOG_TAG = "ZeroIntrusionRegistration"
    }

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()

    fun onEmailChanged(value: String) {
        _uiState.update { it.copy(email = value, emailError = null, errorMessage = null) }
    }

    fun onPhoneChanged(value: String) {
        _uiState.update { it.copy(phone = value, phoneError = null, errorMessage = null) }
    }

    fun onNamespaceNameChanged(value: String) {
        _uiState.update { it.copy(namespaceName = value, namespaceNameError = null, errorMessage = null) }
    }

    fun onApiUrlChanged(value: String) {
        _uiState.update { it.copy(apiUrl = value, apiUrlError = null, errorMessage = null) }
    }

    fun register() {
        val input = RegistrationFormInput(
            email = _uiState.value.email,
            phone = _uiState.value.phone,
            namespaceName = _uiState.value.namespaceName,
            apiUrl = _uiState.value.apiUrl,
        )
        Log.d(REG_LOG_TAG, "REG submit tapped. apiUrl=${input.apiUrl.trim()} namespace=${input.namespaceName.trim()}")
        val validation = validator.validateForm(input)
        if (!validation.isValid) {
            Log.e(
                REG_LOG_TAG,
                "REG validation failed. email=${validation.emailError} phone=${validation.phoneError} namespace=${validation.namespaceNameError} apiUrl=${validation.apiUrlError}",
            )
            _uiState.update {
                it.copy(
                    emailError = validation.emailError,
                    phoneError = validation.phoneError,
                    namespaceNameError = validation.namespaceNameError,
                    apiUrlError = validation.apiUrlError,
                    errorMessage = null,
                )
            }
            return
        }

        viewModelScope.launch {
            Log.d(REG_LOG_TAG, "REG coroutine started.")
            _uiState.update { it.copy(isSubmitting = true, errorMessage = null) }
            runCatching { registrationRepository.register(input) }
                .onFailure { throwable ->
                    val message = (throwable as? RegistrationFailure)?.message
                        ?: throwable.message
                        ?: "Registration failed."
                    Log.e(REG_LOG_TAG, "REG failed: $message", throwable)
                    _uiState.update {
                        it.copy(
                            isSubmitting = false,
                            errorMessage = message,
                        )
                    }
                }
                .onSuccess {
                    Log.d(REG_LOG_TAG, "REG completed successfully.")
                    _uiState.update { it.copy(isSubmitting = false, errorMessage = null) }
                }
        }
    }
}