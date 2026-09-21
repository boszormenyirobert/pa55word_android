package com.zerointrusion.data.repository.common

import com.google.gson.Gson
import com.zerointrusion.data.network.MobileDeviceAdditionDekDto
import com.zerointrusion.data.network.MobileDeviceAdditionDeviceDto
import com.zerointrusion.data.network.MobileDeviceAdditionRequestDto
import com.zerointrusion.data.qr.buildMobileDeviceAdditionJson
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileDeviceRequestSupportTest {
    @Test
    fun buildsBootstrapUrl() {
        assertEquals(
            "https://example.com/api/v1/mobile-device/AbCdEf1234/registration?nonce=nonce-value",
            buildMobileDeviceBootstrapUrl("https://example.com/", "AbCdEf1234", "nonce-value"),
        )
    }

    @Test
    fun buildsExactBootstrapSignatureJson() {
        assertEquals(
            """{"mobileDeviceId":"AbCdEf1234","nonce":"nonce-value"}""",
            buildSignedMobileDeviceNonceJson(Gson(), "AbCdEf1234", "nonce-value"),
        )
    }

    @Test
    fun serializesExactMobileDeviceAdditionPayload() {
        val payload = MobileDeviceAdditionRequestDto(
            accountId = "existing-account-id",
            signingMobileDeviceId = "known-mobile-id",
            device = MobileDeviceAdditionDeviceDto("new-mobile-id", "new-mobile-public-key", "New phone"),
            dek = MobileDeviceAdditionDekDto("new-mobile-id", "encrypted-for-new-mobile"),
            nonce = "unique-nonce",
        )

        assertEquals(
            """{"accountId":"existing-account-id","signingMobileDeviceId":"known-mobile-id","device":{"mobileDeviceId":"new-mobile-id","publicKey":"new-mobile-public-key","deviceName":"New phone"},"dek":{"mobileDeviceId":"new-mobile-id","encDek":"encrypted-for-new-mobile"},"nonce":"unique-nonce"}""",
            buildMobileDeviceAdditionJson(Gson(), payload),
        )
    }
}