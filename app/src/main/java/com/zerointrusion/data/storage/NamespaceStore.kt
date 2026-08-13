package com.zerointrusion.data.storage

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.NamespaceEntry
import java.io.IOException
import java.security.GeneralSecurityException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

interface NamespaceStore {
    fun observeNamespaces(): Flow<List<NamespaceEntry>>

    suspend fun saveNamespaces(namespaces: List<NamespaceEntry>)
}

@Singleton
class EncryptedNamespaceStore @Inject constructor(
    private val context: Context,
    private val gson: Gson,
) : NamespaceStore {
    private val encryptedPreferences = openEncryptedPreferences()

    private val stateFlow = MutableStateFlow(readNamespacesSafely())

    override fun observeNamespaces(): Flow<List<NamespaceEntry>> = stateFlow.asStateFlow()

    override suspend fun saveNamespaces(namespaces: List<NamespaceEntry>) {
        withContext(Dispatchers.IO) {
            encryptedPreferences.edit()
                .putString(AppConstants.ENCRYPTED_PREFS_NAMESPACES_KEY, gson.toJson(namespaces))
                .apply()
            stateFlow.value = namespaces
        }
    }

    private fun readNamespacesSafely(): List<NamespaceEntry> = try {
        readNamespaces()
    } catch (exception: Exception) {
        if (!exception.isSecurePrefsCorruption()) {
            throw exception
        }

        Log.w(
            SECURE_STORAGE_LOG_TAG,
            "Secure namespace state could not be decrypted after app restore or reinstall. Clearing local secure state.",
            exception,
        )
        context.deleteSharedPreferences(AppConstants.ENCRYPTED_PREFS_NAME)
        recreateEncryptedPreferencesFile()
        emptyList()
    }

    private fun readNamespaces(): List<NamespaceEntry> {
        val rawNamespaces = encryptedPreferences.getString(AppConstants.ENCRYPTED_PREFS_NAMESPACES_KEY, null)
            ?: return emptyList()
        val listType = object : TypeToken<List<NamespaceEntry>>() {}.type
        return gson.fromJson<List<NamespaceEntry>>(rawNamespaces, listType).orEmpty()
    }

    private fun openEncryptedPreferences() = try {
        createEncryptedPreferences()
    } catch (exception: Exception) {
        if (!exception.isSecurePrefsCorruption()) {
            throw exception
        }

        Log.w(
            SECURE_STORAGE_LOG_TAG,
            "Secure namespace preferences failed to open. Clearing restored encrypted preferences and recreating storage.",
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
        const val SECURE_STORAGE_LOG_TAG = "ZeroIntrusionStorage"
    }
}