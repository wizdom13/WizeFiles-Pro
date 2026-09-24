// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

enum class MetadataAttribute {
    CREATION_TIME,
    MODIFIED_TIME,
    ACCESS_TIME,
    POSIX_PERMISSIONS,
    OWNER,
    ACL,
    EXTENDED_ATTRIBUTES,
    DOS_ATTRIBUTES,
    SYMBOLIC_LINK
}

enum class PreservationStatus { PRESERVED, UNSUPPORTED, FAILED, NOT_REQUESTED }

data class MetadataPreservation(
    val attribute: MetadataAttribute,
    val status: PreservationStatus,
    val detail: String? = null
) {
    init {
        require(detail == null || detail.length <= MAX_DETAIL_LENGTH) { "detail is too large" }
    }

    companion object { const val MAX_DETAIL_LENGTH = 1024 }
}

data class MetadataPreservationReport(val entries: List<MetadataPreservation>) {
    val isComplete: Boolean get() = entries.none { it.status == PreservationStatus.FAILED || it.status == PreservationStatus.UNSUPPORTED }
    val warnings: List<MetadataPreservation> get() = entries.filter { it.status == PreservationStatus.FAILED || it.status == PreservationStatus.UNSUPPORTED }

    init {
        require(entries.map { it.attribute }.distinct().size == entries.size) {
            "Each metadata attribute must be reported exactly once"
        }
    }
}

sealed interface OperationResult {
    val completedBytes: Long
    val completedItems: Long

    data class Complete(
        override val completedBytes: Long,
        override val completedItems: Long,
        val metadata: MetadataPreservationReport = MetadataPreservationReport(emptyList())
    ) : OperationResult {
        init {
            require(completedBytes >= 0) { "completedBytes must not be negative" }
            require(completedItems >= 0) { "completedItems must not be negative" }
        }
    }

    data class Partial(
        override val completedBytes: Long,
        override val completedItems: Long,
        val failure: FileOperationFailure,
        val checkpoint: ResumeCheckpoint?,
        val destinationMayExist: Boolean
    ) : OperationResult {
        init {
            require(completedBytes >= 0) { "completedBytes must not be negative" }
            require(completedItems >= 0) { "completedItems must not be negative" }
            require(checkpoint == null || checkpoint.completedBytes <= completedBytes) {
                "checkpoint cannot exceed reported progress"
            }
        }
    }
}

data class FileOperationFailure(
    val kind: FileOperationFailureKind,
    val message: String? = null,
    val mutationStarted: Boolean = false
) {
    val retryClassification: RetryClassification get() = kind.retryClassification()
    val requiresUserAction: Boolean get() = retryClassification == RetryClassification.REQUIRES_USER_ACTION
}
