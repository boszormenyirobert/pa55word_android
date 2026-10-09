package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import org.junit.Assert.assertEquals
import org.junit.Test

class MobileRegistrationRefreshMergerTest {
    private val previous = MobileNamespaceRegistration("old", "phone12345", "0123456789", "old", "https://old.example", "dek")
    private val added = previous.copy(accountId = "new", namespaceUrl = "https://new.example")

    @Test
    fun `refresh preserves registration added while requests were in flight`() {
        val refreshed = previous.copy(namespaceName = "updated")
        assertEquals(listOf(refreshed, added),
            mergeRefreshedMobileRegistrations(listOf(previous), listOf(previous, added), listOf(refreshed)))
    }

    @Test
    fun `missing previous account is not restored from stale cache`() {
        assertEquals(listOf(added),
            mergeRefreshedMobileRegistrations(listOf(previous), listOf(previous, added), emptyList()))
    }

    @Test
    fun `new account already returned by refresh is not duplicated`() {
        assertEquals(listOf(previous, added),
            mergeRefreshedMobileRegistrations(listOf(previous), listOf(previous, added), listOf(previous, added)))
    }
}
