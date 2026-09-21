package com.zerointrusion.data.qr

import android.util.Log
import com.zerointrusion.data.repository.registration.FcmTokenSynchronizer
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.LocalConnectedNamespace
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.qr.AdditionalMobileNamespacesQrPayload
import com.zerointrusion.domain.qr.QrNamespaceRegistrationResult
import com.zerointrusion.domain.registration.RegistrationState
import kotlinx.coroutines.flow.first

internal class AdditionalMobileNamespacesFromQrFlow(
    private val stateStore: RegistrationStateStore,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
    private val fcmTokenSynchronizer: FcmTokenSynchronizer,
    private val connectedDevicesRepository: ConnectedDevicesRepository,
) {
    suspend fun register(
        registrationState: RegistrationState,
        payload: AdditionalMobileNamespacesQrPayload,
    ): QrNamespaceRegistrationResult {
        if (!canImportAdditionalMobileNamespaces(registrationState)) {
            throw RegistrationFailure.Validation(
                "Additional namespace import is only available while adding a mobile device to an existing account.",
            )
        }
        val completedNamespaceKeys = registrationState.completedAdditionalNamespaceKeys.orEmpty().toMutableSet()
        val registeredEntries = mutableListOf<NamespaceEntry>()
        val targets = payload.namespaces
            .filterNot { it.url.normalizedUrl() == registrationState.namespaceUrl.normalizedUrl() }

        targets.forEach { namespace ->
            val namespaceKey = namespace.registrationKey()
            if (namespaceKey in completedNamespaceKeys) {
                Log.d(QR_LOG_TAG, "Additional mobile namespace already registered. key=$namespaceKey")
                return@forEach
            }
            val accountId = namespace.accountId.trim()
            connectedDevicesLocalStore.upsertEntry(
                LocalConnectedDeviceEntry(
                    url = namespace.url,
                    accountId = accountId,
                    desktopDeviceId = namespace.desktopDeviceId,
                    namespaces = listOf(
                        LocalConnectedNamespace(
                            accountId = accountId,
                            desktopDeviceId = namespace.desktopDeviceId,
                            namespaceId = namespace.namespaceId,
                            namespace = namespace.namespace,
                            url = namespace.url,
                            root = namespace.root,
                        ),
                    ),
                ),
            )
            registeredEntries += NamespaceEntry(
                namespaceName = namespace.namespace,
                namespaceId = namespace.namespaceId,
                namespaceUrl = namespace.url,
                accountId = accountId,
                desktopDeviceId = namespace.desktopDeviceId,
                root = namespace.root,
            )
            completedNamespaceKeys += namespaceKey
            val progressState = (stateStore.observeState().first() ?: registrationState).copy(
                pendingAdditionalNamespacesScan = true,
                completedAdditionalNamespaceKeys = completedNamespaceKeys.toList(),
            )
            stateStore.save(progressState)
        }

        val currentState = stateStore.observeState().first() ?: registrationState
        val finalState = currentState.copy(
            pendingAdditionalNamespacesScan = false,
            completedAdditionalNamespaceKeys = completedNamespaceKeys.toList(),
        )
        stateStore.save(finalState)
        registeredEntries.forEach { registered ->
            fcmTokenSynchronizer.syncNamespace(
                namespaceEntry = registered,
                registrationState = finalState,
            )
        }
        runCatching { connectedDevicesRepository.fetchConnectedDevices() }
            .onFailure { Log.w(QR_LOG_TAG, "Additional namespace account refresh failed.", it) }

        return QrNamespaceRegistrationResult(
            message = if (targets.isEmpty()) {
                "No additional namespace servers were provided."
            } else {
                "Additional namespace servers connected: ${targets.size}"
            },
        )
    }

    private companion object {
        const val QR_LOG_TAG = "ZeroIntrusionQr"
    }
}

private fun String.normalizedUrl(): String = trim().trimEnd('/').lowercase()

internal fun canImportAdditionalMobileNamespaces(registrationState: RegistrationState): Boolean =
    registrationState.pendingAdditionalNamespacesScan

private fun com.zerointrusion.domain.qr.AdditionalMobileNamespace.registrationKey(): String =
    desktopDeviceId.trim().lowercase() + "|" + namespaceId.trim().lowercase() + "|" + url.normalizedUrl()