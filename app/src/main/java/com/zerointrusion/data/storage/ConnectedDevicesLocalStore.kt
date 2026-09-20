package com.zerointrusion.data.storage

import android.content.Context
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zerointrusion.core.AppConstants
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceNamespace
import com.zerointrusion.domain.namespaces.NamespaceEntry
import java.io.IOException
import java.security.GeneralSecurityException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class LocalConnectedNamespace(
    val accountId: String,
    val desktopDeviceId: String,
    val namespaceId: String,
    val namespace: String,
    val url: String,
)

data class LocalConnectedDeviceEntry(
    val url: String,
    val accountId: String,
    val desktopDeviceId: String,
    val namespaces: List<LocalConnectedNamespace>,
)

interface ConnectedDevicesLocalStore {
    fun observeEntries(): Flow<List<LocalConnectedDeviceEntry>>

    fun currentEntries(): List<LocalConnectedDeviceEntry>

    suspend fun replaceEntries(entries: List<LocalConnectedDeviceEntry>)

    suspend fun upsertEntry(entry: LocalConnectedDeviceEntry)
}

@Singleton
class EncryptedConnectedDevicesLocalStore @Inject constructor(
    private val context: Context,
    private val gson: Gson,
) : ConnectedDevicesLocalStore {
    private val encryptedPreferences = openEncryptedPreferences()
    private val stateFlow = MutableStateFlow(readEntriesSafely())

    override fun observeEntries(): Flow<List<LocalConnectedDeviceEntry>> = stateFlow.asStateFlow()

    override fun currentEntries(): List<LocalConnectedDeviceEntry> = stateFlow.value

    override suspend fun replaceEntries(entries: List<LocalConnectedDeviceEntry>) {
        persist(normalizeEntries(entries))
    }

    override suspend fun upsertEntry(entry: LocalConnectedDeviceEntry) {
        val existingEntries = stateFlow.value
        val mergedEntries = normalizeEntries(existingEntries + entry)
        persist(mergedEntries)
    }

    private suspend fun persist(entries: List<LocalConnectedDeviceEntry>) {
        withContext(Dispatchers.IO) {
            encryptedPreferences.edit()
                .putString(AppConstants.ENCRYPTED_PREFS_CONNECTED_DEVICES_KEY, gson.toJson(entries))
                .apply()
            stateFlow.value = entries
        }
    }

    private fun readEntriesSafely(): List<LocalConnectedDeviceEntry> = try {
        readEntries()
    } catch (exception: Exception) {
        if (!exception.isSecurePrefsCorruption()) {
            throw exception
        }

        Log.w(
            CONNECTED_DEVICES_STORAGE_LOG_TAG,
            "Secure connected-devices state could not be decrypted after app restore or reinstall. Clearing local connected-devices state.",
            exception,
        )
        context.deleteSharedPreferences(AppConstants.ENCRYPTED_PREFS_NAME)
        recreateEncryptedPreferencesFile()
        emptyList()
    }

    private fun readEntries(): List<LocalConnectedDeviceEntry> {
        val rawState = encryptedPreferences.getString(AppConstants.ENCRYPTED_PREFS_CONNECTED_DEVICES_KEY, null)
            ?: return emptyList()
        val type = object : TypeToken<List<LocalConnectedDeviceEntry>>() {}.type
        return gson.fromJson<List<LocalConnectedDeviceEntry>>(rawState, type).orEmpty()
    }

    private fun openEncryptedPreferences() = try {
        createEncryptedPreferences()
    } catch (exception: Exception) {
        if (!exception.isSecurePrefsCorruption()) {
            throw exception
        }

        Log.w(
            CONNECTED_DEVICES_STORAGE_LOG_TAG,
            "Secure connected-devices preferences failed to open. Clearing restored encrypted preferences and recreating storage.",
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
        const val CONNECTED_DEVICES_STORAGE_LOG_TAG = "ZeroIntrusionStorage"
    }
}

internal fun List<NamespaceEntry>.toLocalConnectedDeviceEntries(): List<LocalConnectedDeviceEntry> = normalizeEntries(
    flatMap { namespaceEntry ->
        val normalizedUrl = namespaceEntry.namespaceUrl.trim().trimEnd('/')
        val normalizedAccountId = namespaceEntry.accountId.trim()
        val normalizedDesktopDeviceId = namespaceEntry.desktopDeviceId.trim()
        val normalizedNamespaceId = namespaceEntry.namespaceId.trim()
        val normalizedNamespaceName = namespaceEntry.namespaceName.trim()
        if (
            normalizedUrl.isEmpty() ||
            normalizedAccountId.isEmpty() ||
            normalizedDesktopDeviceId.isEmpty() ||
            normalizedNamespaceId.isEmpty() ||
            normalizedNamespaceName.isEmpty()
        ) {
            emptyList()
        } else {
            listOf(
                LocalConnectedDeviceEntry(
                    url = normalizedUrl,
                    accountId = normalizedAccountId,
                    desktopDeviceId = normalizedDesktopDeviceId,
                    namespaces = listOf(
                        LocalConnectedNamespace(
                            accountId = normalizedAccountId,
                            desktopDeviceId = normalizedDesktopDeviceId,
                            namespaceId = normalizedNamespaceId,
                            namespace = normalizedNamespaceName,
                            url = normalizedUrl,
                        ),
                    ),
                ),
            )
        }
    },
)

internal fun List<LocalConnectedDeviceEntry>.toConnectedDeviceGroups(): List<ConnectedDeviceGroup> = groupBy { entry ->
    entry.accountId.trim().lowercase() + "|" + entry.desktopDeviceId.trim().lowercase()
}.values.mapNotNull { entries ->
    val representative = entries.firstOrNull() ?: return@mapNotNull null
    val namespaces = entries.flatMap { entry ->
        entry.namespaces.mapNotNull { namespace ->
            val namespaceId = namespace.namespaceId.trim()
            val namespaceName = namespace.namespace.trim()
            val namespaceUrl = namespace.url.trim().trimEnd('/')
            if (namespaceUrl.isEmpty() || namespaceId.isEmpty() || namespaceName.isEmpty()) {
                null
            } else {
                ConnectedDeviceNamespace(
                    accountId = namespace.accountId,
                    desktopDeviceId = namespace.desktopDeviceId,
                    namespaceId = namespaceId,
                    namespaceName = namespaceName,
                    namespaceUrl = namespaceUrl,
                    deviceId = representative.desktopDeviceId,
                )
            }
        }
    }.distinctBy { namespace -> namespace.namespaceId.lowercase() + "|" + namespace.namespaceUrl.lowercase() }
        .sortedWith(compareBy({ it.namespaceUrl.lowercase() }, { it.namespaceId.lowercase() }))

    ConnectedDeviceGroup(
        accountId = representative.accountId,
        deviceId = representative.desktopDeviceId,
        deviceName = null,
        namespaces = namespaces,
    )
}.sortedWith(compareBy({ it.accountId.lowercase() }, { it.deviceId.lowercase() }))

internal fun List<LocalConnectedDeviceEntry>.toNamespaceEntries(): List<NamespaceEntry> = flatMap { entry ->
    entry.namespaces.mapNotNull { namespace ->
        val accountId = namespace.accountId.trim()
        val desktopDeviceId = namespace.desktopDeviceId.trim()
        val namespaceId = namespace.namespaceId.trim()
        val namespaceName = namespace.namespace.trim()
        val namespaceUrl = namespace.url.trim().trimEnd('/')

        if (
            accountId.isEmpty() ||
            desktopDeviceId.isEmpty() ||
            namespaceId.isEmpty() ||
            namespaceName.isEmpty() ||
            namespaceUrl.isEmpty()
        ) {
            null
        } else {
            NamespaceEntry(
                namespaceName = namespaceName,
                namespaceId = namespaceId,
                namespaceUrl = namespaceUrl,
                accountId = accountId,
                desktopDeviceId = desktopDeviceId,
                deviceName = null,
                publicKey = null,
            )
        }
    }
}.distinctBy { namespace ->
    namespace.accountId.lowercase() + "|" +
        namespace.desktopDeviceId.lowercase() + "|" +
        namespace.namespaceId.lowercase() + "|" +
        namespace.namespaceUrl.lowercase()
}.sortedWith(
    compareBy(
        { it.desktopDeviceId.lowercase() },
        { it.namespaceName.lowercase() },
        { it.namespaceUrl.lowercase() },
    ),
)

private fun normalizeEntries(entries: List<LocalConnectedDeviceEntry>): List<LocalConnectedDeviceEntry> = entries
    .flatMap { entry ->
        val normalizedUrl = entry.url.trim().trimEnd('/')
        val normalizedAccountId = entry.accountId.trim()
        val normalizedDesktopDeviceId = entry.desktopDeviceId.trim()
        val normalizedNamespaces = entry.namespaces
            .mapNotNull { namespace ->
                val normalizedNamespaceAccountId = namespace.accountId.trim()
                val normalizedNamespaceDesktopDeviceId = namespace.desktopDeviceId.trim()
                val normalizedNamespaceId = namespace.namespaceId.trim()
                val normalizedNamespaceName = namespace.namespace.trim()
                val normalizedNamespaceUrl = namespace.url.trim().trimEnd('/')
                if (
                    normalizedNamespaceAccountId.isEmpty() ||
                    normalizedNamespaceDesktopDeviceId.isEmpty() ||
                    normalizedNamespaceId.isEmpty() ||
                    normalizedNamespaceName.isEmpty() ||
                    normalizedNamespaceUrl.isEmpty()
                ) {
                    null
                } else {
                    LocalConnectedNamespace(
                        accountId = normalizedNamespaceAccountId,
                        desktopDeviceId = normalizedNamespaceDesktopDeviceId,
                        namespaceId = normalizedNamespaceId,
                        namespace = normalizedNamespaceName,
                        url = normalizedNamespaceUrl,
                    )
                }
            }
            .distinctBy { namespace -> namespace.namespaceId.lowercase() + "|" + namespace.url.lowercase() }
            .sortedWith(compareBy({ it.url.lowercase() }, { it.namespaceId.lowercase() }))

        if (
            normalizedUrl.isEmpty() ||
            normalizedAccountId.isEmpty() ||
            normalizedDesktopDeviceId.isEmpty() ||
            normalizedNamespaces.isEmpty()
        ) {
            emptyList()
        } else {
            listOf(
                LocalConnectedDeviceEntry(
                    url = normalizedUrl,
                    accountId = normalizedAccountId,
                    desktopDeviceId = normalizedDesktopDeviceId,
                    namespaces = normalizedNamespaces,
                ),
            )
        }
    }
    .groupBy { entry ->
        entry.url.lowercase() + "|" + entry.accountId.lowercase() + "|" + entry.desktopDeviceId.lowercase()
    }
    .values
    .mapNotNull { groupedEntries ->
        val representative = groupedEntries.firstOrNull() ?: return@mapNotNull null
        val namespaces = groupedEntries
            .flatMap { it.namespaces }
            .distinctBy { namespace -> namespace.namespaceId.lowercase() + "|" + namespace.url.lowercase() }
            .sortedWith(compareBy({ it.url.lowercase() }, { it.namespaceId.lowercase() }))

        representative.copy(namespaces = namespaces)
    }
    .sortedWith(compareBy({ it.url.lowercase() }, { it.accountId.lowercase() }, { it.desktopDeviceId.lowercase() }))