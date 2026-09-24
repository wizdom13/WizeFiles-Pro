// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

internal data class SyncEndpoint(
    val uri: String,
    val storageIdentity: String,
    val writable: Boolean,
    val available: Boolean
)

internal sealed interface SyncEndpointValidation {
    data object Valid : SyncEndpointValidation
    data class Invalid(val reason: String) : SyncEndpointValidation
}

internal object SyncEndpointValidator {
    fun validate(source: SyncEndpoint, destination: SyncEndpoint): SyncEndpointValidation {
        if (!source.available) return SyncEndpointValidation.Invalid("SOURCE_UNAVAILABLE")
        if (!destination.available) return SyncEndpointValidation.Invalid("DESTINATION_UNAVAILABLE")
        if (!destination.writable) return SyncEndpointValidation.Invalid("DESTINATION_READ_ONLY")
        val first = normalize(source.uri)
        val second = normalize(destination.uri)
        if (first == second) return SyncEndpointValidation.Invalid("IDENTICAL_ENDPOINTS")
        if (source.storageIdentity == destination.storageIdentity && scopesOverlap(first, second)) {
            return SyncEndpointValidation.Invalid("OVERLAPPING_ENDPOINTS")
        }
        return SyncEndpointValidation.Valid
    }

    fun normalize(uri: String): String = uri.trim().trimEnd('/')

    fun scopesOverlap(firstUri: String, secondUri: String): Boolean {
        val first = normalize(firstUri)
        val second = normalize(secondUri)
        return first == second || first.startsWith("$second/") || second.startsWith("$first/")
    }
}
