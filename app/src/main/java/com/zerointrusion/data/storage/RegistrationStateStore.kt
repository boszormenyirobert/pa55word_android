package com.zerointrusion.data.storage

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.registration.RegistrationState
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface RegistrationStateStore {
    fun observeState(): Flow<RegistrationState?>

    suspend fun save(state: RegistrationState)

    suspend fun clear()
}

@Singleton
class EncryptedRegistrationStateStore @Inject constructor(
    private val context: Context,
    private val gson: Gson,
) : RegistrationStateStore {
    private val encryptedPreferences = openEncryptedPreferences()

    init {
        encryptedPreferences.edit()
            .remove(LEGACY_NAMESPACES_STATE_KEY)
            .apply()
    }

    private val stateFlow = MutableStateFlow(readStateSafely())

    override fun observeState(): Flow<RegistrationState?> = stateFlow.asStateFlow()

    override suspend fun save(state: RegistrationState) {
        withContext(Dispatchers.IO) {
            encryptedPreferences.edit()
                .putString(AppConstants.ENCRYPTED_PREFS_STATE_KEY, gson.toJson(state))
                .apply()
            stateFlow.value = state
        }
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) {
            encryptedPreferences.edit()
                .remove(AppConstants.ENCRYPTED_PREFS_STATE_KEY)
                .apply()
            stateFlow.value = null
        }
    }

    private fun readStateSafely(): RegistrationState? = try {
        readState()
    } catch (exception: Exception) {
        if (!exception.isSecurePrefsCorruption()) {
            throw exception
        }

        Log.w(
            SECURE_STORAGE_LOG_TAG,
            "Secure registration state could not be decrypted after app restore or reinstall. Clearing local secure state.",
            exception,
        )
        context.deleteSharedPreferences(AppConstants.ENCRYPTED_PREFS_NAME)
        recreateEncryptedPreferencesFile()
        null
    }

    private fun readState(): RegistrationState? {
        val rawState = encryptedPreferences.getString(AppConstants.ENCRYPTED_PREFS_STATE_KEY, null)
            ?: return null
        return gson.fromJson(rawState, RegistrationState::class.java)
    }

    private fun openEncryptedPreferences() = try {
        createEncryptedPreferences()
    } catch (exception: Exception) {
        if (!exception.isSecurePrefsCorruption()) {
            throw exception
        }

        Log.w(
            SECURE_STORAGE_LOG_TAG,
            "Secure registration preferences failed to open. Clearing restored encrypted preferences and recreating storage.",
            exception,
        )
        context.deleteSharedPreferences(AppConstants.ENCRYPTED_PREFS_NAME)
        createEncryptedPreferences()
    }

    private fun createEncryptedPreferences() = EncryptedSharedPreferences.create(
        context,
        AppConstants.ENCRYPTED_PREFS_NAME,
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )

    private fun recreateEncryptedPreferencesFile() {
        createEncryptedPreferences().edit().clear().apply()
    }

    private fun Exception.isSecurePrefsCorruption(): Boolean =
        this is GeneralSecurityException ||
            this is IOException ||
            cause?.let { nested ->
                nested is GeneralSecurityException ||
                    nested is IOException
            } == true

    private companion object {
        const val LEGACY_NAMESPACES_STATE_KEY = "namespaces_state"
        const val SECURE_STORAGE_LOG_TAG = "ZeroIntrusionStorage"
    }
}