// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback

/** Converts Google Nearby callbacks into small service-owned transport events. */
internal class NearbyTransportAdapter(private val events: Events) {
    interface Events {
        fun endpointFound(id: String, info: DiscoveredEndpointInfo)
        fun endpointLost(id: String)
        fun connectionInitiated(id: String, info: ConnectionInfo)
        fun connectionResult(id: String, resolution: ConnectionResolution)
        fun disconnected(id: String)
    }

    val discoveryCallback: EndpointDiscoveryCallback = object : EndpointDiscoveryCallback() {
        override fun onEndpointFound(id: String, info: DiscoveredEndpointInfo) =
            events.endpointFound(id, info)

        override fun onEndpointLost(id: String) = events.endpointLost(id)
    }

    val connectionCallback: ConnectionLifecycleCallback = object : ConnectionLifecycleCallback() {
        override fun onConnectionInitiated(id: String, info: ConnectionInfo) =
            events.connectionInitiated(id, info)

        override fun onConnectionResult(id: String, resolution: ConnectionResolution) =
            events.connectionResult(id, resolution)

        override fun onDisconnected(id: String) = events.disconnected(id)
    }
}
