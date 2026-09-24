package com.wisso.wizefiles.util

import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.wisso.wizefiles.core.app.application
import com.wisso.wizefiles.core.android.compat.getSystemServiceCompat
import java.net.Inet4Address
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.SocketException
import kotlin.reflect.KClass

fun KClass<InetAddress>.getLocalAddress(): InetAddress? {
    val connectivityManager = application.getSystemServiceCompat(ConnectivityManager::class.java)
    connectivityManager.activeNetwork?.let { network ->
        val capabilities = connectivityManager.getNetworkCapabilities(network)
        if (capabilities?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true) {
            connectivityManager.getLinkProperties(network)?.linkAddresses
                ?.firstOrNull { it.address is Inet4Address && it.address.isSiteLocalAddress }
                ?.address
                ?.let { return it }
        }
    }
    try {
        for (networkInterface in NetworkInterface.getNetworkInterfaces()) {
            if (!networkInterface.isUp || networkInterface.isLoopback) {
                continue
            }
            for (inetAddress in networkInterface.inetAddresses) {
                if (inetAddress is Inet4Address && inetAddress.isSiteLocalAddress) {
                    return inetAddress
                }
            }
        }
    } catch (e: SocketException) {
        com.wisso.wizefiles.util.AppLog.e("Error", "Unexpected failure", e)
    }
    return null
}
