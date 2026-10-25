package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.Gson
import com.zerointrusion.data.network.AccountApi
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.LocalConnectedNamespace
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.domain.registration.RegistrationState
import java.lang.reflect.Proxy
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.startCoroutineUninterceptedOrReturn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import retrofit2.Response

class AccountDetailsSnapshotFetcherTest {
    @Test
    fun `deleting current phone on default server preserves surviving registration`() = runBlocking {
        val fixture = Fixture(home = "removed")
        fixture.repository().deleteDevice(
            RemovableAccountDevice("removed", "https://removed.example", targetMobileDeviceId = "phone"),
        )
        assertEquals("active", fixture.state.value?.accountId)
        assertEquals("active-dek", fixture.state.value?.encDek)
        assertEquals(listOf("active"), fixture.store.registrations.map { it.accountId })
    }

    @Test
    fun `deleting last phone registration clears local registration`() = runBlocking {
        val fixture = Fixture(home = "removed", includeActive = false)
        fixture.repository().deleteDevice(
            RemovableAccountDevice("removed", "https://removed.example", targetMobileDeviceId = "phone"),
        )
        assertNull(fixture.state.value)
        assertEquals(emptyList<MobileNamespaceRegistration>(), fixture.store.registrations)
    }

    @Test
    fun `removed secondary account does not break surviving account list`() = runBlocking {
        val fixture = Fixture(home = "active")
        val snapshot = fixture.fetcher().fetchSnapshot()
        assertEquals(listOf("active"), snapshot.mobileRegistrations.map { it.accountId })
        assertEquals(listOf("active"), fixture.store.registrations.map { it.accountId })
        assertEquals(listOf("active"), fixture.store.currentEntries().map { it.accountId })
        assertEquals("active", fixture.state.value?.accountId)
        fixture.fetcher().fetchSnapshot()
        assertEquals(1, fixture.requests.count { it == "removed" })
    }

    @Test
    fun `removed gateway switches to verified surviving account with its own dek`() = runBlocking {
        val fixture = Fixture(home = "removed")
        fixture.fetcher().fetchSnapshot()
        assertEquals("active", fixture.state.value?.accountId)
        assertEquals("active-dek", fixture.state.value?.encDek)
        assertEquals("https://active.example", fixture.state.value?.namespaceUrl)
    }

    @Test
    fun `last removed account clears registration without a list error`() = runBlocking {
        val fixture = Fixture(home = "removed", includeActive = false)
        assertEquals(emptyList<MobileNamespaceRegistration>(), fixture.fetcher().fetchSnapshot().mobileRegistrations)
        assertNull(fixture.state.value)
        assertEquals(emptyList<MobileNamespaceRegistration>(), fixture.store.registrations)
    }

    @Test
    fun `rejected secondary signature preserves registrations and reachable data`() = runBlocking {
        val fixture = Fixture(home = "active", errorCode = "e_40101_1")
        val snapshot = fixture.fetcher().fetchSnapshot()
        assertEquals(listOf("active"), snapshot.mobileRegistrations.map { it.accountId })
        assertEquals(2, fixture.store.registrations.size)
        assertEquals("active", fixture.state.value?.accountId)
    }

    @Test
    fun `unavailable secondary server does not hide reachable server data`() = runBlocking {
        val fixture = Fixture(home = "active", unavailableAccount = "removed")

        val snapshot = fixture.fetcher().fetchSnapshot()

        assertEquals(listOf("active"), snapshot.mobileRegistrations.map { it.accountId })
        assertEquals(listOf("active", "removed"), fixture.store.registrations.map { it.accountId })
        assertEquals(listOf("active", "removed"), fixture.requests)
    }

    @Test
    fun `timed out first server does not stop checking the next server`() = runBlocking {
        val fixture = Fixture(home = "active", timeoutAccount = "active", removedAvailable = true)

        val snapshot = fixture.fetcher().fetchSnapshot()

        assertEquals(listOf("active", "removed"), fixture.requests)
        assertEquals(listOf("removed"), snapshot.mobileRegistrations.map { it.accountId })
        val availability = fixture.holder.observeDatabaseAvailability().first().associate { it.accountId to it.isAvailable }
        assertEquals(false, availability["active"])
        assertEquals(true, availability["removed"])
    }

    @Test
    fun `account request cancellation stops the refresh`() = runBlocking {
        val fixture = Fixture(home = "active", cancelAccount = "active")
        try {
            fixture.fetcher().fetchSnapshot()
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(listOf("active"), fixture.requests)
        }
    }

