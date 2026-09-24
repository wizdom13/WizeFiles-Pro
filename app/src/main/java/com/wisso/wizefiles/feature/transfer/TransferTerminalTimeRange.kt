// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

internal data class TransferTimeRange(
    val startedAtMillis: Long,
    val endedAtMillis: Long
)

internal fun TransferOperationRecord.terminalTimeRangeOrNull(): TransferTimeRange? {
    if (state != TransferOperationState.COMPLETED &&
        state != TransferOperationState.COMPLETED_WITH_WARNINGS &&
        state != TransferOperationState.CANCELLED) {
        return null
    }
    val started = startedAtMillis.takeIf { it > 0 } ?: createdAtMillis
    val ended = completedAtMillis.takeIf { it > 0 } ?: updatedAtMillis
    if (started <= 0 || ended <= 0) {
        return null
    }
    return TransferTimeRange(
        startedAtMillis = started,
        endedAtMillis = ended.coerceAtLeast(started)
    )
}
