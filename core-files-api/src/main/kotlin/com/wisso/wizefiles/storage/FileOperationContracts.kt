package com.wisso.wizefiles.storage

/** Provider-neutral mutation requested by a planner; credentials and URI grants stay in adapters. */
sealed interface FileOperationRequest {
    val source: FileNode

    data class Copy(override val source: FileNode, val target: FileNode) : FileOperationRequest
    data class Move(override val source: FileNode, val target: FileNode) : FileOperationRequest
    data class Delete(override val source: FileNode) : FileOperationRequest
}

enum class FileOperationFailureKind {
    INTERRUPTED,
    TIMEOUT,
    DISK_FULL,
    PERMISSION_REVOKED,
    READ_ONLY,
    CONFLICT,
    MALFORMED_PROVIDER_RESPONSE,
    STALE_RESOURCE,
    PROVIDER_UNAVAILABLE,
    PERMANENT
}

enum class RetryClassification {
    TRANSIENT,
    REQUIRES_USER_ACTION,
    NEVER
}

fun FileOperationFailureKind.retryClassification(): RetryClassification = when (this) {
    FileOperationFailureKind.TIMEOUT,
    FileOperationFailureKind.PROVIDER_UNAVAILABLE -> RetryClassification.TRANSIENT
    FileOperationFailureKind.PERMISSION_REVOKED,
    FileOperationFailureKind.READ_ONLY,
    FileOperationFailureKind.CONFLICT,
    FileOperationFailureKind.STALE_RESOURCE -> RetryClassification.REQUIRES_USER_ACTION
    FileOperationFailureKind.INTERRUPTED,
    FileOperationFailureKind.DISK_FULL,
    FileOperationFailureKind.MALFORMED_PROVIDER_RESPONSE,
    FileOperationFailureKind.PERMANENT -> RetryClassification.NEVER
}
