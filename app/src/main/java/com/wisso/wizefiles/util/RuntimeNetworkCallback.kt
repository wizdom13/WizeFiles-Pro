package com.wisso.wizefiles.util

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import androidx.core.content.getSystemService

class RuntimeNetworkCallback(
    context: Context,
    private val onChange: () -> Unit
) {
    private val connectivityManager = context.getSystemService<ConnectivityManager>()!!
    private val callback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) = onChange()

        override fun onLost(network: Network) = onChange()

        override fun onCapabilitiesChanged(network: Network, networkCapabilities: NetworkCapabilities) = onChange()

        override fun onLinkPropertiesChanged(network: Network, linkProperties: android.net.LinkProperties) = onChange()
    }

    fun register() {
        connectivityManager.registerNetworkCallback(
            NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build(),
            callback
        )
    }

    fun unregister() {
        runCatching { connectivityManager.unregisterNetworkCallback(callback) }
    }
}
