package com.areadiscovery.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import com.areadiscovery.domain.model.ConnectivityState
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.distinctUntilChanged

actual class ConnectivityMonitor(private val context: Context) {
    actual fun observe(): Flow<ConnectivityState> = callbackFlow {
        val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Emit initial state synchronously
        trySend(currentState(cm))

        val callback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) { trySend(ConnectivityState.Online) }
            override fun onLost(network: Network) { trySend(ConnectivityState.Offline) }
            override fun onUnavailable() { trySend(ConnectivityState.Offline) }
        }

        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(request, callback)

        awaitClose { cm.unregisterNetworkCallback(callback) }
    }.distinctUntilChanged()

    private fun currentState(cm: ConnectivityManager): ConnectivityState {
        val network = cm.activeNetwork ?: return ConnectivityState.Offline
        val caps = cm.getNetworkCapabilities(network) ?: return ConnectivityState.Offline
        return if (caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)) {
            ConnectivityState.Online
        } else {
            ConnectivityState.Offline
        }
    }
}
