package com.zerointrusion.data.storage

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.recovery.RecoveryPendingState
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

interface RecoveryPendingStateStore {
    fun observe(): Flow<RecoveryPendingState?>
    suspend fun save(state: RecoveryPendingState)
    suspend fun clear()
}

@Singleton
class EncryptedRecoveryPendingStateStore @Inject constructor(
    context: Context,
    private val gson: Gson,
) : RecoveryPendingStateStore {
    private val preferences = EncryptedSharedPreferences.create(
        context,
        AppConstants.ENCRYPTED_PREFS_NAME,
        MasterKey.Builder(context).setKeyScheme(MasterKey.KeyScheme.AES256_GCM).build(),
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM,
    )
    private val state = MutableStateFlow(read())

    override fun observe(): Flow<RecoveryPendingState?> = state.asStateFlow()

    override suspend fun save(state: RecoveryPendingState) = withContext(Dispatchers.IO) {
        preferences.edit().putString(AppConstants.ENCRYPTED_PREFS_RECOVERY_PENDING_KEY, gson.toJson(state)).apply()
        this@EncryptedRecoveryPendingStateStore.state.value = state
    }

    override suspend fun clear() = withContext(Dispatchers.IO) {
        preferences.edit().remove(AppConstants.ENCRYPTED_PREFS_RECOVERY_PENDING_KEY).apply()
        state.value = null
    }

    private fun read(): RecoveryPendingState? = preferences
        .readStateWithoutPhone(AppConstants.ENCRYPTED_PREFS_RECOVERY_PENDING_KEY, gson)
        ?.let { gson.fromJson(it, RecoveryPendingState::class.java) }
}