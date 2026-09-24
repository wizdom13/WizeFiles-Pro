// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

/** State of a provider-neutral operation. Secrets and provider handles must stay in adapters. */
enum class OperationExecutionState {
    PLANNED,
    RUNNING,
    WAITING_FOR_USER,
    RECOVERABLE,
    COMPLETED,
    CANCELLED,
    FAILED
}

sealed interface OperationExecutionEvent {
    data object Start : OperationExecutionEvent
    data class Progress(val completedBytes: Long, val completedItems: Long) : OperationExecutionEvent
    data class Fail(val failure: FileOperationFailure, val destinationMayExist: Boolean) : OperationExecutionEvent
    data object UserApprovedRetry : OperationExecutionEvent
    data object Cancel : OperationExecutionEvent
    data class Complete(val metadata: MetadataPreservationReport) : OperationExecutionEvent
}

data class OperationExecutionSnapshot(
    val state: OperationExecutionState = OperationExecutionState.PLANNED,
    val completedBytes: Long = 0,
    val completedItems: Long = 0,
    val failure: FileOperationFailure? = null,
    val destinationMayExist: Boolean = false,
    val checkpoint: ResumeCheckpoint? = null,
    val metadata: MetadataPreservationReport = MetadataPreservationReport(emptyList())
) {
    init {
        require(completedBytes >= 0 && completedItems >= 0) { "Progress cannot be negative" }
        require(checkpoint == null || checkpoint.completedBytes <= completedBytes) {
            "Checkpoint cannot exceed completed progress"
        }
    }
}

/** Deterministic transition policy shared by services, workers, and provider adapters. */
object OperationExecutionReducer {
    fun reduce(
        current: OperationExecutionSnapshot,
        event: OperationExecutionEvent
    ): OperationExecutionSnapshot = when (event) {
        OperationExecutionEvent.Start -> {
            require(current.state == OperationExecutionState.PLANNED ||
                current.state == OperationExecutionState.RECOVERABLE) { "Operation cannot start from ${current.state}" }
            current.copy(state = OperationExecutionState.RUNNING, failure = null)
        }
        is OperationExecutionEvent.Progress -> {
            require(current.state == OperationExecutionState.RUNNING) { "Progress requires a running operation" }
            require(event.completedBytes >= current.completedBytes &&
                event.completedItems >= current.completedItems) { "Progress cannot move backwards" }
            current.copy(
                completedBytes = event.completedBytes,
                completedItems = event.completedItems,
                checkpoint = ResumeCheckpoint(event.completedBytes, event.completedItems)
            )
        }
        is OperationExecutionEvent.Fail -> {
            require(current.state == OperationExecutionState.RUNNING) { "Only a running operation can fail" }
            val state = when (event.failure.retryClassification) {
                RetryClassification.TRANSIENT -> OperationExecutionState.RECOVERABLE
                RetryClassification.REQUIRES_USER_ACTION -> OperationExecutionState.WAITING_FOR_USER
                RetryClassification.NEVER -> OperationExecutionState.FAILED
            }
            current.copy(
                state = state,
                failure = event.failure,
                destinationMayExist = event.destinationMayExist,
                checkpoint = current.checkpoint.takeIf { state == OperationExecutionState.RECOVERABLE }
            )
        }
        OperationExecutionEvent.UserApprovedRetry -> {
            require(current.state == OperationExecutionState.WAITING_FOR_USER) {
                "User approval is valid only for a waiting operation"
            }
            current.copy(state = OperationExecutionState.RECOVERABLE)
        }
        OperationExecutionEvent.Cancel -> {
            require(current.state !in TERMINAL_STATES) { "Terminal operations cannot be cancelled" }
            current.copy(state = OperationExecutionState.CANCELLED)
        }
        is OperationExecutionEvent.Complete -> {
            require(current.state == OperationExecutionState.RUNNING) { "Only a running operation can complete" }
            current.copy(
                state = OperationExecutionState.COMPLETED,
                failure = null,
                destinationMayExist = false,
                checkpoint = null,
                metadata = event.metadata
            )
        }
    }

