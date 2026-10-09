package com.zerointrusion.data.repository.connecteddevices

import com.google.gson.Gson
import com.zerointrusion.domain.common.RegistrationFailure
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ConnectedDevicesResponseParserTest {
    private val parser = ConnectedDevicesResponseParser(Gson())

    @Test
    fun `mobile only account exposes registration without inventing a desktop`() {
        val parsed = parser.parse(response(), "phone")
        assertTrue(parsed.groups.isEmpty())
        assertTrue(parsed.namespaces.isEmpty())
        val registration = parsed.mobileRegistrations.single()
        assertEquals("target-account", registration.accountId)
        assertEquals("phone", registration.mobileDeviceId)
        assertEquals("dev", registration.namespaceName)
        assertEquals("https://target.example", registration.namespaceUrl)
        assertEquals("target-dek", registration.encDek)
    }

    @Test
    fun `existing desktop groups and namespaces remain unchanged`() {
        val parsed = parser.parse(response(includeDesktop = true), "phone")
        assertEquals("desktop", parsed.groups.single().deviceId)
        assertEquals("desktop", parsed.namespaces.single().desktopDeviceId)
        assertEquals("target-account", parsed.namespaces.single().accountId)
        assertEquals(1, parsed.mobileRegistrations.size)
    }

    @Test
    fun `another phone cannot supply the target registration`() {
        assertTrue(parser.parse(response(), "other-phone").mobileRegistrations.isEmpty())
    }

    @Test(expected = RegistrationFailure.Server::class)
    fun `malformed unidentified devices still fail`() {
        parser.parse("""{"success":true,"accounts":[{"accountId":"account","devices":[{}]}]}""")
    }

    @Test
    fun `mobile registrations survive snapshot merge independently of desktop namespaces`() {
        val parsed = parser.parse(response(), "phone")
        val snapshot = com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot(
            parsed.groups, parsed.namespaces, parsed.mobileRegistrations,
        )
        val merged = AccountDetailsSnapshotMerger().mergeSnapshots(listOf(snapshot), emptyList())
        assertEquals(parsed.mobileRegistrations, merged.mobileRegistrations)
        assertTrue(merged.groups.isEmpty())
    }

    @Test
    fun `shared account filter removes desktop and mobile connections together`() {
        val parsed = parser.parse(response(includeDesktop = true), "phone")
        val snapshot = com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot(
            parsed.groups, parsed.namespaces, parsed.mobileRegistrations,
        )
        val removed = setOf(AccountDetailsFetchTarget(" HTTPS://TARGET.EXAMPLE/ ", " target-account ").storageIdentity())
        val filtered = snapshot.withoutAccounts(removed)
        assertTrue(filtered.groups.isEmpty())
        assertTrue(filtered.namespaces.isEmpty())
        assertTrue(filtered.mobileRegistrations.isEmpty())
    }

    @Test
    fun `shared account filter preserves connections to another account on same server`() {
        val parsed = parser.parse(response(includeDesktop = true), "phone")
        val snapshot = com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot(
            parsed.groups, parsed.namespaces, parsed.mobileRegistrations,
        )
        val removed = setOf(AccountDetailsFetchTarget("https://target.example", "other-account").storageIdentity())
        assertEquals(snapshot, snapshot.withoutAccounts(removed))
    }

    private fun response(includeDesktop: Boolean = false): String {
        val namespace = """{"accountId":"target-account","namespaceId":"1234567890","namespace":"dev","url":"https://target.example/","root":true}"""
        val desktop = if (includeDesktop) {
            """,{"desktopDeviceId":"desktop","deviceName":"PC","namespaces":[$namespace]}"""
        } else {
            ""
        }
        return """{"success":true,"accounts":[{
            "accountId":"target-account",
            "devices":[{"mobileDeviceId":"phone","desktopDeviceId":null,"namespaces":[$namespace]}$desktop],
            "dek":[{"mobileDeviceId":"phone","encDek":"target-dek"},{"mobileDeviceId":"other","encDek":"wrong-dek"}]
        }]}"""
    }
}
