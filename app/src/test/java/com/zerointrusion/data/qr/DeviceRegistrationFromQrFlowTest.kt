package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.qr.DeviceRegistrationQrPayload
import com.zerointrusion.domain.registration.RegistrationState
import com.zerointrusion.domain.registration.RegistrationValidator
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class DeviceRegistrationFromQrFlowTest {
    private val state = RegistrationState(
        "account", "AbCdEf1234", "Phone", "dev", "http://server:3000", "f65b0a7c93",
        "test@example.com", "encrypted-dek",
    )
    private val payload = DeviceRegistrationQrPayload(
        "WNKXHfaKhG", "cHVibGlj", "PC", "http://server:3000",
    )
    private val parser = QrPayloadParser(Gson(), RegistrationValidator())

    @Test
    fun `matching target preserves registration account and server`() = runBlocking {
        val result = DeviceRegistrationFromQrFlow().prepare(state, payload)
        assertEquals(state.accountId, result.targetNamespaceAccountId)
        assertEquals(state.namespaceUrl, result.namespaceEntry.namespaceUrl)
    }

    @Test
    fun `normalizes target URL when comparing servers`() = runBlocking {
        val result = DeviceRegistrationFromQrFlow().prepare(
            state, payload.copy(targetUrl = " HTTP://SERVER:3000/ "),
        )
        assertEquals(state.namespaceUrl, result.namespaceEntry.namespaceUrl)
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `different port is rejected`() = runBlocking {
        DeviceRegistrationFromQrFlow().prepare(state, payload.copy(targetUrl = "http://server:3001"))
        Unit
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `different host is rejected`() = runBlocking {
        DeviceRegistrationFromQrFlow().prepare(state, payload.copy(targetUrl = "http://other:3000"))
        Unit
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `different scheme is rejected`() = runBlocking {
        DeviceRegistrationFromQrFlow().prepare(state, payload.copy(targetUrl = "https://server:3000"))
        Unit
    }

    @Test
    fun `parser reads target URL`() {
        val parsed = parser.parse(qrJson("http://server:3000")) as DeviceRegistrationQrPayload
        assertEquals(payload, parsed)
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `parser rejects missing target URL`() {
        parser.parse("""{"type":"deviceRegistration","desktopDeviceId":"WNKXHfaKhG","publicKey":"cHVibGlj","deviceName":"PC"}""")
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `parser rejects invalid target URL`() {
        parser.parse(qrJson("not-a-url"))
    }

    private fun qrJson(targetUrl: String) =
        """{"type":"deviceRegistration","desktopDeviceId":"WNKXHfaKhG","publicKey":"cHVibGlj","deviceName":"PC","targetUrl":"$targetUrl"}"""
}
