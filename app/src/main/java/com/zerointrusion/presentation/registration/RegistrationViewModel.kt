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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

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