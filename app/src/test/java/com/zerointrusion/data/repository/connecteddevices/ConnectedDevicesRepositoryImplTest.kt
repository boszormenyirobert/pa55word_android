package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.RemovableAccountDevice
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.namespaces.NamespaceEntry
import com.zerointrusion.domain.registration.RegistrationState
import org.junit.Assert.assertEquals
import org.junit.Test

class ConnectedDevicesRepositoryImplTest {
    @Test
    fun `includes secondary mobile registration without desktop connections`() {
        val targets = resolveDeviceRemovalAccountTargets(
            state = registrationState(),
            namespaces = emptyList(),
            mobileRegistrations = listOf(mobileRegistration("secondary", "http://server:3001")),
        )

        assertEquals(
            listOf(AccountTarget("http://server:3000", "home"), AccountTarget("http://server:3001", "secondary")),
            targets,
        )
    }

    @Test
    fun `deduplicates account targets and excludes other phones and invalid targets`() {
        val targets = resolveDeviceRemovalAccountTargets(
            state = registrationState(),
            namespaces = listOf(NamespaceEntry("dev", "namespace", "http://server:3001", "secondary", "desktop")),
            mobileRegistrations = listOf(
                mobileRegistration(" home ", " http://server:3000/ "),
                mobileRegistration(" secondary ", "http://server:3001/"),
                mobileRegistration("another-account", "http://server:3001"),
                mobileRegistration("foreign", "http://server:3002").copy(mobileDeviceId = "other-phone"),
                mobileRegistration("", "http://server:3003"),
                mobileRegistration("invalid", ""),
            ),
        )

        assertEquals(
            listOf(
                AccountTarget("http://server:3000", "home"),
                AccountTarget("http://server:3001", "another-account"),
                AccountTarget("http://server:3001", "secondary"),
            ),
            targets,
        )
    }

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

    private fun registrationState() = RegistrationState(
        accountId = "home",
        mobileDeviceId = "current-phone",
        deviceName = "Phone",
        namespaceName = "dev",
        namespaceUrl = "http://server:3000",
        namespaceId = "namespace",
        email = "test@example.com",
        phone = "+36123456789",
        encDek = null,
    )

    private fun mobileRegistration(accountId: String, namespaceUrl: String) = MobileNamespaceRegistration(
        accountId = accountId,
        mobileDeviceId = "current-phone",
        namespaceId = "namespace",
        namespaceName = "dev",
        namespaceUrl = namespaceUrl,
        encDek = "encrypted-dek",
    )
}