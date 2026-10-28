package com.zerointrusion.data.repository.namespaces

import com.google.gson.Gson
import com.zerointrusion.core.AppConstants
import com.zerointrusion.data.network.AccountRegistrationRequestDto
import com.zerointrusion.data.network.AccountApi
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.qr.NamespaceRegistrationFromQrFlow
import com.zerointrusion.data.security.DefaultNamespaceService
import com.zerointrusion.data.security.KeyStoreService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.LocalConnectedNamespace
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.qr.NamespaceQrPayload
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import java.lang.reflect.Proxy
import java.security.KeyPairGenerator
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response
import kotlin.coroutines.Continuation
import kotlin.coroutines.intrinsics.COROUTINE_SUSPENDED
import kotlin.coroutines.resumeWithException

class AddDatabaseTest {
    @Test
    fun `health precedes one account POST with exact mobile payload and persistent account`() = runBlocking {
        val fixture = Fixture()
        fixture.repository.addDatabase("  database  ", "  https://new.example/path///  ")
        val url = "https://new.example/path"
        assertEquals(listOf("GET $url${AppConstants.HEALTH_ENDPOINT_PATH}", "POST $url/api/v1/account"), fixture.calls)
        val namespace = DefaultNamespaceService().createNamespace("database", url)
        val expected = """
            {
              "namespace":{"namespaceId":"${namespace.namespaceId}","name":"database","url":"$url"},
              "device":{"mobileDeviceId":"phone12345","publicKey":"${fixture.encodedPublicKey}","deviceName":"Phone"},
              "dek":{"mobileDeviceId":"phone12345","encDek":"phone-encrypted-dek"},
              "email":"user@example.com","nonce":"${fixture.request!!.nonce}"
            }
        """.trimIndent()
        assertEquals(Gson().fromJson(expected, com.google.gson.JsonElement::class.java),
            Gson().toJsonTree(fixture.request))
        assertTrue(fixture.request!!.nonce.matches(Regex("^[0-9a-f]{32}$")))
        val saved = fixture.store.currentMobileRegistrations().single()
        assertEquals("new-account", saved.accountId)
        assertEquals(namespace.namespaceId, saved.namespaceId)
        assertEquals("phone-encrypted-dek", saved.encDek)
        assertTrue(fixture.store.currentEntries().isEmpty())
        assertEquals(fixture.originalState, fixture.state.value)
        assertTrue(fixture.repository.observeAccounts().first().any { it.accountId == "new-account" })
        assertTrue(fixture.repository.observeNamespaces().first().isEmpty())
    }

    @Test
    fun `duplicate normalized mobile URL is rejected without requests even with a different name`() = runBlocking {
        val fixture = Fixture()
        fixture.repository.addDatabase("first", "https://new.example/path/")
        fixture.calls.clear()
        expectFailure<RegistrationFailure.Validation> {
            fixture.repository.addDatabase("different", " https://NEW.example/path/// ")
        }
        assertTrue(fixture.calls.isEmpty())
        assertEquals(1, fixture.store.currentMobileRegistrations().size)
    }

