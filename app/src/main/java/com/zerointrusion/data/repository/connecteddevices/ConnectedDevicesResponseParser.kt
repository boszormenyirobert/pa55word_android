package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.annotations.SerializedName
import com.google.gson.Gson
import com.google.gson.JsonSyntaxException
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceNamespace
import com.zerointrusion.domain.namespaces.NamespaceEntry

internal class ConnectedDevicesResponseParser(
    private val gson: Gson,
) {
    data class ParsedConnectedDevices(
        val groups: List<ConnectedDeviceGroup>,
        val namespaces: List<NamespaceEntry>,
    )

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
        val desktopDeviceId: String?,
        val deviceName: String?,
        val publicKey: String?,
        val namespaces: List<ConnectedNamespaceDto>?,
    )

    private data class ConnectedNamespaceDto(
        val accountId: String?,
        val deviceId: String?,
        val desktopDeviceId: String?,
        val namespaceId: String?,
        val name: String?,
        val namespace: String?,
        val url: String?,
        val root: Boolean?,
    )

    fun parse(rawBody: String): ParsedConnectedDevices {
        val envelope = try {
            gson.fromJson(rawBody, ConnectedAccountsEnvelopeDto::class.java)
        } catch (_: JsonSyntaxException) {
            throw RegistrationFailure.Server("The backend returned invalid JSON for the connected devices response.")
        }

        if (envelope.success == false) {
            throw RegistrationFailure.Server("The backend returned an unsuccessful connected devices response.")
        }

        val groups = envelope.accounts.orEmpty().flatMap { account ->
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

            val parsedGroups = deviceDtos.mapNotNull { device ->
                val deviceId = device.desktopDeviceId?.trim().orEmpty()
                if (deviceId.isEmpty()) {
                    return@mapNotNull null
                }

                val matchingNamespaceDtos = device.namespaces.orEmpty()
                    .ifEmpty {
                        namespaceDtos.filter { namespace ->
                            namespace.deviceId?.trim().orEmpty() == deviceId
                        }
                    }
                    .ifEmpty { namespaceDtos }

                ConnectedDeviceGroup(
                    accountId = resolvedAccountId,
                    deviceId = deviceId,
                    deviceName = device.deviceName?.trim()?.takeIf { it.isNotEmpty() },
                    namespaces = matchingNamespaceDtos.toConnectedNamespaces(deviceId),
                )
            }

            if (parsedGroups.isNotEmpty()) {
                parsedGroups
            } else {
                val legacyDeviceIds = namespaceDtos.mapNotNull { namespace ->
                    namespace.desktopDeviceId?.trim()?.takeIf { it.isNotEmpty() }
                }.distinct()

                if (legacyDeviceIds.isEmpty()) {
                    throw RegistrationFailure.Server("The backend returned a connected devices entry without any deviceId.")
                }

                legacyDeviceIds.map { deviceId ->
                    val matchingNamespaceDtos = namespaceDtos
                        .filter { it.desktopDeviceId?.trim() == deviceId }
                        .ifEmpty { namespaceDtos }

                    ConnectedDeviceGroup(
                        accountId = resolvedAccountId,
                        deviceId = deviceId,
                        deviceName = null,
                        namespaces = matchingNamespaceDtos.toConnectedNamespaces(deviceId),
                    )
                }
            }
        }.distinctBy { it.accountId + "|" + it.deviceId }
            .sortedWith(compareBy({ it.accountId.lowercase() }, { it.deviceId.lowercase() }))

        val namespaces = envelope.accounts.orEmpty().flatMap { account ->
            val deviceDtos = account.devices.orEmpty()
            val accountNamespaceDtos = account.namespaces.orEmpty()
            val resolvedAccountId = listOfNotNull(
                account.accountId,
                deviceDtos.firstNotNullOfOrNull { it.accountId },
                accountNamespaceDtos.firstNotNullOfOrNull { it.accountId },
            ).firstOrNull()?.trim().orEmpty()

            if (resolvedAccountId.isEmpty()) {
                return@flatMap emptyList()
            }

            if (deviceDtos.isNotEmpty()) {
                deviceDtos.flatMap { device ->
                    val deviceId = device.desktopDeviceId?.trim().orEmpty()
                    if (deviceId.isEmpty()) {
                        return@flatMap emptyList()
                    }

                    val deviceNamespaces = device.namespaces.orEmpty()
                        .ifEmpty {
                            accountNamespaceDtos.filter { namespace ->
                                namespace.desktopDeviceId?.trim().orEmpty() == deviceId
                            }
                        }

                    deviceNamespaces.toNamespaceEntries(
                        accountId = resolvedAccountId,
                        deviceId = deviceId,
                        deviceName = device.deviceName?.trim()?.takeIf { it.isNotEmpty() },
                        publicKey = device.publicKey?.trim()?.takeIf { it.isNotEmpty() },
                    )
                }
            } else {
                accountNamespaceDtos
                    .mapNotNull { namespaceDto -> namespaceDto.desktopDeviceId?.trim()?.takeIf { it.isNotEmpty() } }
                    .distinct()
                    .flatMap { deviceId ->
                        accountNamespaceDtos
                            .filter { namespace -> namespace.desktopDeviceId?.trim().orEmpty() == deviceId }
                            .toNamespaceEntries(
                                accountId = resolvedAccountId,
                                deviceId = deviceId,
                                deviceName = null,
                                publicKey = null,
                            )
                    }
            }
        }.distinctBy { namespace ->
            listOf(
                namespace.accountId.trim().lowercase(),
                namespace.desktopDeviceId.trim().lowercase(),
                namespace.namespaceId.trim().lowercase(),
                namespace.namespaceUrl.trim().trimEnd('/').lowercase(),
            ).joinToString(separator = "|")
        }.sortedWith(compareBy({ it.namespaceName.lowercase() }, { it.desktopDeviceId.lowercase() }))

        return ParsedConnectedDevices(
            groups = groups,
            namespaces = namespaces,
        )
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
                append(" desktopDeviceId=")
                append(namespace.deviceId)
            }
        }.ifEmpty { "no namespaces" }
        "accountId=${group.accountId}, deviceId=${group.deviceId}, namespaces=[$namespaces]"
    }

    private fun List<ConnectedNamespaceDto>.toConnectedNamespaces(deviceIdFallback: String): List<ConnectedDeviceNamespace> = mapNotNull { namespaceDto ->
        val accountId = namespaceDto.accountId?.trim().orEmpty()
        val deviceId = namespaceDto.desktopDeviceId?.trim()?.takeIf { it.isNotEmpty() } ?: deviceIdFallback
        val namespaceId = namespaceDto.namespaceId?.trim().orEmpty()
        val namespaceName = listOf(namespaceDto.name, namespaceDto.namespace)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            .orEmpty()
        val namespaceUrl = namespaceDto.url?.trim().orEmpty()

        if (accountId.isEmpty() || deviceId.isEmpty() || namespaceId.isEmpty() || namespaceName.isEmpty() || namespaceUrl.isEmpty()) {
            return@mapNotNull null
        }

        ConnectedDeviceNamespace(
            accountId = accountId,
            desktopDeviceId = deviceId,
            namespaceId = namespaceId,
            namespaceName = namespaceName,
            namespaceUrl = namespaceUrl,
            deviceId = deviceId,
        )
    }.distinctBy { it.namespaceId.lowercase() + "|" + it.namespaceUrl.lowercase() }
        .sortedBy { it.namespaceName.lowercase() }

    private fun List<ConnectedNamespaceDto>.toNamespaceEntries(
        accountId: String,
        deviceId: String,
        deviceName: String?,
        publicKey: String?,
    ): List<NamespaceEntry> = mapNotNull { namespaceDto ->
        val namespaceId = namespaceDto.namespaceId?.trim().orEmpty()
        val namespaceName = listOf(namespaceDto.name, namespaceDto.namespace)
            .firstOrNull { !it.isNullOrBlank() }
            ?.trim()
            .orEmpty()
        val namespaceUrl = namespaceDto.url?.trim().orEmpty()

        if (namespaceId.isEmpty() || namespaceName.isEmpty() || namespaceUrl.isEmpty()) {
            return@mapNotNull null
        }

        NamespaceEntry(
            namespaceName = namespaceName,
            namespaceId = namespaceId,
            namespaceUrl = namespaceUrl,
            accountId = accountId,
            desktopDeviceId = deviceId,
            deviceName = deviceName,
            publicKey = publicKey,
        )
    }
}