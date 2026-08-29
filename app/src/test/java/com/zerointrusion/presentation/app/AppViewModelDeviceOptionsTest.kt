package com.zerointrusion.presentation.app

import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceNamespace
import com.zerointrusion.domain.namespaces.NamespaceEntry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppViewModelDeviceOptionsTest {
    @Test
    fun `buildQrNamespaceDeviceOptions prefers connected-device name over device id`() {
        val options = buildQrNamespaceDeviceOptions(
            connectedDeviceGroups = listOf(
                ConnectedDeviceGroup(
                    accountId = "account-1",
                    deviceId = "desktop-1",
                    deviceName = "Office Mac",
                    namespaces = listOf(
                        ConnectedDeviceNamespace(
                            namespaceId = "namespace-1",
                            namespaceName = "Primary",
                            namespaceUrl = "https://primary.test",
                            deviceId = "desktop-1",
                        ),
                    ),
                ),
            ),
            storedNamespaces = listOf(
                NamespaceEntry(
                    namespaceName = "Primary",
                    namespaceId = "namespace-1",
                    namespaceUrl = "https://primary.test",
                    accountId = "account-1",
                    deviceId = "desktop-1",
                    deviceName = null,
                ),
            ),
        )

        assertEquals(1, options.size)
        assertEquals("Office Mac", options.single().deviceName)
        assertEquals("desktop-1", options.single().deviceId)
    }
}