package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.data.network.AccountErrorResponseDto

internal fun isRemovedAccountRegistration(statusCode: Int, errorBody: String?, gson: Gson): Boolean {
    if (statusCode == 410) return true
    if (statusCode != 401 || errorBody.isNullOrBlank()) return false
    val error = try {
        gson.fromJson(errorBody, AccountErrorResponseDto::class.java)
    } catch (_: JsonSyntaxException) {
        return false
    }
    return error?.internalCode == "e_40101_2"
}
