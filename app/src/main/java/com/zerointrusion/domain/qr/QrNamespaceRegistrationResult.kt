package com.zerointrusion.domain.qr

data class QrNamespaceRegistrationResult(
    val message: String,
    val followUpQrPayload: String? = null,
)