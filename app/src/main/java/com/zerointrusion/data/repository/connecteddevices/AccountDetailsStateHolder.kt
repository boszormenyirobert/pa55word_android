package com.zerointrusion.data.repository.connecteddevices

import com.zerointrusion.domain.connecteddevices.AccountDetailsSnapshot
import com.zerointrusion.domain.connecteddevices.DatabaseAvailability
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

interface AccountDetailsStateHolder {
    fun observeSnapshot(): Flow<AccountDetailsSnapshot?>

    fun observeDatabaseAvailability(): Flow<List<DatabaseAvailability>>

    fun currentSnapshot(): AccountDetailsSnapshot?

    fun replaceSnapshot(snapshot: AccountDetailsSnapshot)

    fun replaceDatabaseAvailability(availability: List<DatabaseAvailability>)
}

@Singleton
class InMemoryAccountDetailsStateHolder @Inject constructor() : AccountDetailsStateHolder {
    private val snapshotFlow = MutableStateFlow<AccountDetailsSnapshot?>(null)
    private val databaseAvailabilityFlow = MutableStateFlow<List<DatabaseAvailability>>(emptyList())

    override fun observeSnapshot(): Flow<AccountDetailsSnapshot?> = snapshotFlow.asStateFlow()

    override fun observeDatabaseAvailability(): Flow<List<DatabaseAvailability>> = databaseAvailabilityFlow.asStateFlow()

    override fun currentSnapshot(): AccountDetailsSnapshot? = snapshotFlow.value

    @Synchronized
    override fun replaceSnapshot(snapshot: AccountDetailsSnapshot) {
        snapshotFlow.value = snapshot
    }

    @Synchronized
    override fun replaceDatabaseAvailability(availability: List<DatabaseAvailability>) {
        databaseAvailabilityFlow.value = availability
    }
}