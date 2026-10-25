package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.connecteddevices.InMemoryAccountDetailsStateHolder
import com.zerointrusion.data.security.DekService
import com.zerointrusion.data.security.SignatureService
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.LocalConnectedDeviceEntry
import com.zerointrusion.data.storage.LocalConnectedNamespace
import com.zerointrusion.domain.qr.AdditionalMobileNamespace
import com.zerointrusion.domain.qr.AdditionMobileDeviceQrPayload
import com.zerointrusion.domain.registration.RegistrationState
import java.lang.reflect.Proxy
import kotlinx.coroutines.runBlocking
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Test
import retrofit2.Response

class MobileDeviceAdditionFromQrFlowTest {
    @Test
    fun keepsDuplicateUrlsAndExcludesRegistrationUrl() = runBlocking {
        val stored = listOf(
            LocalConnectedNamespace("account-1", "Desktop001", "64917a01df", "start", "http://192.168.178.20:3000", root = true),
            LocalConnectedNamespace("account-2", "Desktop002", "e06798dafb", "dev", "http://192.168.178.20:3001/", root = true),
            LocalConnectedNamespace("account-2", "Desktop002", "e06798dafb", "dev", "http://192.168.178.20:3001/", root = true),
            LocalConnectedNamespace("account-3", "Desktop003", "ffffffffff", "duplicate", "HTTP://192.168.178.20:3001", root = false),
        )

        val flow = MobileDeviceAdditionFromQrFlow(
            registrationApi = proxy(RegistrationApi::class.java) { method ->
                check(method == "registerMobileDevice")
                Response.success("{}".toResponseBody())
            },
            accountDetailsStateHolder = InMemoryAccountDetailsStateHolder(),
            connectedDevicesLocalStore = proxy(ConnectedDevicesLocalStore::class.java) { method ->
                check(method == "currentEntries")
                stored.map { LocalConnectedDeviceEntry(it.url, it.accountId, it.desktopDeviceId, listOf(it)) }
            },
            dekService = proxy(DekService::class.java) { method ->
                when (method) {
                    "decryptDek" -> ByteArray(32)
                    "encryptDek" -> "encrypted-dek"
                    else -> error("Unexpected method: $method")
                }
            },
            signatureService = proxy(SignatureService::class.java) { method ->
                check(method == "signJsonString")
                "signature"
            },
            gson = Gson(),
        )
        val registration = flow.register(
            registrationState = RegistrationState(
                "account-1", "phone", "Phone", "start", "http://192.168.178.20:3000",
                "64917a01df", "user@example.com", "+36123456789", "encrypted-dek",
            ),
            payload = AdditionMobileDeviceQrPayload(
                "public-key", "New phone", "new-phone",
                listOf("http://192.168.178.20:3000", "http://192.168.178.20:3001"),
            ),
            storedEncDek = "encrypted-dek",
        )
        val result = Gson().fromJson(
            requireNotNull(registration.followUpQrPayload),
            Array<AdditionalMobileNamespace>::class.java,
        ).toList()

        assertEquals(2, result.size)
        assertEquals(listOf("account-2", "account-3"), result.map { it.accountId })
        assertEquals(listOf("Desktop002", "Desktop003"), result.map { it.desktopDeviceId })
        assertEquals(listOf("e06798dafb", "ffffffffff"), result.map { it.namespaceId })
        assertEquals(listOf("dev", "duplicate"), result.map { it.namespace })
        assertEquals(
            listOf("http://192.168.178.20:3001", "HTTP://192.168.178.20:3001"),
            result.map { it.url },
        )
        assertEquals(listOf(true, false), result.map { it.root })
    }

    private fun <T> proxy(type: Class<T>, handler: (String) -> Any): T = requireNotNull(
        type.cast(Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { _, method, _ ->
            handler(method.name)
        }),
    )
}