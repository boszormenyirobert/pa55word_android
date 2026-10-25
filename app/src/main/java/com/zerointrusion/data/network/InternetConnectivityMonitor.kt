package com.zerointrusion.data.network

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

class InternetConnectivityMonitor @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val connectivityManager = context.getSystemService(ConnectivityManager::class.java)

    fun observe(): Flow<Boolean> = callbackFlow {
        fun updateConnection() {
            val capabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
            trySend(
                capabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true &&
                    capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED),
            )
        }

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                updateConnection()
            }

            override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
                updateConnection()
            }

            override fun onLost(network: Network) {
                updateConnection()
            }
        }

        connectivityManager.registerDefaultNetworkCallback(callback)
        updateConnection()
        awaitClose { connectivityManager.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()
}