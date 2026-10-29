package com.zerointrusion.data.security

import android.os.SystemClock
import com.zerointrusion.domain.common.RegistrationFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@Singleton
class BiometricKeyAccess
internal constructor(private val now: () -> Long, private val scope: CoroutineScope) {
    @Inject
    constructor() :
        this(SystemClock::elapsedRealtime, CoroutineScope(SupervisorJob() + Dispatchers.Default))

    private val mutableUnlocked = MutableStateFlow(false)
    val unlocked: StateFlow<Boolean> = mutableUnlocked.asStateFlow()
    private val mutableRemainingSeconds = MutableStateFlow(0)
    val remainingSeconds: StateFlow<Int> = mutableRemainingSeconds.asStateFlow()
    private var foreground = false
    private var generation = 0L
    private var expiresAt = 0L
    private var expirationJob: Job? = null

    @Synchronized
    fun setForeground(value: Boolean) {
        foreground = value
        if (!value) lock()
    }

    @Synchronized
    fun beginAuthentication(): Long {
        if (!foreground) throw RegistrationFailure.BiometricAuthenticationRequired()
        return generation
    }

    @Synchronized
    internal fun requireAuthenticationAttempt(attempt: Long) {
        if (!foreground || attempt != generation) {
            throw RegistrationFailure.BiometricAuthenticationRequired()
        }
    }

    @Synchronized
    internal fun confirmAuthentication(attempt: Long) {
        requireAuthenticationAttempt(attempt)
        expirationJob?.cancel()
        val authenticatedGeneration = ++generation
        expiresAt = now() + VALIDITY_SECONDS * 1_000L
        mutableRemainingSeconds.value = VALIDITY_SECONDS
        mutableUnlocked.value = true
        expirationJob =
            scope.launch {
                do {
                    delay(1_000L)
                } while (updateRemainingSeconds(authenticatedGeneration))
            }
    }

    @Synchronized
    internal fun updateRemainingSeconds(authenticatedGeneration: Long): Boolean {
        if (generation != authenticatedGeneration) return false
        val remainingMillis = (expiresAt - now()).coerceAtLeast(0L)
        mutableRemainingSeconds.value = ((remainingMillis + 999L) / 1_000L).toInt()
        if (remainingMillis == 0L) {
            expireAuthentication(authenticatedGeneration)
            return false
        }
        return true
    }

    @Synchronized
    internal fun expireAuthentication(authenticatedGeneration: Long) {
        if (generation == authenticatedGeneration) lock()
    }

    @Synchronized
    fun requireUnlocked() {
        if (!foreground || !mutableUnlocked.value || now() >= expiresAt) {
            lock()
            throw RegistrationFailure.BiometricAuthenticationRequired()
        }
    }

    @Synchronized
    fun lock() {
        generation++
        expiresAt = 0L
        mutableRemainingSeconds.value = 0
        mutableUnlocked.value = false
        expirationJob?.cancel()
        expirationJob = null
    }

    companion object {
        const val VALIDITY_SECONDS = 300
    }
}
