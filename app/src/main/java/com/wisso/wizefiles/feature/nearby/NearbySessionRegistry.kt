// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

/** Owns endpoint discovery and authentication material for one Nearby service session. */
internal class NearbySessionRegistry {
    private val endpoints = linkedMapOf<String, NearbyEndpoint>()

    var connectedEndpointId: String? = null
    var pendingAuthEndpointId: String? = null
        private set
    var authenticationExpiresAtMillis: Long = 0
        private set
    private var authenticationToken: ByteArray? = null

    fun endpoints(): List<NearbyEndpoint> = endpoints.values.toList()
    fun endpoint(id: String): NearbyEndpoint? = endpoints[id]

    fun discovered(endpoint: NearbyEndpoint) {
        endpoints[endpoint.id] = endpoint
    }

    fun lost(id: String) {
        endpoints.remove(id)
    }

    fun beginAuthentication(endpointId: String, token: ByteArray, expiresAtMillis: Long) {
        clearAuthentication()
        pendingAuthEndpointId = endpointId
        authenticationToken = token.copyOf()
        authenticationExpiresAtMillis = expiresAtMillis
    }

    fun authenticationToken(): ByteArray? = authenticationToken?.copyOf()

    fun clearAuthentication() {
        authenticationToken?.fill(0)
        authenticationToken = null
        pendingAuthEndpointId = null
        authenticationExpiresAtMillis = 0
    }

    fun reset() {
        endpoints.clear()
        connectedEndpointId = null
        clearAuthentication()
    }
}
