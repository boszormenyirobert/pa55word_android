package com.zerointrusion.data.repository.registration

import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildHealthUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.RegistrationFormInput

internal class RegistrationServerHealthChecker(
    private val registrationApi: RegistrationApi,
) {
    suspend fun requireReachableServers(input: RegistrationFormInput) {
        input.serverUrls().forEach { baseUrl ->
            val healthUrl = buildHealthUrl(baseUrl)
            val response = try {
                registrationApi.checkServerHealth(healthUrl)
            } catch (exception: Exception) {
                throw mapNetworkException(healthUrl, exception)
            }
            if (!response.isSuccessful) {
                throw RegistrationFailure.Network(
                    "The server at $baseUrl is not healthy (HTTP ${response.code()}).",
                )
            }
        }
    }
}

internal fun RegistrationFormInput.serverUrls(): List<String> =
    (listOf(apiUrl) + additionalApiUrls)
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }