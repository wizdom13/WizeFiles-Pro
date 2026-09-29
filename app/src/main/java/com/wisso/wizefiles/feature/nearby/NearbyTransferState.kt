// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

internal enum class NearbyPhase {
    IDLE, PLANNING, DISCOVERING, ADVERTISING,
    AUTH_APPROVAL_REQUIRED, AUTH_QR_VISIBLE, AUTH_SCAN_REQUIRED, CONNECTED,
    OFFER_PENDING, TRANSFERRING, PAUSED, RECOVERABLE, COMPLETED, CANCELLED, ERROR
}

internal data class NearbyEndpoint(val id: String, val name: String)

internal data class NearbyUiSnapshot(
    val phase: NearbyPhase = NearbyPhase.IDLE,
    val role: NearbyRole? = null,
    val operationId: String = "",
    val peerName: String = "",
    val authenticationQr: String = "",
    val authenticationExpiresAtMillis: Long = 0,
    val endpoints: List<NearbyEndpoint> = emptyList(),
    val offer: NearbyOffer? = null,
    val message: String = "",
    val visibilityEndsAtMillis: Long = 0,
    val transferredBytes: Long = 0,
    val totalBytes: Long = 0
)

