package com.zerointrusion.data.repository.authentication

import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationSessionDto
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.connecteddevices.InMemoryAccountDetailsStateHolder
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.RegistrationStateStore
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.registration.RegistrationState
import java.lang.reflect.Proxy
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class AuthenticationSessionHandlerTest {
    @Test
    fun `empty desktop connections still query both mobile servers and clear sessions`() = runBlocking {
        val fixture = Fixture()

        assertEquals(emptyList<Any>(), fixture.handler().fetchActiveDesktopSessions())
        assertEquals(2, fixture.requests.size)
        assertEquals(true, fixture.requests.any { it.contains(":3000/") })
        assertEquals(true, fixture.requests.any { it.contains(":3001/") && it.contains("/secondary/") })
    }

    @Test
    fun `failed account refresh without cached desktops is not an empty success`() = runBlocking {
        val fixture = Fixture(refreshFailure = RegistrationFailure.Server("Unavailable"))
        try {
            fixture.handler().fetchActiveDesktopSessions()
            error("Expected refresh failure")
        } catch (exception: RegistrationFailure.Server) {
            assertEquals("Unavailable", exception.message)
            assertEquals(0, fixture.requests.size)
        }
    }

    @Test
    fun `session request cancellation propagates`() = runBlocking {
        val fixture = Fixture(cancelSessions = true)
        try {
            fixture.handler().fetchActiveDesktopSessions()
            error("Expected cancellation")
        } catch (_: CancellationException) {
            assertEquals(true, fixture.requests.isNotEmpty())
        }
    }

    private inner class Fixture(
        private val refreshFailure: Exception? = null,
        private val cancelSessions: Boolean = false,
    ) {
        val requests = mutableListOf<String>()
        private val state = RegistrationState(
            "home", "phone", "Phone", "dev", "http://server:3000", "namespace",
            "test@example.com", "+36123456789", null,
        )
        private val holder = InMemoryAccountDetailsStateHolder()

        fun handler(): AuthenticationSessionHandler {
            val repository = proxy(ConnectedDevicesRepository::class.java) { method, _ ->
                check(method == "fetchConnectedDevices")
                refreshFailure?.let { throw it }
                holder.replaceSnapshot(
                    AccountDetailsSnapshot(
                        groups = emptyList(),
                        namespaces = emptyList(),
                        mobileRegistrations = listOf(
                            MobileNamespaceRegistration("home", "phone", "namespace", "dev", "http://server:3000", "dek"),
                            MobileNamespaceRegistration("secondary", "phone", "namespace", "dev", "http://server:3001", "dek"),
                        ),
                    ),
                )
                emptyList<Any>()
            }
            val api = proxy(RegistrationApi::class.java) { method, args ->
                check(method == "fetchAuthenticationSessions")
                synchronized(requests) { requests += args[0].toString() }
                if (cancelSessions) throw CancellationException("Cancelled")
                Response.success(emptyList<AuthenticationSessionDto>())
            }
            val store = object : RegistrationStateStore {
                override fun observeState() = flowOf(state)
                override suspend fun save(state: RegistrationState) = error("Unexpected save")
                override suspend fun clear() = error("Unexpected clear")
            }
            return AuthenticationSessionHandler(
                api, holder, store,
                proxy(SignatureService::class.java) { method, _ ->
                    check(method == "signJsonString")
                    "signature"
                },
                Gson(), repository,
            )
        }
    }

    private fun <T> proxy(type: Class<T>, handler: (String, Array<out Any?>) -> Any?): T =
        requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        }))
}