    private val TERMINAL_STATES = setOf(
        OperationExecutionState.COMPLETED,
        OperationExecutionState.CANCELLED,
        OperationExecutionState.FAILED
    )
}

enum class ProviderFailureSignal {
    INTERRUPTED,
    TIMEOUT,
    DISK_FULL,
    PERMISSION_REVOKED,
    READ_ONLY,
    CONFLICT,
    MALFORMED_RESPONSE,
    STALE_RESOURCE,
    UNAVAILABLE,
    PERMANENT
}

/** Optional adapter exception contract for failures richer than JVM exception types. */
interface ProviderFailureSignalSource {
    val providerFailureSignal: ProviderFailureSignal
}

/** Shared JVM failure classification; adapters may translate protocol-specific exceptions first. */
object ProviderJvmFailureClassifier {
    fun classify(exception: java.io.IOException): ProviderFailureSignal {
        val causes = generateSequence<Throwable>(exception) { it.cause }.take(MAX_CAUSE_DEPTH).toList()
        return causes.filterIsInstance<ProviderFailureSignalSource>().firstOrNull()?.providerFailureSignal ?: when {
            causes.any { it is java.io.InterruptedIOException && it !is java.net.SocketTimeoutException } -> ProviderFailureSignal.INTERRUPTED
            causes.any { it is java.net.SocketTimeoutException || it.message?.contains("timed out", true) == true } -> ProviderFailureSignal.TIMEOUT
            causes.any { it is java.nio.file.AccessDeniedException } -> ProviderFailureSignal.PERMISSION_REVOKED
            causes.any { it is java.nio.file.FileAlreadyExistsException } -> ProviderFailureSignal.CONFLICT
            causes.any { it is java.io.FileNotFoundException || it is java.nio.file.NoSuchFileException } -> ProviderFailureSignal.STALE_RESOURCE
            causes.any { it.message?.contains("no space", true) == true || it.message?.contains("disk full", true) == true } -> ProviderFailureSignal.DISK_FULL
            causes.any {
                it is java.net.ConnectException || it is java.net.UnknownHostException ||
                    it is java.net.SocketException || it is java.io.EOFException ||
                    it is java.nio.channels.ClosedChannelException
            } -> ProviderFailureSignal.UNAVAILABLE
            else -> ProviderFailureSignal.PERMANENT
        }
    }

    private const val MAX_CAUSE_DEPTH = 16
}

/** The single provider-neutral mapping adapters use at their exception boundary. */
object ProviderFailureMapper {
    fun map(signal: ProviderFailureSignal, message: String? = null, mutationStarted: Boolean = false) =
        FileOperationFailure(
            kind = when (signal) {
                ProviderFailureSignal.INTERRUPTED -> FileOperationFailureKind.INTERRUPTED
                ProviderFailureSignal.TIMEOUT -> FileOperationFailureKind.TIMEOUT
                ProviderFailureSignal.DISK_FULL -> FileOperationFailureKind.DISK_FULL
                ProviderFailureSignal.PERMISSION_REVOKED -> FileOperationFailureKind.PERMISSION_REVOKED
                ProviderFailureSignal.READ_ONLY -> FileOperationFailureKind.READ_ONLY
                ProviderFailureSignal.CONFLICT -> FileOperationFailureKind.CONFLICT
                ProviderFailureSignal.MALFORMED_RESPONSE -> FileOperationFailureKind.MALFORMED_PROVIDER_RESPONSE
                ProviderFailureSignal.STALE_RESOURCE -> FileOperationFailureKind.STALE_RESOURCE
                ProviderFailureSignal.UNAVAILABLE -> FileOperationFailureKind.PROVIDER_UNAVAILABLE
                ProviderFailureSignal.PERMANENT -> FileOperationFailureKind.PERMANENT
            },
            message = message?.take(MAX_MESSAGE_LENGTH),
            mutationStarted = mutationStarted
        )

    const val MAX_MESSAGE_LENGTH = 1024
}
