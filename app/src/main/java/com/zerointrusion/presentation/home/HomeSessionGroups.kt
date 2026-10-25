package com.zerointrusion.presentation.home

import com.zerointrusion.domain.authentication.ActiveDesktopSession

internal data class HomeSessionDeviceUiModel(
    val deviceName: String,
    val namespaces: List<HomeSessionNamespaceUiModel>,
)

internal data class HomeSessionNamespaceUiModel(
    val serverName: String,
    val sessions: List<ActiveDesktopSession>,
)

internal fun buildHomeSessionGroups(
    sessions: List<ActiveDesktopSession>,
    fallbackServerName: String,
): List<HomeSessionDeviceUiModel> = sessions
    .groupBy { session -> if (session.deviceIdAmbiguous) "ambiguous|${session.attestationId}" else session.deviceId }
    .toList()
    .sortedBy { (deviceId, deviceSessions) ->
        (deviceSessions.firstOrNull()?.deviceName?.takeIf { it.isNotBlank() } ?: deviceId).lowercase()
    }
    .map { (deviceId, deviceSessions) ->
        val groupRepresentative = deviceSessions.firstOrNull()
        val deviceName = groupRepresentative?.deviceName?.takeIf { it.isNotBlank() } ?: deviceId
        val displayDeviceName = if (groupRepresentative?.deviceIdAmbiguous == true) {
            "$deviceName (unconfirmed device)"
        } else {
            deviceName
        }
        val liveNamespaces = deviceSessions
            .groupBy { session ->
                session.namespaceUrl.trim().trimEnd('/').lowercase() + "|" +
                    session.namespaceName.trim().lowercase()
            }
            .values
            .mapNotNull { namespaceSessions ->
                val representative = namespaceSessions.firstOrNull() ?: return@mapNotNull null
                val resolvedServerName = representative.namespaceName
                    .takeIf { it.isNotBlank() }
                    ?: fallbackServerName.ifBlank { representative.namespaceUrl }

                HomeSessionNamespaceUiModel(resolvedServerName, namespaceSessions)
            }
            .sortedBy { it.serverName.lowercase() }

        HomeSessionDeviceUiModel(displayDeviceName, liveNamespaces)
    }
    .filter { it.namespaces.isNotEmpty() }