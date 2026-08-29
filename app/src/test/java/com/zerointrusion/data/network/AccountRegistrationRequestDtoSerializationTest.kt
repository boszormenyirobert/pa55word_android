package com.zerointrusion.data.network

import com.google.gson.GsonBuilder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountRegistrationRequestDtoSerializationTest {
    private val gson = GsonBuilder().disableHtmlEscaping().create()

    @Test
    fun `serializes root true for initial registration`() {
        val request = AccountRegistrationRequestDto(
            namespace = NamespaceDto(
                namespaceId = "namespace-id",
                name = "Primary Namespace",
                url = "https://example.test",
                root = true,
            ),
            device = DeviceDto(
                deviceId = "device-id",
                publicKey = "public-key",
                deviceName = "Pixel",
            ),
            dek = DekDto(
                deviceId = "device-id",
                encDek = "enc-dek",
            ),
            email = "user@example.test",
            phone = "+36123456789",
            nonce = "nonce-value",
        )

        val json = gson.toJson(request)

        assertEquals(true, request.namespace.root)
        assertTrue(json.contains("\"namespace\":{"))
        assertTrue(json.contains("\"root\":true"))
    }
}