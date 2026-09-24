// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.transfer.LongRunningOperationLimiter
import com.wisso.wizefiles.feature.sync.SyncPathResolver
import com.wisso.wizefiles.feature.transfer.TransferDatabase
import com.wisso.wizefiles.feature.transfer.TransferItemRecord
import com.wisso.wizefiles.feature.transfer.TransferItemState
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferOperationType
import com.wisso.wizefiles.feature.transfer.TransferProgressCheckpoint
import com.wisso.wizefiles.feature.transfer.TransferProgress
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.storage.path.toAppPath
import com.wisso.wizefiles.storage.path.toUriString
import org.json.JSONObject
import java.io.Closeable
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.StandardOpenOption
import java.util.UUID
import java.util.concurrent.Executors
import kotlin.math.max

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
