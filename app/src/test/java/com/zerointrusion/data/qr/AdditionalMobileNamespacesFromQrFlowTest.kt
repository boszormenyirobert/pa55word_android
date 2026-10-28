package com.zerointrusion.data.qr

import com.zerointrusion.domain.registration.RegistrationState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdditionalMobileNamespacesFromQrFlowTest {
    @Test
    fun allowsImportOnlyForExistingAccountMobileAddition() {
        assertFalse(canImportAdditionalMobileNamespaces(registrationState()))
        assertTrue(
            canImportAdditionalMobileNamespaces(
                registrationState().copy(pendingAdditionalNamespacesScan = true),
            ),
        )
    }

    private fun registrationState() = RegistrationState(
        accountId = "account-1",
        mobileDeviceId = "AbCdEf1234",
        deviceName = "New phone",
        namespaceName = "start",
        namespaceUrl = "https://example.com",
        namespaceId = "64917a01df",
        email = "user@example.com",
        encDek = "encrypted-dek",
    )
}