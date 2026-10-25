package com.zerointrusion.presentation.home

import com.zerointrusion.domain.authentication.ActiveDesktopSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class HomeSessionGroupsTest {
    @Test
    fun `empty sessions produce no device cards`() {
        assertEquals(emptyList<HomeSessionDeviceUiModel>(), buildHomeSessionGroups(emptyList(), "Fallback"))
    }

    @Test
    fun `devices and servers keep their case insensitive display order`() {
        val sessions = listOf(
            session("first", deviceId = "z", deviceName = "Zulu", serverName = "Zulu server"),
            session("second", deviceId = "a", deviceName = "alpha", serverName = "Zulu server"),
            session("third", deviceId = "a", deviceName = "alpha", serverName = "Alpha server"),
        )

        val groups = buildHomeSessionGroups(sessions, "Fallback")

        assertEquals(listOf("alpha", "Zulu"), groups.map { it.deviceName })
        assertEquals(listOf("Alpha server", "Zulu server"), groups.first().namespaces.map { it.serverName })
    }

    @Test
    fun `namespace grouping normalizes URL and name without changing representative labels`() {
        val first = session("first", serverName = "Server", serverUrl = "https://example.com/")
        val second = session("second", serverName = "server", serverUrl = " HTTPS://EXAMPLE.COM ")

        val namespace = buildHomeSessionGroups(listOf(first, second), "Fallback").single().namespaces.single()

        assertEquals("Server", namespace.serverName)
        assertEquals(listOf(first, second), namespace.sessions)
        assertSame(first, namespace.sessions.first())
        assertSame(second, namespace.sessions.last())
    }

    @Test
    fun `ambiguous devices remain separate even when guessed device IDs match`() {
        val first = session("first").copy(deviceIdAmbiguous = true)
        val second = session("second").copy(deviceIdAmbiguous = true)

        val groups = buildHomeSessionGroups(listOf(first, second), "Fallback")

        assertEquals(2, groups.size)
        assertEquals(listOf("Desktop (unconfirmed device)", "Desktop (unconfirmed device)"), groups.map { it.deviceName })
        assertSame(first, groups.first().namespaces.single().sessions.single())
        assertSame(second, groups.last().namespaces.single().sessions.single())
    }

    @Test
    fun `blank labels use the same device and server fallbacks`() {
        val session = session("first", deviceName = " ", serverName = " ")

        val group = buildHomeSessionGroups(listOf(session), "Fallback server").single()

        assertEquals("desktop", group.deviceName)
        assertEquals("Fallback server", group.namespaces.single().serverName)
        assertEquals(
            session.namespaceUrl,
            buildHomeSessionGroups(listOf(session), " ").single().namespaces.single().serverName,
        )
    }

    @Test
    fun `equal labels retain original insertion order and duplicate session rows`() {
        val first = session("first", deviceId = "first")
        val second = session("second", deviceId = "second")

        val groups = buildHomeSessionGroups(listOf(first, first, second), "Fallback")

        assertEquals(listOf(first, first), groups.first().namespaces.single().sessions)
        assertSame(second, groups.last().namespaces.single().sessions.single())
    }

    private fun session(
        attestationId: String,
        deviceId: String = "desktop",
        deviceName: String = "Desktop",
        serverName: String = "Server",
        serverUrl: String = "https://example.com",
    ) = ActiveDesktopSession(
        accountId = "account",
        attestationId = attestationId,
        deviceId = deviceId,
        deviceName = deviceName,
        namespaceName = serverName,
        namespaceUrl = serverUrl,
        issuedAt = "2026-10-25T00:00:00Z",
        expiresAt = "2026-10-26T00:00:00Z",
    )
}