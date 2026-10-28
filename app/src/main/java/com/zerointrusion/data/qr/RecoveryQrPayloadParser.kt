package com.zerointrusion.data.qr

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import com.zerointrusion.BuildConfig
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.recovery.RecoveryQrPayload
import java.net.URI
import java.util.UUID
import javax.inject.Inject

class RecoveryQrPayloadParser @Inject constructor() {
    fun parse(payload: String, allowHttp: Boolean = BuildConfig.DEBUG): RecoveryQrPayload = try {
        require(payload.length <= MAX_PAYLOAD_LENGTH) { "Recovery QR exceeds the supported size." }
        val json = JsonParser.parseString(payload).asJsonObject
        require(json.keySet() == REQUIRED_FIELDS) { "Recovery QR contains unsupported fields." }
        require(json.requiredString("type") == "recovery") { "QR type must be recovery." }
        val version = json.getAsJsonPrimitive("version")
        require(version.isNumber && version.asString == "1") { "Recovery QR version must be integer 1." }
        val recoveryId = json.requiredString("recoveryId")
        require(UUID.fromString(recoveryId).toString().equals(recoveryId, ignoreCase = true)) {
            "Recovery ID must be a canonical UUID."
        }
        val apiBaseUrl = json.requiredString("apiBaseUrl")
        val uri = URI(apiBaseUrl)
        require(uri.scheme == "https" || (allowHttp && uri.scheme == "http")) {
            "Recovery server requires HTTPS outside development builds."
        }
        require(
            !uri.host.isNullOrBlank() &&
                uri.rawUserInfo == null &&
                uri.rawQuery == null &&
                uri.rawFragment == null &&
                (uri.rawPath.isNullOrEmpty() || uri.rawPath == "/") &&
                (uri.port == -1 || uri.port in 1..65535),
        ) { "Recovery server must be an origin without credentials, path, query, or fragment." }
        RecoveryQrPayload(recoveryId, apiBaseUrl.trimEnd('/'))
    } catch (exception: Exception) {
        throw RegistrationFailure.Validation("RECOVERY_QR_INVALID")
    }

    private fun JsonObject.requiredString(name: String): String {
        val value = getAsJsonPrimitive(name)
        require(value.isString) { "Recovery QR fields must have the expected JSON types." }
        return value.asString
    }

    private companion object {
        const val MAX_PAYLOAD_LENGTH = 4096
        val REQUIRED_FIELDS = setOf("type", "version", "recoveryId", "apiBaseUrl")
    }
}