package com.zerointrusion.domain.connecteddevices

import kotlinx.coroutines.flow.Flow

interface ConnectedDevicesRepository {
    fun observeConnectedDevices(): Flow<List<ConnectedDeviceGroup>>

    suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup>
}