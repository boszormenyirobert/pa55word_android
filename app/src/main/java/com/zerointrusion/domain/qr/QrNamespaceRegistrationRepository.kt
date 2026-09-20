package com.zerointrusion.domain.qr

interface QrNamespaceRegistrationRepository {
    suspend fun registerNamespaceFromQr(
        qrPayload: String,
        rootDeviceAccountId: String? = null,
    ): QrNamespaceRegistrationResult
}