package com.zerointrusion.data.storage

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.ServicePlan
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

interface RegistrationStateStore {
    fun observeState(): Flow<RegistrationState?>

    suspend fun save(state: RegistrationState)

    suspend fun setServicePlan(servicePlan: ServicePlan) {
        val state = observeState().first() ?: return
        save(state.copy(servicePlan = servicePlan))
    }

    suspend fun clear()
}

@Singleton
class EncryptedRegistrationStateStore
@Inject
constructor(private val context: Context, private val gson: Gson) : RegistrationStateStore {
    private val encryptedPreferences = openEncryptedPreferences()

    init {
        encryptedPreferences.edit().remove(LEGACY_NAMESPACES_STATE_KEY).apply()
    }

    private val stateFlow = MutableStateFlow(readStateSafely())

    override fun observeState(): Flow<RegistrationState?> = stateFlow.asStateFlow()

    override suspend fun save(state: RegistrationState) = updateState { current ->
        val servicePlan =
            if (
                current?.accountId == state.accountId &&
                    current.mobileDeviceId == state.mobileDeviceId
            ) {
                current.servicePlan
            } else {
                state.servicePlan
            }
        state.copy(servicePlan = servicePlan)
    }

    override suspend fun setServicePlan(servicePlan: ServicePlan) = updateState { current ->
        current?.copy(servicePlan = servicePlan)
    }

    override suspend fun clear() = updateState { null }

    private suspend fun updateState(transform: (RegistrationState?) -> RegistrationState?) {
        withContext(Dispatchers.IO) {
            synchronized(stateFlow) {
                val state = transform(stateFlow.value)
                val editor = encryptedPreferences.edit()
                if (state == null) {
                    editor.remove(AppConstants.ENCRYPTED_PREFS_STATE_KEY)
                } else {
                    editor.putString(AppConstants.ENCRYPTED_PREFS_STATE_KEY, gson.toJson(state))
                }
                editor.apply()
                stateFlow.value = state
            }
        }
    }

    private fun readStateSafely(): RegistrationState? =
        try {
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
        val rawState =
            encryptedPreferences.readStateWithoutPhone(AppConstants.ENCRYPTED_PREFS_STATE_KEY, gson)
                ?: return null
        return gson.fromJson(rawState, RegistrationState::class.java)
    }

    private fun openEncryptedPreferences() =
        try {
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

    private fun createEncryptedPreferences() =
        EncryptedSharedPreferences.create(
            context,
            AppConstants.ENCRYPTED_PREFS_NAME,
            MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
        )

    private fun recreateEncryptedPreferencesFile() {
        createEncryptedPreferences().edit().clear().apply()
    }

    private fun Exception.isSecurePrefsCorruption(): Boolean =
        this is GeneralSecurityException ||
            this is IOException ||
            cause?.let { nested -> nested is GeneralSecurityException || nested is IOException } ==
                true

    private companion object {
        const val LEGACY_NAMESPACES_STATE_KEY = "namespaces_state"
        const val SECURE_STORAGE_LOG_TAG = "ZeroIntrusionStorage"
    }
}

internal fun SharedPreferences.readStateWithoutPhone(key: String, gson: Gson): String? {
    val raw = getString(key, null) ?: return null
    val json = gson.fromJson(raw, JsonObject::class.java) ?: return raw
    if (!json.has("phone")) return raw
    json.remove("phone")
    val migrated = gson.toJson(json)
    edit().putString(key, migrated).apply()
    return migrated
}
