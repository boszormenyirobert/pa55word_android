package com.zerointrusion.data.qr

import com.zerointrusion.domain.common.RegistrationFailure
import com.zerointrusion.domain.connecteddevices.MobileNamespaceRegistration
import com.zerointrusion.domain.qr.NamespaceSyncQrPayload

internal fun resolveNamespaceSyncAccount(
    registrations: List<MobileNamespaceRegistration>,
    payload: NamespaceSyncQrPayload,
    mobileDeviceId: String,
): MobileNamespaceRegistration {
    val selected = registrations.firstOrNull {
        it.mobileDeviceId == mobileDeviceId &&
            it.namespaceUrl.trim().trimEnd('/').equals(payload.namespaceUrl.trim().trimEnd('/'), ignoreCase = true)
    } ?: throw RegistrationFailure.Validation("This phone has no registered account for the scanned namespace server.")
    return selected.also {
        if (it.encDek.isBlank()) {
            throw RegistrationFailure.Security("The target account does not contain an encrypted DEK for this phone.")
        }
    }
}
