package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AccountDetailsStateHolder {
    fun observeSnapshot(): Flow<AccountDetailsSnapshot?>

    fun currentSnapshot(): AccountDetailsSnapshot?

    fun replaceSnapshot(snapshot: AccountDetailsSnapshot)
}

@Singleton
class InMemoryAccountDetailsStateHolder @Inject constructor() : AccountDetailsStateHolder {
    private val snapshotFlow = MutableStateFlow<AccountDetailsSnapshot?>(null)

    override fun observeSnapshot(): Flow<AccountDetailsSnapshot?> = snapshotFlow.asStateFlow()

    override fun currentSnapshot(): AccountDetailsSnapshot? = snapshotFlow.value

    @Synchronized
    override fun replaceSnapshot(snapshot: AccountDetailsSnapshot) {
        snapshotFlow.value = snapshot
    }
}