package com.zerointrusion.data.repository.authentication

import com.google.gson.Gson
import com.zerointrusion.data.network.AuthenticationApi
import com.zerointrusion.data.network.MobileAuthenticationStatusDto
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.domain.authentication.MobileAuthenticationStatus
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class MobileAuthenticationStatusHandlerTest {
    @Test
    fun `signed status request returns the backend terminal status`() = runBlocking {
        var requestUrl = ""
        var signedPayload = ""
        val handler = MobileAuthenticationStatusHandler(
            authenticationApi = proxy(AuthenticationApi::class.java) { method, args ->
                assertEquals("fetchMobileAuthenticationStatus", method)
                requestUrl = args[0].toString()
                Response.success(
                    MobileAuthenticationStatusDto(
                        authorized = false,
                        status = "rejected",
                        notificationId = "notification",
                    ),
                )
            },
            signatureService = proxy(SignatureService::class.java) { method, args ->
                assertEquals("signJsonString", method)
                signedPayload = args[0].toString()
                "signature"
            },
            gson = Gson(),
        )

        val result = handler.fetch(
            namespaceUrl = "https://example.test/",
            accountId = "account",
            mobileDeviceId = "mobile",
            notificationId = "notification",
        )

        assertEquals(MobileAuthenticationStatus.REJECTED, result)
        assertTrue(requestUrl.startsWith("https://example.test/api/v1/authentication/mobile-status/account/notification?"))
        val payload = Gson().fromJson(signedPayload, Map::class.java)
        assertEquals("account", payload["accountId"])
        assertEquals("mobile", payload["mobileDeviceId"])
        assertEquals("notification", payload["notificationId"])
        assertTrue((payload["nonce"] as String).isNotBlank())
    }

    private fun <T> proxy(type: Class<T>, handler: (String, Array<out Any?>) -> Any?): T =
        requireNotNull(type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, args ->
            handler(method.name, args ?: emptyArray())
        }))
}