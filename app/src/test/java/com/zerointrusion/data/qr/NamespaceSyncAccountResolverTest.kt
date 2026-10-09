package com.zerointrusion.data.qr

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.qr.NamespaceSyncQrPayload
import org.junit.Assert.assertEquals
import org.junit.Test

class NamespaceSyncAccountResolverTest {
    private val payload = NamespaceSyncQrPayload(
        desktopDeviceId = "WNKXHfaKhG",
        publicKey = "desktop-key",
        deviceName = "PC",
        namespaceId = "1234567890",
        namespaceUrl = "https://target.example/",
    )
    private val registration = MobileNamespaceRegistration(
        "target-account", "phone", "1234567890", "dev", "https://target.example", "target-dek",
    )

    @Test
    fun `target mobile registration works without desktops`() {
        assertEquals(registration, resolve(listOf(registration)))
    }

    @Test
    fun `persisted registration resolves after memory cache is lost`() {
        val gson = Gson()
        val type = object : TypeToken<List<MobileNamespaceRegistration>>() {}.type
        val restored = gson.fromJson<List<MobileNamespaceRegistration>>(gson.toJson(listOf(registration)), type)
        assertEquals(registration, resolve(restored))
    }

    @Test
    fun `home server account and dek do not override target server`() {
        val home = registration.copy(accountId = "home", namespaceUrl = "https://home.example", encDek = "home-dek")
        assertEquals("target-dek", resolve(listOf(home, registration)).encDek)
    }

    @Test
    fun `multiple accounts use first URL match`() {
        assertEquals(registration, resolve(listOf(registration, registration.copy(accountId = "second"))))
    }

    @Test
    fun `stored order determines selected account and its dek`() {
        val second = registration.copy(accountId = "second", encDek = "second-dek")
        assertEquals(second, resolve(listOf(second, registration)))
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `unregistered phone cannot register desktop`() {
        resolve(listOf(registration.copy(mobileDeviceId = "other")))
    }

    @Test
    fun `later namespace ID match does not override first URL match`() {
        val first = registration.copy(namespaceId = "different", accountId = "first", encDek = "first-dek")
        assertEquals(first, resolve(listOf(first, registration)))
    }

    @Test
    fun `skips registrations of other phones and normalizes URL`() {
        val first = registration.copy(namespaceUrl = " HTTPS://TARGET.EXAMPLE/ ")
        assertEquals(first, resolve(listOf(registration.copy(mobileDeviceId = "other"), first, registration)))
    }

    @Test(expected = RegistrationFailure.Validation::class)
    fun `different server cannot supply account`() {
        resolve(listOf(registration.copy(namespaceUrl = "https://foreign.example")))
    }

    @Test(expected = RegistrationFailure.Security::class)
    fun `missing target dek cannot fall back to home dek`() {
        resolve(listOf(registration.copy(encDek = "")))
    }

    @Test(expected = RegistrationFailure.Security::class)
    fun `missing first matching dek does not select a later account`() {
        resolve(listOf(registration.copy(encDek = ""), registration.copy(accountId = "second")))
    }

    @Test
    fun `legacy URL-only desktop namespace hash remains compatible`() {
        assertEquals(
            registration,
            resolveNamespaceSyncAccount(listOf(registration), payload.copy(namespaceId = "legacyhash"), "phone"),
        )
    }

    private fun resolve(entries: List<MobileNamespaceRegistration>) =
        resolveNamespaceSyncAccount(entries, payload, "phone")
}
