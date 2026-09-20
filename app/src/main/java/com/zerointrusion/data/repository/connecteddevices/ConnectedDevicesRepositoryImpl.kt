package com.zerointrusion.data.repository.connecteddevices

import android.util.Log
import com.zerointrusion.data.repository.common.ACCOUNT_LOG_TAG
import com.zerointrusion.data.storage.ConnectedDevicesLocalStore
import com.zerointrusion.data.storage.toConnectedDeviceGroups
import com.zerointrusion.data.storage.toLocalConnectedDeviceEntries
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectedDevicesRepositoryImpl(
    private val accountDetailsSnapshotFetcher: AccountDetailsSnapshotFetcher,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val connectedDevicesLocalStore: ConnectedDevicesLocalStore,
) : ConnectedDevicesRepository {
    override fun observeConnectedDevices(): Flow<List<ConnectedDeviceGroup>> =
        connectedDevicesLocalStore.observeEntries().map { entries -> entries.toConnectedDeviceGroups() }

    override suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup> {
        val snapshot = accountDetailsSnapshotFetcher.fetchSnapshot()
        accountDetailsStateHolder.replaceSnapshot(snapshot)
        val mergedEntries = connectedDevicesLocalStore.currentEntries() + snapshot.namespaces.toLocalConnectedDeviceEntries()
        connectedDevicesLocalStore.replaceEntries(mergedEntries)
        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details parsed response. groups=${accountDetailsSnapshotFetcher.summarizeGroups(snapshot.groups)}",
        )

        return connectedDevicesLocalStore.currentEntries().toConnectedDeviceGroups()
    }
}
