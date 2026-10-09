package com.zerointrusion.data.repository.registration

import com.zerointrusion.data.network.RegistrationApi
import com.zerointrusion.data.repository.common.buildHealthUrl
import com.zerointrusion.data.repository.common.mapNetworkException
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.registration.RegistrationFormInput
import kotlinx.coroutines.CancellationException

internal class RegistrationServerHealthChecker(
    private val registrationApi: RegistrationApi,
) {
    suspend fun requireReachableServers(input: RegistrationFormInput) {
        input.serverUrls().forEach { baseUrl ->
            requireReachableServer(baseUrl)
        }
    }

    suspend fun requireReachableServer(baseUrl: String) {
        val healthUrl = buildHealthUrl(baseUrl)
        val response = try {
            registrationApi.checkServerHealth(healthUrl)
        } catch (exception: CancellationException) {
            throw exception
        } catch (exception: Exception) {
            throw mapNetworkException(healthUrl, exception)
        }
        if (!response.isSuccessful) {
            response.errorBody()?.close()
            throw RegistrationFailure.Network(
                "The server at $baseUrl is not healthy (HTTP ${response.code()}).",
            )
        }
        response.body()?.close()
    }
}

internal fun RegistrationFormInput.serverUrls(): List<String> =
    (listOf(apiUrl) + additionalApiUrls)
        .map { it.trim().trimEnd('/') }
        .filter { it.isNotEmpty() }
        .distinctBy { it.lowercase() }