package com.zerointrusion.data.qr

import com.google.gson.JsonObject
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.recovery.RecoveryQrPayload
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class RecoveryQrPayloadParserTest {
    private val parser = RecoveryQrPayloadParser()
    private val recoveryId = "12345678-1234-1234-1234-123456789abc"

    @Test
    fun validHttpsOriginIsNormalized() {
        assertEquals(
            RecoveryQrPayload(recoveryId, "https://example.test:8443"),
            parser.parse(payload("https://example.test:8443/"), allowHttp = false),
        )
    }

    @Test
    fun httpIsAllowedOnlyWhenExplicitlyEnabledForDevelopment() {
        val raw = payload("http://localhost:3000")
        assertThrows(RegistrationFailure.Validation::class.java) { parser.parse(raw, allowHttp = false) }
        assertEquals("http://localhost:3000", parser.parse(raw, allowHttp = true).apiBaseUrl)
    }

    @Test
    fun unsafeAndMalformedOriginsAreRejected() {
        listOf(
            "https://user:password@example.test",
            "https://@example.test",
            "https://example.test/path",
            "https://example.test/%2f",
            "https://example.test?",
            "https://example.test#",
            "https://example.test:0",
            "https://example.test:65536",
            "file:///example.test",
            "https://",
        ).forEach { origin ->
            val failure = assertThrows(RegistrationFailure.Validation::class.java) {
                parser.parse(payload(origin), allowHttp = false)
            }
            assertEquals("RECOVERY_QR_INVALID", failure.message)
        }
    }

    @Test
    fun invalidSchemaAndTypesExposeOnlyGenericErrorCode() {
        val valid = payload("https://example.test")
        listOf(
            "not-json", "[]", "null", "x".repeat(4097),
            valid.replace("\"version\":1", "\"version\":1.5"),
            valid.replace("\"version\":1", "\"version\":\"1\""),
            valid.replace(recoveryId, "1-1-1-1-1"),
            valid.replace("\"type\":\"recovery\"", "\"type\":123"),
            valid.replace("\"type\":\"recovery\"", "\"type\":\"desktop\""),
            valid.replace("\"version\":1", "\"version\":1,\"unexpected\":true"),
        ).forEach { raw ->
            val failure = assertThrows(RegistrationFailure.Validation::class.java) {
                parser.parse(raw, allowHttp = false)
            }
            assertEquals("RECOVERY_QR_INVALID", failure.message)
        }
    }

    private fun payload(origin: String): String = JsonObject().apply {
        addProperty("type", "recovery")
        addProperty("version", 1)
        addProperty("recoveryId", recoveryId)
        addProperty("apiBaseUrl", origin)
    }.toString()
}