package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileRegistrationRefreshMergerTest {
    private val previous =
        MobileNamespaceRegistration(
            "old",
            "phone12345",
            "0123456789",
            "old",
            "https://old.example",
            "dek",
            email = "old@example.com",
        )
    private val added =
        previous.copy(
            accountId = "new",
            namespaceUrl = "https://new.example",
            email = "new@example.com",
        )

    @Test
    fun `refresh without email preserves only the matching registration email`() {
        val refreshed = listOf(previous.copy(email = ""), added.copy(email = ""))
        assertEquals(
            listOf(previous, added),
            mergeRefreshedMobileRegistrations(
                listOf(previous, added),
                listOf(previous, added),
                refreshed,
            ),
        )
        val differentServer = previous.copy(namespaceUrl = "https://other.example", email = "")
        assertEquals(
            listOf(differentServer),
            mergeRefreshedMobileRegistrations(
                listOf(previous),
                listOf(previous),
                listOf(differentServer),
            ),
        )
    }

    @Test
    fun `server email updates the matching local registration`() {
        val refreshed = previous.copy(email = "changed@example.com")
        assertEquals(
            listOf(refreshed),
            mergeRefreshedMobileRegistrations(listOf(previous), listOf(previous), listOf(refreshed)),
        )
    }

    @Test
    fun `refresh preserves registration added while requests were in flight`() {
        val refreshed = previous.copy(namespaceName = "updated")
        assertEquals(
            listOf(refreshed, added),
            mergeRefreshedMobileRegistrations(
                listOf(previous),
                listOf(previous, added),
                listOf(refreshed),
            ),
        )
    }

    @Test
    fun `missing previous account is not restored from stale cache`() {
        assertEquals(
            listOf(added),
            mergeRefreshedMobileRegistrations(
                listOf(previous),
                listOf(previous, added),
                emptyList(),
            ),
        )
    }

    @Test
    fun `new account already returned by refresh is not duplicated`() {
        assertEquals(
            listOf(previous, added),
            mergeRefreshedMobileRegistrations(
                listOf(previous),
                listOf(previous, added),
                listOf(previous, added),
            ),
        )
    }
}
