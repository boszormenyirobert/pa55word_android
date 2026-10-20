package com.zerointrusion.domain.connecteddevices

import kotlinx.coroutines.flow.Flow

interface ConnectedDevicesRepository {
    fun observeConnectedDevices(): Flow<List<ConnectedDeviceGroup>>

    fun observeDatabaseAvailability(): Flow<List<DatabaseAvailability>> = kotlinx.coroutines.flow.flowOf(emptyList())

    suspend fun refreshDatabaseAvailability(database: DatabaseAvailability) = Unit

    suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup>

    suspend fun loadRemovableDevices(): List<RemovableAccountDevice>

    suspend fun deleteDevice(device: RemovableAccountDevice)
}