package com.zerointrusion.domain.connecteddevices

interface ConnectedDevicesRepository {
    suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup>
}