    private class Fixture(
        home: String,
        includeActive: Boolean = true,
        private val errorCode: String = "e_40101_2",
        private val unavailableAccount: String? = null,
        private val timeoutAccount: String? = null,
        private val cancelAccount: String? = null,
        private val removedAvailable: Boolean = false,
    ) {
        val state = MutableStateFlow<RegistrationState?>(
            RegistrationState(
                accountId = home, mobileDeviceId = "phone", deviceName = "Phone",
                namespaceName = "dev", namespaceUrl = "https://$home.example",
                namespaceId = "1234567890", email = "a@example.com", phone = "+36123456789",
                encDek = "$home-dek",
            ),
        )
        val store = Store(
            (if (includeActive) listOf("active", "removed") else listOf("removed")).map {
                MobileNamespaceRegistration(it, "phone", "1234567890", "dev", "https://$it.example", "$it-dek")
            },
        )
        val requests = mutableListOf<String>()
        val holder = InMemoryAccountDetailsStateHolder()

        private val api = proxy(AccountApi::class.java) { method, args ->
                if (method == "deleteAccountDevice") {
                    return@proxy Response.success("""{"success":true}""".toResponseBody())
                }
                check(method == "getAccountDetails")
                val account = if (args[0].toString().contains("active.example")) "active" else "removed"
                requests += account
                if (account == timeoutAccount) {
                    return@proxy suspendUntilCancelled(args.last())
                }
                if (account == cancelAccount) {
                    throw CancellationException("Cancelled")
                }
                if (account == unavailableAccount) {
                    throw IllegalStateException("$account server is unavailable")
                }
                if (account == "removed" && !removedAvailable) {
                    Response.error<okhttp3.ResponseBody>(401, """{"internalCode":"$errorCode"}""".toResponseBody())
                } else {
                    Response.success(
                        """{"success":true,"accounts":[{"accountId":"$account",
                        "devices":[{"mobileDeviceId":"phone","namespaces":[{"namespaceId":"1234567890",
                        "namespace":"dev","url":"https://$account.example"}]}],
                        "dek":[{"mobileDeviceId":"phone","encDek":"$account-dek"}]}]}""".toResponseBody(),
                    )
                }
            }
        private val stateStore = object : RegistrationStateStore {
                override fun observeState() = state
                override suspend fun save(state: RegistrationState) { this@Fixture.state.value = state }
                override suspend fun clear() { state.value = null }
            }
        private val signatureService = proxy(SignatureService::class.java) { method, _ ->
            check(method == "signJsonString")
            "signature"
        }

        fun repository() = ConnectedDevicesRepositoryImpl(
            fetcher(), holder, store, stateStore, api, signatureService, Gson(),
        )

        fun fetcher(): AccountDetailsSnapshotFetcher = AccountDetailsSnapshotFetcher(
                api, stateStore, holder, store,
                proxy(DekService::class.java) { method, _ ->
                    check(method == "decryptDek")
                    ByteArray(32)
                },
                signatureService,
                Gson(),
            )
    }

    private class Store(var registrations: List<MobileNamespaceRegistration>) : ConnectedDevicesLocalStore {
        private val entries = MutableStateFlow(
            registrations.map {
                LocalConnectedDeviceEntry(
                    it.namespaceUrl, it.accountId, "desktop",
                    listOf(LocalConnectedNamespace(
                        it.accountId, "desktop", it.namespaceId, it.namespaceName, it.namespaceUrl,
                    )),
                )
            },
        )
        override fun observeEntries() = entries
        override fun currentEntries() = entries.value
        override suspend fun replaceEntries(entries: List<LocalConnectedDeviceEntry>) { this.entries.value = entries }
        override suspend fun upsertEntry(entry: LocalConnectedDeviceEntry) { entries.value += entry }
        override fun currentMobileRegistrations() = registrations
        override fun observeMobileRegistrations() = kotlinx.coroutines.flow.flowOf(registrations)
        override suspend fun replaceMobileRegistrations(registrations: List<MobileNamespaceRegistration>) {
            this.registrations = registrations
        }
        override suspend fun updateMobileRegistrations(
            transform: (List<MobileNamespaceRegistration>) -> List<MobileNamespaceRegistration>,
        ) {
            registrations = transform(registrations)
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        private fun suspendUntilCancelled(continuation: Any?): Any {
            val operation: suspend () -> Any = { awaitCancellation() }
            return operation.startCoroutineUninterceptedOrReturn(continuation as Continuation<Any>)
                ?: error("Expected suspended operation")
        }

        private fun <T : Any> proxy(type: Class<T>, handler: (String, Array<out Any?>) -> Any): T =
            requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                handler(method.name, args ?: emptyArray())
            }))
    }
}
