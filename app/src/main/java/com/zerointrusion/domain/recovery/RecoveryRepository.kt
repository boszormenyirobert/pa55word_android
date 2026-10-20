package com.zerointrusion.domain.recovery

interface RecoveryRepository {
    suspend fun current(): RecoveryPendingState?
    suspend fun start(qrPayload: RecoveryQrPayload): RecoveryProgress
    suspend fun refresh(): RecoveryProgress
    suspend fun cancel()
    suspend fun retryFcm(): String?
}