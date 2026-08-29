package com.zerointrusion.data.repository.connecteddevices

import android.util.Log
import com.zerointrusion.data.repository.common.ACCOUNT_LOG_TAG
import com.zerointrusion.data.storage.NamespaceStore
import com.zerointrusion.domain.connecteddevices.ConnectedDeviceGroup
import com.zerointrusion.domain.connecteddevices.ConnectedDevicesRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class ConnectedDevicesRepositoryImpl(
    private val accountDetailsSnapshotFetcher: AccountDetailsSnapshotFetcher,
    private val accountDetailsStateHolder: AccountDetailsStateHolder,
    private val namespaceStore: NamespaceStore,
) : ConnectedDevicesRepository {
    override fun observeConnectedDevices(): Flow<List<ConnectedDeviceGroup>> =
        accountDetailsStateHolder.observeSnapshot().map { snapshot -> snapshot?.groups.orEmpty() }

    override suspend fun fetchConnectedDevices(): List<ConnectedDeviceGroup> {
        val snapshot = accountDetailsSnapshotFetcher.fetchSnapshot()
        accountDetailsStateHolder.replaceSnapshot(snapshot)
        namespaceStore.saveNamespaces(snapshot.namespaces)
        Log.d(
            ACCOUNT_LOG_TAG,
            "ACCOUNT details parsed response. groups=${accountDetailsSnapshotFetcher.summarizeGroups(snapshot.groups)}",
        )

        return snapshot.groups
    }
}
