package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceNamespace

internal class ConnectedDevicesResponseParser(
    private val gson: Gson,
) {
    private data class ConnectedAccountsEnvelopeDto(
        val success: Boolean?,
        val accounts: List<ConnectedAccountResponseDto>?,
    )

    private data class ConnectedAccountResponseDto(
        val accountId: String?,
        val devices: List<ConnectedAccountDeviceDto>?,
        val namespaces: List<ConnectedNamespaceDto>?,
    )

    private data class ConnectedAccountDeviceDto(
        val accountId: String?,
        val deviceId: String?,
    )

    private data class ConnectedNamespaceDto(
        val accountId: String?,
        val deviceId: String?,
        val namespaceId: String?,
        val name: String?,
        val namespace: String?,
        val url: String?,
    )

    fun parse(rawBody: String): List<ConnectedDeviceGroup> {
        val envelope = try {
            gson.fromJson(rawBody, ConnectedAccountsEnvelopeDto::class.java)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Server("The backend returned invalid JSON for the connected devices response.")
        }

        if (envelope.success == false) {
            throw RegistrationFailure.Server("The backend returned an unsuccessful connected devices response.")
        }

        return envelope.accounts.orEmpty().flatMap { account ->
            val deviceDtos = account.devices.orEmpty()
            val namespaceDtos = account.namespaces.orEmpty()
            val resolvedAccountId = listOfNotNull(
                account.accountId,
                deviceDtos.firstNotNullOfOrNull { it.accountId },
                namespaceDtos.firstNotNullOfOrNull { it.accountId },
            ).firstOrNull()?.trim().orEmpty()

            if (resolvedAccountId.isEmpty()) {
                throw RegistrationFailure.Server("The backend returned a connected devices entry without a valid accountId.")
            }

            val deviceIds = buildList {
                deviceDtos.forEach { device ->
                    device.deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                }
                namespaceDtos.forEach { namespace ->
                    namespace.deviceId?.trim()?.takeIf { it.isNotEmpty() }?.let(::add)
                }
            }.distinct()

            if (deviceIds.isEmpty()) {
                throw RegistrationFailure.Server("The backend returned a connected devices entry without any deviceId.")
            }

            deviceIds.map { deviceId ->
                val matchingNamespaceDtos = namespaceDtos
                    .filter { it.deviceId?.trim() == deviceId }
                    .ifEmpty { namespaceDtos }

                ConnectedDeviceGroup(
                    accountId = resolvedAccountId,
                    deviceId = deviceId,
                    namespaces = matchingNamespaceDtos.toConnectedNamespaces(),
                )
            }
        }.distinctBy { it.accountId + "|" + it.deviceId }
            .sortedWith(compareBy({ it.accountId.lowercase() }, { it.deviceId.lowercase() }))
    }

    fun summarize(groups: List<ConnectedDeviceGroup>): String = groups.joinToString(separator = "; ") { group ->
        val namespaces = group.namespaces.joinToString(separator = ", ") { namespace ->
            buildString {
                append(namespace.namespaceName)
                append(" [")
                append(namespace.namespaceId)
                append("] (")
                append(namespace.namespaceUrl)
                append(')')
                append(" deviceId=")
                append(namespace.deviceId)
            }
        }.ifEmpty { "no namespaces" }
        "accountId=${group.accountId}, deviceId=${group.deviceId}, namespaces=[$namespaces]"
    }

    private fun List<ConnectedNamespaceDto>.toConnectedNamespaces(): List<ConnectedDeviceNamespace> = mapNotNull { namespaceDto ->
        val deviceId = namespaceDto.deviceId?.trim().orEmpty()
        val namespaceId = namespaceDto.namespaceId?.trim().orEmpty()
        val namespaceName = listOf(namespaceDto.name, namespaceDto.namespace)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            .orEmpty()
        val namespaceUrl = namespaceDto.url?.trim().orEmpty()

        if (deviceId.isEmpty() || namespaceId.isEmpty() || namespaceName.isEmpty() || namespaceUrl.isEmpty()) {
            return@mapNotNull null
        }

        ConnectedDeviceNamespace(
            namespaceId = namespaceId,
            namespaceName = namespaceName,
            namespaceUrl = namespaceUrl,
            deviceId = deviceId,
        )
    }.distinctBy { it.namespaceId.lowercase() + "|" + it.namespaceUrl.lowercase() }
        .sortedBy { it.namespaceName.lowercase() }
}