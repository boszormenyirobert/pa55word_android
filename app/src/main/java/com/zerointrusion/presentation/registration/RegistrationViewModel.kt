package com.zerointrusion.presentation.registration

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.RegistrationRepository
import com.zerointrusion.domain.registration.RegistrationFormInput
import com.zerointrusion.domain.registration.RegistrationValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@HiltViewModel
class RegistrationViewModel @Inject constructor(
    private val registrationRepository: RegistrationRepository,
    private val validator: RegistrationValidator,
) : ViewModel() {
    private companion object {
        const val REG_LOG_TAG = "ZeroIntrusionRegistration"
        const val MOBILE_REGISTRATION_RETRY_DELAY_MILLIS = 2_000L
    }

    private val _uiState = MutableStateFlow(RegistrationUiState())
    val uiState: StateFlow<RegistrationUiState> = _uiState.asStateFlow()
    private var mobileRegistrationJob: Job? = null

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

    fun onAdditionalApiUrlChanged(value: String) {
        _uiState.update { it.copy(additionalApiUrl = value, additionalApiUrlError = null, errorMessage = null) }
    }

    fun onThirdApiUrlChanged(value: String) {
        _uiState.update { it.copy(thirdApiUrl = value, thirdApiUrlError = null, errorMessage = null) }
    }

    fun register() {
        val input = RegistrationFormInput(
            email = _uiState.value.email,
            phone = _uiState.value.phone,
            namespaceName = _uiState.value.namespaceName,
            apiUrl = _uiState.value.apiUrl,
            additionalApiUrls = listOf(_uiState.value.additionalApiUrl, _uiState.value.thirdApiUrl),
        )
        Log.d(REG_LOG_TAG, "REG submit tapped. apiUrl=${input.apiUrl.trim()} namespace=${input.namespaceName.trim()}")
        val validation = validator.validateForm(input)
        val additionalUrlErrors = input.additionalApiUrls.map { url ->
            url.trim().takeIf { it.isNotEmpty() }?.let(validator::validateNamespaceUrl)
        }
        if (!validation.isValid || additionalUrlErrors.any { it != null }) {
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
                    additionalApiUrlError = additionalUrlErrors.getOrNull(0),
                    thirdApiUrlError = additionalUrlErrors.getOrNull(1),
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

    fun registerNewMobileToExistingAccount() {
        val input = RegistrationFormInput(
            email = _uiState.value.email,
            phone = _uiState.value.phone,
            namespaceName = _uiState.value.namespaceName,
            apiUrl = _uiState.value.apiUrl,
            additionalApiUrls = listOf(_uiState.value.additionalApiUrl, _uiState.value.thirdApiUrl),
        )
        val validation = validator.validateForm(input)
        val additionalUrlErrors = input.additionalApiUrls.map { url ->
            url.trim().takeIf { it.isNotEmpty() }?.let(validator::validateNamespaceUrl)
        }
        if (!validation.isValid || additionalUrlErrors.any { it != null }) {
            _uiState.update {
                it.copy(
                    emailError = validation.emailError,
                    phoneError = validation.phoneError,
                    namespaceNameError = validation.namespaceNameError,
                    apiUrlError = validation.apiUrlError,
                    additionalApiUrlError = additionalUrlErrors.getOrNull(0),
                    thirdApiUrlError = additionalUrlErrors.getOrNull(1),
                    errorMessage = null,
                )
            }
            return
        }

        mobileRegistrationJob?.cancel()
        mobileRegistrationJob = viewModelScope.launch {
            runCatching { registrationRepository.prepareMobileDeviceRegistration(input) }
                .onFailure(::showMobileRegistrationFailure)
                .onSuccess { preparation ->
                    _uiState.update {
                        it.copy(
                            isAwaitingMobileRegistration = true,
                            mobileRegistrationQrPayload = preparation.qrPayload,
                            errorMessage = null,
                        )
                    }
                    while (true) {
                        val failure = runCatching {
                            registrationRepository.awaitMobileDeviceRegistration(preparation)
                        }.exceptionOrNull() ?: break
                        if (failure is CancellationException) {
                            throw failure
                        }
                        Log.w(REG_LOG_TAG, "Mobile registration SSE failed; opening a new request in 2 seconds.", failure)
                        _uiState.update {
                            it.copy(
                                isAwaitingMobileRegistration = true,
                                errorMessage = "Waiting for approval. Opening a new registration stream in 2 seconds...",
                            )
                        }
                        delay(MOBILE_REGISTRATION_RETRY_DELAY_MILLIS)
                    }
                }
        }
    }

    private fun showMobileRegistrationFailure(throwable: Throwable) {
        val message = (throwable as? RegistrationFailure)?.message
            ?: throwable.message
            ?: "Mobile device registration failed."
        _uiState.update {
            it.copy(
                isAwaitingMobileRegistration = false,
                errorMessage = message,
            )
        }
    }

}