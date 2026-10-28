package com.zerointrusion.domain.registration

import com.google.gson.annotations.SerializedName

data class Namespace(
    val namespaceId: String,
    val name: String,
    val url: String,
)

data class Device(
    @SerializedName("mobileDeviceId")
    val mobileDeviceId: String,
    val publicKey: String,
    val deviceName: String,
) {
    val deviceId: String
        get() = mobileDeviceId
}

data class DekPayload(
    @SerializedName("mobileDeviceId")
    val mobileDeviceId: String,
    val encDek: String,
) {
    val deviceId: String
        get() = mobileDeviceId
}

data class RegistrationPayload(
    val namespace: Namespace,
    val device: Device,
    val dek: DekPayload,
    val email: String,
)

data class RegistrationResult(
    val accountId: String,
)

data class MobileDeviceRegistrationPreparation(
    val qrPayload: String,
    val mobileDeviceId: String,
    val deviceName: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val namespaceUrls: List<String>,
    val namespaceId: String,
    val email: String,
)

data class RegistrationState(
    val accountId: String,
    @SerializedName("mobileDeviceId")
    val mobileDeviceId: String,
    val deviceName: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val namespaceId: String,
    val email: String,
    val encDek: String?,
    val root: Boolean = false,
    val fcmToken: String? = null,
    val pendingAdditionalNamespacesScan: Boolean = false,
    val completedAdditionalNamespaceKeys: List<String>? = null,
    val corporateMode: Boolean = false,
) {
    val deviceId: String
        get() = mobileDeviceId
}

data class RegistrationFormInput(
    val email: String,
    val namespaceName: String,
    val apiUrl: String,
    val additionalApiUrls: List<String> = emptyList(),
)

data class RegistrationFormValidation(
    val emailError: String? = null,
    val namespaceNameError: String? = null,
    val apiUrlError: String? = null,
) {
    val isValid: Boolean =
        emailError == null &&
            namespaceNameError == null &&
            apiUrlError == null
}