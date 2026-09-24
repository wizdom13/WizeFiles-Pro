// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.toUriString
import java.util.UUID

internal const val TRANSFER_PATH_SCHEMA_VERSION = 1

data class TransferOperationSpec(
    val id: String = UUID.randomUUID().toString(),
    val type: TransferOperationType,
    val sourceUris: List<String>,
    val destinationUri: String,
    val pathSchemaVersion: Int = TRANSFER_PATH_SCHEMA_VERSION,
    val createdAtMillis: Long = System.currentTimeMillis()
) {
    init {
        require(sourceUris.isNotEmpty()) { "A transfer needs at least one source" }
        require(destinationUri.isNotBlank()) { "A transfer needs a destination" }
        require(pathSchemaVersion > 0) { "Invalid path schema version" }
    }

    companion object {
        fun fromPaths(
            type: TransferOperationType,
            sources: List<AppPath>,
            destination: AppPath
        ): TransferOperationSpec = TransferOperationSpec(
            type = type,
            sourceUris = sources.map { it.toUriString() },
            destinationUri = destination.toUriString()
        )
    }
}

data class TransferOperationRecord(
    val id: String,
    val type: TransferOperationType,
    val state: TransferOperationState,
    val destinationUri: String,
    val pathSchemaVersion: Int,
    val queuePosition: Long,
    val createdAtMillis: Long,
    val startedAtMillis: Long,
    val updatedAtMillis: Long,
    val completedAtMillis: Long,
    val totalItems: Long,
    val completedItems: Long,
    val failedItems: Long,
    val skippedItems: Long,
    val totalBytes: Long,
    val transferredBytes: Long,
    val currentItem: String,
    val lastErrorCategory: String,
    val lastErrorMessage: String,
    val requiresUserAction: Boolean,
    val recoveryReason: String,
    val speedBytesPerSecond: Long = 0,
    val etaSeconds: Long = -1
)

data class TransferItemRecord(
    val id: Long,
    val operationId: String,
    val ordinal: Long,
    val sourceUri: String,
    val targetUri: String,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedMillis: Long,
    val sourceFingerprint: String,
    val state: TransferItemState,
    val bytesCompleted: Long,
    val attemptCount: Int,
    val temporaryTargetUri: String,
    val finalResultUri: String,
    val errorCategory: String,
    val errorMessage: String,
    val completedAtMillis: Long
)

data class TransferProgressCheckpoint(
    val operationId: String,
    val itemId: Long,
    val itemBytesCompleted: Long,
    val operationBytesCompleted: Long,
    val currentItem: String,
    val speedBytesPerSecond: Long = 0,
    val etaSeconds: Long = -1,
    val updatedAtMillis: Long = System.currentTimeMillis()
)

data class PendingTransferDecision(
    val id: Long,
    val operationId: String,
    val itemId: Long,
    val type: String,
    val payload: String,
    val availableResponses: String,
    val createdAtMillis: Long
)
