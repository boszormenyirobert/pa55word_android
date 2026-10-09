package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.domain.qr.NamespaceSyncQrPayload
import com.zerointrusion.domain.registration.RegistrationValidator
import java.lang.reflect.Proxy
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NamespaceRegistrationFromQrFlowTest {
    private val registration = MobileNamespaceRegistration(
        "Y-account", "z9NrbrbcNK", "f65b0a7c93", "dev", "http://192.168.178.20:3001", "Y-dek",
    )
    private val payload = NamespaceSyncQrPayload(
        "WNKXHfaKhG", "desktop-key", "PC", "3269e3c126", "http://192.168.178.20:3001",
    )

    @Test
    fun `cold cache refresh can resolve mobile-only registration`() = runBlocking {
        val store = MemoryStore()
        var refreshes = 0
        val flow = flow(store) {
            refreshes++
            store.replaceMobileRegistrations(listOf(registration))
        }
        val prepared = flow.prepareSync(payload, registration.mobileDeviceId)
        assertEquals(1, refreshes)
        assertEquals("Y-account", prepared.targetNamespaceAccountId)
        assertEquals("Y-dek", prepared.targetEncDek)
        assertEquals(payload.namespaceUrl, prepared.namespaceEntry.namespaceUrl)
        assertEquals(payload.desktopDeviceId, prepared.scannedDesktopDeviceId)
        assertTrue(store.currentEntries().isEmpty())
    }

    @Test
    fun `persisted registration is refreshed before selecting account and dek`() = runBlocking {
        val store = MemoryStore(listOf(registration))
        val refreshed = registration.copy(accountId = "fresh-account", encDek = "fresh-dek")
        var refreshes = 0
        val prepared = flow(store) {
            refreshes++
            store.replaceMobileRegistrations(listOf(refreshed, registration))
        }
            .prepareSync(payload, registration.mobileDeviceId)
        assertEquals(1, refreshes)
        assertEquals("fresh-account", prepared.targetNamespaceAccountId)
        assertEquals("fresh-dek", prepared.targetEncDek)
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `removed cached registration cannot prepare desktop registration`() = runBlocking {
        val store = MemoryStore(listOf(registration))
        flow(store) { store.replaceMobileRegistrations(emptyList()) }
            .prepareSync(payload, registration.mobileDeviceId)
        Unit
    }

    @Test
    fun `failed refresh cannot use an existing cached registration`() = runBlocking {
        val store = MemoryStore(listOf(registration))
        val failure = RegistrationFailure.Network("Server unavailable")
        try {
            flow(store) { throw failure }.prepareSync(payload, registration.mobileDeviceId)
            error("Expected network failure")
        } catch (actual: RegistrationFailure.Network) {
            assertEquals(failure, actual)
        }
    }

    @Test
    fun `refresh error is not replaced with namespace-not-found`() = runBlocking {
        val failure = RegistrationFailure.Server("Account response failed")
        try {
            flow(MemoryStore()) { throw failure }.prepareSync(payload, registration.mobileDeviceId)
            error("Expected the server failure")
        } catch (actual: RegistrationFailure.Server) {
            assertEquals(failure, actual)
        }
    }

    private fun flow(store: MemoryStore, refresh: suspend () -> Unit): NamespaceRegistrationFromQrFlow {
        val repository = object : ConnectedDevicesRepository {
            override fun observeConnectedDevices(): Flow<List<ConnectedDeviceGroup>> = flowOf(emptyList())
            override suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup> {
                refresh()
                return emptyList()
            }
            override suspend fun loadRemovableDevices(): List<RemovableAccountDevice> = error("Not used")
            override suspend fun deleteDevice(device: RemovableAccountDevice) = error("Not used")
        }
        return NamespaceRegistrationFromQrFlow(
            registrationApi = unused(RegistrationApi::class.java),
            keyStoreService = unused(KeyStoreService::class.java),
            connectedDevicesRepository = repository,
            connectedDevicesLocalStore = store,
            gson = Gson(),
            validator = RegistrationValidator(),
        )
    }

    private fun <T : Any> unused(type: Class<T>): T = requireNotNull(type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            error("Unexpected call: ${method.name}")
        },
    ))

    private class MemoryStore(
        private var registrations: List<MobileNamespaceRegistration> = emptyList(),
    ) : ConnectedDevicesLocalStore {
        override fun observeEntries(): Flow<List<LocalConnectedDeviceEntry>> = flowOf(emptyList())
        override fun currentEntries(): List<LocalConnectedDeviceEntry> = emptyList()
        override suspend fun replaceEntries(entries: List<LocalConnectedDeviceEntry>) = error("Not used")
        override suspend fun upsertEntry(entry: LocalConnectedDeviceEntry) = error("Not used")
        override fun currentMobileRegistrations(): List<MobileNamespaceRegistration> = registrations
        override fun observeMobileRegistrations(): Flow<List<MobileNamespaceRegistration>> = flowOf(registrations)
        override suspend fun replaceMobileRegistrations(registrations: List<MobileNamespaceRegistration>) {
            this.registrations = registrations
        }
        override suspend fun updateMobileRegistrations(
            transform: (List<MobileNamespaceRegistration>) -> List<MobileNamespaceRegistration>,
        ) {
            registrations = transform(registrations)
        }
    }
}
