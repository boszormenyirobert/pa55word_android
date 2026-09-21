package com.zerointrusion.data.qr

import com.zerointrusion.data.storage.LocalConnectedNamespace
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileDeviceAdditionFromQrFlowTest {
    @Test
    fun keepsDuplicateUrlsAndExcludesRegistrationUrl() {
        val stored = listOf(
            LocalConnectedNamespace("account-1", "Desktop001", "64917a01df", "start", "http://192.168.178.20:3000", root = true),
            LocalConnectedNamespace("account-2", "Desktop002", "e06798dafb", "dev", "http://192.168.178.20:3001/", root = true),
            LocalConnectedNamespace("account-2", "Desktop002", "e06798dafb", "dev", "http://192.168.178.20:3001/", root = true),
            LocalConnectedNamespace("account-3", "Desktop003", "ffffffffff", "duplicate", "HTTP://192.168.178.20:3001", root = false),
        )

        val result = buildAdditionalMobileNamespaces(
            storedNamespaces = stored,
            excludedNamespaceUrl = "http://192.168.178.20:3000",
        )

        assertEquals(2, result.size)
        assertEquals(listOf("account-2", "account-3"), result.map { it.accountId })
        assertEquals(listOf("Desktop002", "Desktop003"), result.map { it.desktopDeviceId })
        assertEquals(listOf("e06798dafb", "ffffffffff"), result.map { it.namespaceId })
        assertEquals(listOf("dev", "duplicate"), result.map { it.namespace })
        assertEquals(
            listOf("http://192.168.178.20:3001", "HTTP://192.168.178.20:3001"),
            result.map { it.url },
        )
        assertEquals(listOf(true, false), result.map { it.root })
    }
}