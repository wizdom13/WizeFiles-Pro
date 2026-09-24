// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.util.UUID

internal const val SYNC_PATH_SCHEMA_VERSION = 1

enum class SyncMode {
    UPDATE_DESTINATION,
    MIRROR,
    TWO_WAY,
    MOVE_SOURCE
}

enum class SyncComparisonPolicy {
    SMART,
    SIZE_AND_MODIFIED_TIME,
    SIZE_ONLY,
    CHECKSUM
}

enum class SyncConflictPolicy {
    KEEP_BOTH,
    PREFER_SOURCE,
    PREFER_DESTINATION,
    PREFER_NEWER,
    PREFER_LARGER,
    SKIP,
    ASK
}

enum class SyncScheduleType {
    MANUAL,
    INTERVAL,
    DAILY,
    WEEKLY
}

enum class SyncSide {
    SOURCE,
    DESTINATION
}

enum class SyncActionType {
    COPY,
    UPDATE,
    MOVE,
    PROTECT,
    DELETE,
    CONFLICT,
    SKIP
}

enum class SyncActionState {
    PENDING,
    RUNNING,
    COMPLETED,
    SKIPPED,
    BLOCKED,
    FAILED
}

data class SyncProfile(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sourceUri: String,
    val destinationUri: String,
    val mode: SyncMode,
    val comparisonPolicy: SyncComparisonPolicy = SyncComparisonPolicy.SMART,
    val conflictPolicy: SyncConflictPolicy = SyncConflictPolicy.KEEP_BOTH,
    val verifyAfterCopy: Boolean = false,
    val propagateDeletions: Boolean = false,
    val filtersJson: String = "{}",
    val protectionJson: String = defaultProtection(mode),
    val scheduleJson: String = "{\"type\":\"MANUAL\"}",
    val constraintsJson: String = "{\"batteryNotLow\":true,\"storageNotLow\":true}",
    val enabled: Boolean = true,
    val pathSchemaVersion: Int = SYNC_PATH_SCHEMA_VERSION,
    val baselineGeneration: Long = 0,
    val lastRunAtMillis: Long = 0,
    val nextRunAtMillis: Long = 0,
    val consecutiveFailures: Int = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val updatedAtMillis: Long = createdAtMillis
) {
    init {
        require(name.isNotBlank()) { "Sync profile name cannot be blank" }
        require(sourceUri.isNotBlank() && destinationUri.isNotBlank()) {
            "Sync endpoints cannot be blank"
        }
        require(pathSchemaVersion > 0) { "Invalid sync path schema version" }
    }

    companion object {
        private fun defaultProtection(mode: SyncMode): String =
            if (mode == SyncMode.MIRROR || mode == SyncMode.TWO_WAY) {
                "{\"enabled\":true,\"retentionDays\":30,\"versionsPerFile\":5}"
            } else {
                "{\"enabled\":false}"
            }
    }
}

data class SyncRun(
    val id: String = UUID.randomUUID().toString(),
    val profileId: String,
    val trigger: SyncRunTrigger,
    val state: SyncRunState = SyncRunState.PLANNING,
    val baselineBefore: Long,
    val baselineAfter: Long = 0,
    val transferOperationId: String = "",
    val safetyBlockReason: String = "",
    val safetyBlockDetails: String = "",
    val plannedActions: Long = 0,
    val plannedTransferBytes: Long = 0,
    val plannedProtectedBytes: Long = 0,
    val createdAtMillis: Long = System.currentTimeMillis(),
    val startedAtMillis: Long = 0,
    val completedAtMillis: Long = 0
)

data class SyncSnapshotEntry(
    val profileId: String,
    val generation: Long,
    val side: SyncSide,
    val relativePath: String,
    val isDirectory: Boolean,
    val sizeBytes: Long,
    val modifiedAtMillis: Long,
    val modifiedPrecisionMillis: Long,
    val revision: String = "",
    val checksum: String = "",
    val providerIdentity: String
)

data class SyncAction(
    val id: Long = 0,
    val runId: String,
    val ordinal: Long,
    val type: SyncActionType,
    val direction: SyncSide,
    val relativePath: String,
    val sourceUri: String,
    val targetUri: String,
    val sourceFingerprint: String,
    val targetFingerprint: String = "",
    val comparisonReason: String,
    val state: SyncActionState = SyncActionState.PENDING,
    val transferItemId: Long = 0,
    val protectedResultUri: String = "",
    val errorMessage: String = ""
)
