// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby


/** Converts LAN connection callbacks into small service-owned transport events. */
internal class NearbyTransportAdapter(private val events: Events) {
    interface Events {
        fun endpointFound(id: String, info: NearbyDiscoveryInfo)
        fun endpointLost(id: String)
        fun connectionInitiated(id: String, info: NearbyConnectionInfo)
        fun connectionResult(id: String, resolution: NearbyConnectionResult)
        fun disconnected(id: String)
    }

    val discoveryCallback: NearbyDiscoveryCallback = object : NearbyDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: NearbyDiscoveryInfo) =
            events.endpointFound(id, info)

        override fun onEndpointLost(id: String) = events.endpointLost(id)
    }

    val connectionCallback: NearbyConnectionCallback = object : NearbyConnectionCallback() {
        override fun onConnectionInitiated(id: String, info: NearbyConnectionInfo) =
            events.connectionInitiated(id, info)

        override fun onConnectionResult(id: String, resolution: NearbyConnectionResult) =
            events.connectionResult(id, resolution)

        override fun onDisconnected(id: String) = events.disconnected(id)
    }
}

