// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.advancedformats.staging

/** Decides whether a future viewer can use a descriptor directly or must stage a source. */
object FormatSourcePlanner {
    const val MAX_STAGED_SOURCE_BYTES = 4L * 1024L * 1024L * 1024L
    const val UNKNOWN_SIZE_RESERVATION_BYTES = 256L * 1024L * 1024L
    const val MIN_FREE_SPACE_RESERVE_BYTES = 64L * 1024L * 1024L
    const val MAX_FREE_SPACE_RESERVE_BYTES = 512L * 1024L * 1024L

    fun plan(capabilities: FormatSourceCapabilities): FormatSourceDecision {
        if (capabilities.seekable && !capabilities.requiresResourceBundle) {
            return FormatSourceDecision.Ready(FormatSourceAccess.DIRECT_DESCRIPTOR, 0)
        }
        val reportedSize = capabilities.reportedSizeBytes
        if (reportedSize != null && reportedSize < 0) {
            return FormatSourceDecision.Rejected(FormatSourceRejection.INVALID_SIZE)
        }
        val reservation = reportedSize ?: UNKNOWN_SIZE_RESERVATION_BYTES
        if (reservation > MAX_STAGED_SOURCE_BYTES) {
            return FormatSourceDecision.Rejected(FormatSourceRejection.STAGING_LIMIT_EXCEEDED)
        }
        val safetyReserve = (reservation / 10)
            .coerceIn(MIN_FREE_SPACE_RESERVE_BYTES, MAX_FREE_SPACE_RESERVE_BYTES)
        if (capabilities.availableBytes < reservation + safetyReserve) {
            return FormatSourceDecision.Rejected(FormatSourceRejection.INSUFFICIENT_SPACE)
        }
        val access = if (capabilities.requiresResourceBundle) {
            FormatSourceAccess.STAGE_RESOURCE_BUNDLE
        } else {
            FormatSourceAccess.STAGE_SINGLE_FILE
        }
        return FormatSourceDecision.Ready(access, reservation)
    }
}

data class FormatSourceCapabilities(
    val seekable: Boolean,
    val requiresResourceBundle: Boolean,
    val reportedSizeBytes: Long?,
    val availableBytes: Long
)

enum class FormatSourceAccess {
    DIRECT_DESCRIPTOR,
    STAGE_SINGLE_FILE,
    STAGE_RESOURCE_BUNDLE
}

sealed interface FormatSourceDecision {
    data class Ready(
        val access: FormatSourceAccess,
        val reservationBytes: Long
    ) : FormatSourceDecision

    data class Rejected(val reason: FormatSourceRejection) : FormatSourceDecision
}

enum class FormatSourceRejection {
    INVALID_SIZE,
    STAGING_LIMIT_EXCEEDED,
    INSUFFICIENT_SPACE
}
