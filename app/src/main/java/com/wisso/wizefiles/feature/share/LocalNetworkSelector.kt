// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.share

import android.content.Context
import android.net.ConnectivityManager
import android.net.LinkProperties
import android.net.Network
import android.net.NetworkCapabilities
import java.net.Inet4Address
import java.net.InetAddress

internal data class SelectedLocalNetwork(
    val network: Network,
    val address: InetAddress,
    val identity: String,
    val interfaceName: String,
)

internal object LocalNetworkSelector {
    @Suppress("DEPRECATION") // A synchronous LAN snapshot has no callback-based platform equivalent.
    fun select(context: Context): SelectedLocalNetwork {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val active = manager.activeNetwork
        val candidates = listOfNotNull(active) + manager.allNetworks.filter { it != active }
        candidates.forEach { network ->
            if (!isApproved(manager.getNetworkCapabilities(network))) return@forEach
            val props = manager.getLinkProperties(network) ?: return@forEach
            val interfaceName = props.interfaceName?.takeIf(String::isNotBlank) ?: return@forEach
            props.linkAddresses.map { it.address }.firstOrNull(::acceptable)?.let { address ->
                return SelectedLocalNetwork(
                    network = network,
                    address = address,
                    identity = "$interfaceName:${address.hostAddress}",
                    interfaceName = interfaceName,
                )
            }
        }
        error("Connect to a Wi-Fi or Ethernet LAN. Mobile data is not supported.")
    }

    fun isApproved(capabilities: NetworkCapabilities?): Boolean =
        capabilities != null &&
            (capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) &&
            !capabilities.hasTransport(NetworkCapabilities.TRANSPORT_VPN)

    fun matches(selected: SelectedLocalNetwork, properties: LinkProperties): Boolean =
        properties.interfaceName == selected.interfaceName &&
            properties.linkAddresses.any { it.address == selected.address }

    private fun acceptable(address: InetAddress) =
        address is Inet4Address && !address.isLoopbackAddress && !address.isLinkLocalAddress
}
