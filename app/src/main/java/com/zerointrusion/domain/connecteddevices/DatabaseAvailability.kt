package com.zerointrusion.domain.connecteddevices

data class DatabaseAvailability(
    val accountId: String,
    val namespaceName: String,
    val namespaceUrl: String,
    val isAvailable: Boolean,
)