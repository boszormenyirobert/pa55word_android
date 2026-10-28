package com.zerointrusion.data.storage

import android.content.Context
import android.content.ContextWrapper
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.recovery.RecoveryPendingState
import com.zerointrusion.domain.recovery.RecoveryStage
import java.util.UUID
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertFalse
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RegistrationStateStoreTest {
    @Test
    fun selectedModeSurvivesReopeningAndBackgroundRegistrationUpdates() = runBlocking {
        val context = isolatedContext()
        val state = RegistrationState(
            accountId = "test-account",
            mobileDeviceId = "test-mobile",
            deviceName = "Test phone",
            namespaceName = "Test database",
            namespaceUrl = "https://example.test",
            namespaceId = "test-namespace",
            email = "test@example.test",
            encDek = "test-encrypted-key",
        )

        try {
            val store = EncryptedRegistrationStateStore(context, Gson())
            store.save(state)
            assertEquals(state, store.observeState().first())

            store.setCorporateMode(true)
            store.save(state.copy(fcmToken = "updated-token"))
            val reopened = EncryptedRegistrationStateStore(context, Gson())
            assertEquals(
                state.copy(corporateMode = true, fcmToken = "updated-token"),
                reopened.observeState().first(),
            )

            reopened.setCorporateMode(false)
            assertEquals(
                state.copy(fcmToken = "updated-token"),
                EncryptedRegistrationStateStore(context, Gson()).observeState().first(),
            )

            val replacement = state.copy(accountId = "other-account", corporateMode = true)
            reopened.save(replacement)
            assertEquals(replacement, EncryptedRegistrationStateStore(context, Gson()).observeState().first())

            reopened.clear()
            assertNull(reopened.observeState().first())
            assertNull(EncryptedRegistrationStateStore(context, Gson()).observeState().first())

            reopened.setCorporateMode(true)
            assertNull(reopened.observeState().first())
        } finally {
            context.deleteSharedPreferences(AppConstants.ENCRYPTED_PREFS_NAME)
        }
    }

    @Test
    fun legacyPhoneIsRemovedFromBothEncryptedRecordsWithoutLosingOtherFields() = runBlocking {
        val context = isolatedContext()
        val gson = Gson()
        try {
            val preferences = EncryptedSharedPreferences.create(
                context,
                AppConstants.ENCRYPTED_PREFS_NAME,
                MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
            )
            val registration = RegistrationState(
                accountId = "test-account",
                mobileDeviceId = "test-mobile",
                deviceName = "Test device",
                namespaceName = "Test database",
                namespaceUrl = "https://example.test",
                namespaceId = "test-namespace",
                email = "test@example.test",
                encDek = "test-encrypted-key",
                corporateMode = true,
            )
            val pending = RecoveryPendingState(
                recoveryId = "test-recovery",
                apiBaseUrl = "https://example.test",
                candidateId = "test-candidate",
                mobileDeviceId = registration.mobileDeviceId,
                deviceName = registration.deviceName,
                publicKey = "test-public-key",
                pairingCode = "test-code",
                pairingCodeHash = "test-hash",
                candidateRequestId = "test-request",
                stage = RecoveryStage.DEK_RECEIVED,
                accountId = registration.accountId,
                email = registration.email,
                encDek = registration.encDek,
            )
            val records = mapOf(
                AppConstants.ENCRYPTED_PREFS_STATE_KEY to registration,
                AppConstants.ENCRYPTED_PREFS_RECOVERY_PENDING_KEY to pending,
            ).mapValues { (_, value) ->
                gson.toJsonTree(value).asJsonObject.apply {
                    addProperty("phone", "+36123456789")
                    addProperty("futureField", "preserve-me")
                }
            }
            records.forEach { (key, json) ->
                preferences.edit().putString(key, gson.toJson(json)).commit()
            }

            assertEquals(registration, EncryptedRegistrationStateStore(context, gson).observeState().first())
            assertEquals(pending, EncryptedRecoveryPendingStateStore(context, gson).observe().first())

            records.forEach { (key, expected) ->
                expected.remove("phone")
                val actual = gson.fromJson(preferences.getString(key, null), com.google.gson.JsonObject::class.java)
                assertEquals(expected, actual)
                assertFalse(actual.has("phone"))
                assertEquals(gson.toJson(expected), preferences.readStateWithoutPhone(key, gson))
            }
        } finally {
            context.deleteSharedPreferences(AppConstants.ENCRYPTED_PREFS_NAME)
        }
    }

    private fun isolatedContext(): ContextWrapper {
        val suffix = UUID.randomUUID().toString()
        return object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getApplicationContext(): Context = this

            override fun getSharedPreferences(name: String, mode: Int) =
                super.getSharedPreferences("${name}_test_$suffix", mode)

            override fun deleteSharedPreferences(name: String): Boolean =
                super.deleteSharedPreferences("${name}_test_$suffix")
        }
    }
}