package com.zerointrusion.data.security

import com.zerointrusion.domain.common.RegistrationFailure
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricKeyAccessTest {
    private var elapsed = 1_000L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val access = BiometricKeyAccess({ elapsed }, scope)

    @After
    fun cleanup() {
        scope.cancel()
    }

    @Test
    fun startsLockedAndCannotAuthenticateInBackground() {
        assertFalse(access.unlocked.value)
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.beginAuthentication()
        }
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
    }

    @Test
    fun confirmedAuthenticationAllowsAccessOnlyWithinWindow() {
        authenticate()
        access.requireUnlocked()
        assertTrue(access.unlocked.value)
        elapsed += BiometricKeyAccess.VALIDITY_SECONDS * 1_000L - 1
        access.requireUnlocked()
        elapsed++
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
        assertFalse(access.unlocked.value)
    }

    @Test
    fun backgroundLocksAndForegroundDoesNotUnlock() {
        authenticate()
        access.setForeground(false)
        access.setForeground(true)
        assertFalse(access.unlocked.value)
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
    }

    @Test
    fun backgroundTransitionInvalidatesPendingAuthentication() {
        access.setForeground(true)
        val attempt = access.beginAuthentication()
        access.setForeground(false)
        access.setForeground(true)
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.confirmAuthentication(attempt)
        }
        assertFalse(access.unlocked.value)
    }

    @Test
    fun explicitLockRequiresFreshAuthentication() {
        authenticate()
        access.lock()
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
        authenticate()
        access.requireUnlocked()
    }

    @Test
    fun staleExpirationCannotLockNewAuthentication() {
        authenticate()
        val previousGeneration = access.beginAuthentication()
        authenticate()
        access.expireAuthentication(previousGeneration)
        access.requireUnlocked()
        assertTrue(access.unlocked.value)
    }

    @Test
    fun scheduledExpirationLocksCurrentAuthentication() {
        authenticate()
        access.expireAuthentication(access.beginAuthentication())
        assertFalse(access.unlocked.value)
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
    }

    @Test
    fun authenticationAttemptCannotBeReused() {
        access.setForeground(true)
        val attempt = access.beginAuthentication()
        access.confirmAuthentication(attempt)
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.confirmAuthentication(attempt)
        }
    }

    @Test
    fun countdownUsesOriginalMonotonicDeadlineWithoutExtendingAccess() {
        authenticate()
        val generation = access.beginAuthentication()
        assertEquals(300, access.remainingSeconds.value)
        elapsed += 1_001L
        assertTrue(access.updateRemainingSeconds(generation))
        assertEquals(299, access.remainingSeconds.value)
        elapsed += 298_000L
        assertTrue(access.updateRemainingSeconds(generation))
        assertEquals(1, access.remainingSeconds.value)
        elapsed += 999L
        assertFalse(access.updateRemainingSeconds(generation))
        assertEquals(0, access.remainingSeconds.value)
        assertFalse(access.unlocked.value)
    }

    @Test
    fun delayedCountdownTickExpiresInsteadOfStartingAnotherWindow() {
        authenticate()
        val generation = access.beginAuthentication()
        elapsed += 600_000L
        assertFalse(access.updateRemainingSeconds(generation))
        assertEquals(0, access.remainingSeconds.value)
        assertFalse(access.unlocked.value)
    }

    @Test
    fun successfulReauthenticationRestartsAnAlreadyValidWindow() {
        authenticate()
        val previousGeneration = access.beginAuthentication()
        elapsed += 290_000L
        access.updateRemainingSeconds(previousGeneration)
        assertEquals(10, access.remainingSeconds.value)

        access.confirmAuthentication(access.beginAuthentication())
        assertEquals(300, access.remainingSeconds.value)
        assertTrue(access.unlocked.value)

        elapsed += 10_000L
        access.expireAuthentication(previousGeneration)
        access.requireUnlocked()
        assertTrue(access.updateRemainingSeconds(access.beginAuthentication()))
        assertEquals(290, access.remainingSeconds.value)

        elapsed += 290_000L
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
    }

    @Test
    fun startingReauthenticationDoesNotExtendTheExistingWindow() {
        authenticate()
        elapsed += 290_000L
        access.beginAuthentication()
        elapsed += 10_000L
        assertThrows(RegistrationFailure.BiometricAuthenticationRequired::class.java) {
            access.requireUnlocked()
        }
        assertEquals(0, access.remainingSeconds.value)
    }

    @Test
    fun staleCountdownCannotUpdateNewAuthentication() {
        authenticate()
        val previousGeneration = access.beginAuthentication()
        elapsed += 10_000L
        authenticate()
        assertFalse(access.updateRemainingSeconds(previousGeneration))
        assertEquals(300, access.remainingSeconds.value)
        access.setForeground(false)
        assertEquals(0, access.remainingSeconds.value)
    }

    private fun authenticate() {
        access.setForeground(true)
        access.confirmAuthentication(access.beginAuthentication())
    }
}
