package com.zerointrusion.presentation.settings

import com.zerointrusion.domain.authentication.ActiveDesktopSession
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceNamespace
import com.zerointrusion.domain.namespaces.NamespaceEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsConnectedDevicesScreenTest {
    @Test
    fun `buildSettingsConnectedDeviceGroups includes live-session-only devices`() {
        val namespaces = listOf(
            NamespaceEntry(
                namespaceName = "Alpha",
                namespaceId = "namespace-1",
                namespaceUrl = "https://alpha.test",
                accountId = "account-1",
                deviceId = "desktop-1",
                deviceName = "Desktop One",
            ),
            NamespaceEntry(
                namespaceName = "Beta",
                namespaceId = "namespace-2",
                namespaceUrl = "https://beta.test",
                accountId = "account-1",
                deviceId = "desktop-2",
                deviceName = "Desktop Two",
            ),
        )

        val connectedDeviceGroups = listOf(
            ConnectedDeviceGroup(
                accountId = "account-1",
                deviceId = "desktop-1",
                namespaces = listOf(
                    ConnectedDeviceNamespace(
                        namespaceId = "namespace-1",
                        namespaceName = "Alpha",
                        namespaceUrl = "https://alpha.test",
                        deviceId = "desktop-1",
                    ),
                ),
            ),
            ConnectedDeviceGroup(
                accountId = "account-1",
                deviceId = "desktop-2",
                namespaces = listOf(
                    ConnectedDeviceNamespace(
                        namespaceId = "namespace-2",
                        namespaceName = "Beta",
                        namespaceUrl = "https://beta.test",
                        deviceId = "desktop-2",
                    ),
                ),
            ),
        )

        val liveDesktopSessions = listOf(
            session(deviceId = "desktop-1", deviceName = "Desktop One"),
            session(deviceId = "desktop-2", deviceName = "Desktop Two"),
            session(deviceId = "desktop-3", deviceName = "Desktop Three"),
        )

        val groups = buildSettingsConnectedDeviceGroups(
            namespaces = namespaces,
            connectedDeviceGroups = connectedDeviceGroups,
            liveDesktopSessions = liveDesktopSessions,
        )

        assertEquals(listOf("desktop-1", "desktop-3", "desktop-2"), groups.map { it.deviceId })
        assertEquals(3, groups.size)
        assertEquals("Desktop Three", groups[1].deviceLabel)
        assertTrue(groups[1].hasLiveSession)
        assertTrue(groups[1].namespaces.isEmpty())
    }

    private fun session(deviceId: String, deviceName: String) = ActiveDesktopSession(
        accountId = "account-1",
        attestationId = "att-$deviceId",
        deviceId = deviceId,
        deviceName = deviceName,
        namespaceName = "Namespace",
        namespaceUrl = "https://namespace.test",
        issuedAt = "2026-01-01T00:00:00Z",
        expiresAt = "2026-01-01T01:00:00Z",
    )
}