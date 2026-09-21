package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.zerointrusion.domain.qr.AdditionMobileDeviceQrPayload
import com.zerointrusion.domain.qr.AdditionalMobileNamespacesQrPayload
import com.zerointrusion.domain.registration.RegistrationValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class QrPayloadParserTest {
    private val parser = QrPayloadParser(Gson(), RegistrationValidator())

    @Test
    fun parsesAdditionMobileDeviceWithoutDesktopFields() {
        val result = parser.parse(
            """{"publicKey":"cHVibGlj","deviceName":"New phone","mobileDeviceId":"AbCdEf1234","namespaceUrl":"https://example.com","type":"addition_mobile_device"}""",
        )

        assertTrue(result is AdditionMobileDeviceQrPayload)
        result as AdditionMobileDeviceQrPayload
        assertEquals("cHVibGlj", result.publicKey)
        assertEquals("New phone", result.deviceName)
        assertEquals("AbCdEf1234", result.mobileDeviceId)
        assertEquals("https://example.com", result.namespaceUrl)
    }

    @Test
    fun parsesAdditionalMobileNamespacesWithoutDeviceFields() {
        val result = parser.parse(
            """[{"accountId":"account-2","desktopDeviceId":"Desktop002","namespaceId":"e06798dafb","namespace":"dev","url":"http://192.168.178.20:3001/","root":true}]""",
        )

        assertTrue(result is AdditionalMobileNamespacesQrPayload)
        result as AdditionalMobileNamespacesQrPayload
        assertEquals(1, result.namespaces.size)
        assertEquals("account-2", result.namespaces.single().accountId)
        assertEquals("Desktop002", result.namespaces.single().desktopDeviceId)
        assertEquals("e06798dafb", result.namespaces.single().namespaceId)
        assertEquals("dev", result.namespaces.single().namespace)
        assertEquals("http://192.168.178.20:3001", result.namespaces.single().url)
        assertTrue(result.namespaces.single().root)
    }

    @Test
    fun defaultsMissingAdditionalNamespaceRootToFalse() {
        val result = parser.parse(
            """[{"accountId":"account-2","desktopDeviceId":"Desktop002","namespaceId":"e06798dafb","namespace":"dev","url":"http://192.168.178.20:3001"}]""",
        ) as AdditionalMobileNamespacesQrPayload

        assertEquals(false, result.namespaces.single().root)
    }
}