    @Test
    fun `gateway and legacy stored URLs are rejected`() = runBlocking {
        val fixture = Fixture()
        expectFailure<RegistrationFailure.Validation> {
            fixture.repository.addDatabase("database", fixture.originalState.namespaceUrl + "/")
        }
        fixture.store.upsertEntry(LocalConnectedDeviceEntry(
            "https://legacy.example", "legacy-account", "desktop123",
            listOf(LocalConnectedNamespace("legacy-account", "desktop123", "0123456789", "legacy", "https://legacy.example")),
        ))
        expectFailure<RegistrationFailure.Validation> {
            fixture.repository.addDatabase("database", "https://legacy.example/")
        }
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun `bad form is rejected before networking`() = runBlocking {
        val fixture = Fixture()
        listOf("" to "https://new.example", "db" to "not-a-url", "db" to "http://new.example").forEach { (name, url) ->
            expectFailure<RegistrationFailure.Validation> { fixture.repository.addDatabase(name, url) }
        }
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun `unhealthy server prevents POST and persistence`() = runBlocking {
        val fixture = Fixture(healthCode = 503)
        expectFailure<RegistrationFailure.Network> { fixture.repository.addDatabase("db", "https://new.example") }
        assertEquals(listOf("GET https://new.example${AppConstants.HEALTH_ENDPOINT_PATH}"), fixture.calls)
        assertTrue(fixture.store.currentMobileRegistrations().isEmpty())
    }

    @Test
    fun `invalid success bodies and HTTP errors never persist a registration`() = runBlocking {
        listOf("", "not-json", "null", "{}", """{"success":false,"accountId":"account"}""",
            """{"success":true,"accountId":" "}""").forEach { body ->
            val fixture = Fixture(body = body)
            expectFailure<RegistrationFailure.Server> { fixture.repository.addDatabase("db", "https://new.example") }
            assertTrue(fixture.store.currentMobileRegistrations().isEmpty())
        }
        val fixture = Fixture(accountCode = 409)
        expectFailure<RegistrationFailure.Server> { fixture.repository.addDatabase("db", "https://new.example") }
        assertTrue(fixture.store.currentMobileRegistrations().isEmpty())
    }

    @Test
    fun `missing registration and encrypted DEK are rejected`() = runBlocking {
        val fixture = Fixture()
        fixture.state.value = null
        expectFailure<RegistrationFailure.Validation> { fixture.repository.addDatabase("db", "https://new.example") }
        fixture.state.value = fixture.originalState.copy(encDek = " ")
        expectFailure<RegistrationFailure.Validation> { fixture.repository.addDatabase("db", "https://new.example") }
        assertTrue(fixture.calls.isEmpty())
    }

    @Test
    fun `parallel duplicate submissions produce only one POST`() = runBlocking {
        val fixture = Fixture()
        val first = async { runCatching { fixture.repository.addDatabase("db", "https://new.example") } }
        val second = async { runCatching { fixture.repository.addDatabase("db", "https://new.example/") } }
        val results = listOf(first.await(), second.await())
        assertEquals(1, results.count { it.isSuccess })
        assertTrue(results.single { it.isFailure }.exceptionOrNull() is RegistrationFailure.Validation)
        assertEquals(1, fixture.calls.count { it.startsWith("POST") })
    }

    @Test
    fun `QR registration still saves mobile account and supplies desktop preparation`() = runBlocking {
        val fixture = Fixture()
        val flow = NamespaceRegistrationFromQrFlow(
            fixture.api, fixture.keys, unused(ConnectedDevicesRepository::class.java), fixture.store,
            Gson(), RegistrationValidator(),
        )
        val result = flow.prepare(
            fixture.originalState,
            NamespaceQrPayload("desktop123", "desktop-key", "PC", "0123456789", "db", "https://new.example/"),
            "phone-encrypted-dek",
        )
        assertEquals(listOf("POST https://new.example/api/v1/account"), fixture.calls)
        assertEquals("new-account", result.targetNamespaceAccountId)
        assertEquals("desktop123", result.scannedDesktopDeviceId)
        assertEquals("desktop-key", result.scannedPublicKey)
        assertEquals("new-account", fixture.store.currentMobileRegistrations().single().accountId)
    }

    @Test
    fun `QR desktop ID returned as account ID remains rejected`() = runBlocking {
        val fixture = Fixture(body = """{"success":true,"accountId":"desktop123"}""")
        val flow = NamespaceRegistrationFromQrFlow(
            fixture.api, fixture.keys, unused(ConnectedDevicesRepository::class.java), fixture.store,
            Gson(), RegistrationValidator(),
        )
        expectFailure<RegistrationFailure.Server> {
            flow.prepare(fixture.originalState,
                NamespaceQrPayload("desktop123", "desktop-key", "PC", "0123456789", "db", "https://new.example"),
                "phone-encrypted-dek")
        }
        assertTrue(fixture.store.currentMobileRegistrations().isEmpty())
    }

    @Test
    fun `network failures and cancellation do not save accounts`() = runBlocking {
        val fixture = Fixture()
        fixture.networkFailure = java.io.IOException("offline")
        expectFailure<RegistrationFailure.Network> { fixture.repository.addDatabase("db", "https://new.example") }
        fixture.networkFailure = CancellationException("cancelled")
        expectFailure<CancellationException> { fixture.repository.addDatabase("db", "https://new.example") }
        assertTrue(fixture.store.currentMobileRegistrations().isEmpty())
    }

    @Test
    fun `deleting desktop account does not treat remaining mobile-only account as last account`() = runBlocking {
        val fixture = Fixture()
        fixture.repository.addDatabase("db", "https://new.example")
        assertFalse(fixture.repository.deleteAccount("https://old.example", "old-account"))
        assertTrue(fixture.repository.observeAccounts().first().any { it.accountId == "new-account" })
        assertEquals("new-account", fixture.state.value?.accountId)
    }

    @Test
    fun `accounts observer receives a newly saved mobile-only database`() = runBlocking {
        val fixture = Fixture()
        val observed = async(start = CoroutineStart.UNDISPATCHED) {
            fixture.repository.observeAccounts().first { accounts -> accounts.any { it.accountId == "new-account" } }
        }
        fixture.repository.addDatabase("db", "https://new.example")
        assertEquals(2, observed.await().size)
    }

    @Test
    fun `deleting added database preserves gateway but deleting final gateway clears accounts`() = runBlocking {
        val fixture = Fixture()
        fixture.repository.addDatabase("db", "https://new.example")
        assertFalse(fixture.repository.deleteAccount("https://new.example", "new-account"))
        assertEquals(fixture.originalState, fixture.state.value)
        assertTrue(fixture.repository.deleteAccount("https://old.example", "old-account"))
        assertTrue(fixture.repository.observeAccounts().first().isEmpty())
    }

    private class Fixture(
        healthCode: Int = 200,
        accountCode: Int = 200,
        body: String = """{"success":true,"accountId":" new-account "}""",
    ) {
        val originalState = RegistrationState(
            "old-account", "phone12345", "Phone", "old", "https://old.example", "abcdef0123",
            "user@example.com", "phone-encrypted-dek",
        )
        val state = MutableStateFlow<RegistrationState?>(originalState)
        val store = Store()
        val calls = mutableListOf<String>()
        var request: AccountRegistrationRequestDto? = null
        var networkFailure: Exception? = null
        private val publicKey = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair().public
        val encodedPublicKey: String = java.util.Base64.getEncoder().encodeToString(publicKey.encoded)
        val keys = proxy(KeyStoreService::class.java) { method, _ ->
            check(method == "getPublicKey")
            publicKey
        }
        val accountApi = proxy(AccountApi::class.java) { method, _ ->
            when (method) {
                "deleteAccount" -> Response.success(Unit)
                else -> error("Unexpected account API call: $method")
            }
        }
        val api = proxy(RegistrationApi::class.java) { method, args ->
            when (method) {
                "checkServerHealth" -> {
                    calls += "GET ${args[0]}"
                    val failure = networkFailure
                    if (failure != null) {
                        (args.last() as Continuation<*>).resumeWithException(failure)
                        COROUTINE_SUSPENDED
                    } else if (healthCode == 200) Response.success("healthy".toResponseBody())
                    else Response.error<okhttp3.ResponseBody>(healthCode, "unhealthy".toResponseBody())
                }
                "registerAccount" -> {
                    calls += "POST ${args[0]}"
                    request = args[1] as AccountRegistrationRequestDto
                    if (accountCode == 200) Response.success(body.toResponseBody())
                    else Response.error<okhttp3.ResponseBody>(accountCode, """{"message":"rejected"}""".toResponseBody())
                }
                else -> error("Unexpected registration API call: $method")
            }
        }
        val repository = NamespacesRepositoryImpl(
            store,
            object : RegistrationStateStore {
                override fun observeState() = state
                override suspend fun save(state: RegistrationState) { this@Fixture.state.value = state }
                override suspend fun clear() { state.value = null }
            },
            accountApi,
            api,
            proxy(SignatureService::class.java) { _, _ -> "signature" },
            Gson(), keys, DefaultNamespaceService(), RegistrationValidator(),
        )
    }

    private class Store : ConnectedDevicesLocalStore {
        private val entries = MutableStateFlow(emptyList<LocalConnectedDeviceEntry>())
        private val registrations = MutableStateFlow(emptyList<MobileNamespaceRegistration>())
        override fun observeEntries() = entries
        override fun currentEntries() = entries.value
        override suspend fun replaceEntries(entries: List<LocalConnectedDeviceEntry>) { this.entries.value = entries }
        override suspend fun upsertEntry(entry: LocalConnectedDeviceEntry) { entries.value += entry }
        override fun currentMobileRegistrations() = registrations.value
        override fun observeMobileRegistrations() = registrations
        override suspend fun replaceMobileRegistrations(registrations: List<MobileNamespaceRegistration>) {
            this.registrations.value = registrations
        }
        override suspend fun updateMobileRegistrations(
            transform: (List<MobileNamespaceRegistration>) -> List<MobileNamespaceRegistration>,
        ) {
            registrations.value = transform(registrations.value)
        }
    }

    companion object {
        private suspend inline fun <reified T : Throwable> expectFailure(block: () -> Unit) {
            try {
                block()
                error("Expected ${T::class.java.simpleName}")
            } catch (actual: Throwable) {
                assertTrue("Unexpected failure: $actual", actual is T)
            }
        }

        private fun <T : Any> unused(type: Class<T>): T = proxy(type) { method, _ -> error("Unexpected call: $method") }

        private fun <T : Any> proxy(type: Class<T>, handler: (String, Array<out Any?>) -> Any): T =
            requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
                handler(method.name, args ?: emptyArray())
            }))
    }
}
