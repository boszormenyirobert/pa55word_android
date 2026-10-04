package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedDevicesRepositoryImplTest {
    @Test
    fun `filters duplicate devices but preserves duplicate current phone`() {
        val devices = listOf(
            mobileDevice(id = "current-phone", namespaceUrl = "https://first.example"),
            mobileDevice(id = "current-phone", namespaceUrl = "https://first.example"),
            mobileDevice(id = "current-phone", namespaceUrl = "https://second.example"),
            mobileDevice(id = "other-phone", namespaceUrl = "https://first.example"),
            mobileDevice(id = "other-phone", namespaceUrl = "https://second.example"),
            desktopDevice(id = "desktop", namespaceUrl = "https://first.example"),
            desktopDevice(id = "desktop", namespaceUrl = "https://second.example"),
        )

        val filteredDevices = devices.filterUniqueRemovableDevices(currentMobileDeviceId = "CURRENT-PHONE")

        assertEquals(
            listOf("current-phone", "current-phone", "other-phone", "desktop"),
            filteredDevices.map(RemovableAccountDevice::id),
        )
    }

    private fun mobileDevice(id: String, namespaceUrl: String) = RemovableAccountDevice(
        accountId = "account",
        namespaceUrl = namespaceUrl,
        targetMobileDeviceId = id,
    )

    private fun desktopDevice(id: String, namespaceUrl: String) = RemovableAccountDevice(
        accountId = "account",
        namespaceUrl = namespaceUrl,
        targetDesktopDeviceId = id,
    )
}