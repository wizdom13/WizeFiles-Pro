package com.wisso.wizefiles.feature.transfer

enum class TransferOperationType {
    COPY,
    MOVE,
    DELETE,
    EXTRACT,
    BACKUP,
    MIRROR,
    TWO_WAY_SYNC,
    MOVE_BACKUP,
    NEARBY_SEND,
    NEARBY_RECEIVE,
    ARCHIVE_MODIFY,
    APK_SIGN,
    AAB_SIGN,
    APKS_SIGN,
    XAPK_SIGN,
    APKM_IMPORT
}

enum class TransferOperationState {
    QUEUED,
    PLANNING,
    RUNNING,
    PAUSE_REQUESTED,
    PAUSED,
    WAITING_FOR_USER,
    RECOVERABLE,
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == COMPLETED_WITH_WARNINGS ||
            this == FAILED || this == CANCELLED
}

enum class TransferItemState {
    PENDING,
    ACTIVE,
    COPIED,
    SKIPPED,
    FAILED,
    BLOCKED
}

/** Shared provider-neutral execution limits used by schedulers and Android executors. */
object TransferExecutionPolicy {
    const val MAXIMUM_CONCURRENT_TRANSFERS = 2
}

object TransferStateMachine {
    private val transitions = mapOf(
        TransferOperationState.QUEUED to setOf(
            TransferOperationState.PLANNING,
            TransferOperationState.RUNNING,
            TransferOperationState.WAITING_FOR_USER,
            TransferOperationState.PAUSED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.PLANNING to setOf(
            TransferOperationState.RUNNING,
            TransferOperationState.PAUSE_REQUESTED,
            TransferOperationState.WAITING_FOR_USER,
            TransferOperationState.RECOVERABLE,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.RUNNING to setOf(
            TransferOperationState.PAUSE_REQUESTED,
            TransferOperationState.WAITING_FOR_USER,
            TransferOperationState.RECOVERABLE,
            TransferOperationState.COMPLETED,
            TransferOperationState.COMPLETED_WITH_WARNINGS,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.PAUSE_REQUESTED to setOf(
            TransferOperationState.PAUSED,
            TransferOperationState.RECOVERABLE,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.PAUSED to setOf(
            TransferOperationState.QUEUED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.WAITING_FOR_USER to setOf(
            TransferOperationState.RUNNING,
            TransferOperationState.QUEUED,
            TransferOperationState.PAUSED,
            TransferOperationState.RECOVERABLE,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.RECOVERABLE to setOf(
            TransferOperationState.QUEUED,
            TransferOperationState.PAUSED,
            TransferOperationState.WAITING_FOR_USER,
            TransferOperationState.FAILED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.FAILED to setOf(
            TransferOperationState.QUEUED,
            TransferOperationState.CANCELLED
        ),
        TransferOperationState.COMPLETED_WITH_WARNINGS to setOf(
            TransferOperationState.QUEUED
        )
    )

    fun canTransition(from: TransferOperationState, to: TransferOperationState): Boolean =
        from == to || to in transitions[from].orEmpty()

    fun requireTransition(from: TransferOperationState, to: TransferOperationState) {
        require(canTransition(from, to)) { "Illegal transfer transition: $from -> $to" }
    }
}

/** Provider-neutral transition sequence used when durable work reconnects to an executor. */
object TransferResumePolicy {
    fun transitionsFrom(state: TransferOperationState): List<TransferOperationState> = when (state) {
        TransferOperationState.PAUSED,
        TransferOperationState.RECOVERABLE -> listOf(
            TransferOperationState.QUEUED,
            TransferOperationState.RUNNING
        )
        TransferOperationState.QUEUED -> listOf(TransferOperationState.RUNNING)
        else -> emptyList()
    }
}
