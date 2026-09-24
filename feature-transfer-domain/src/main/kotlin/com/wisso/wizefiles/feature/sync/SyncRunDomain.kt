package com.wisso.wizefiles.feature.sync

enum class SyncRunTrigger {
    MANUAL,
    SCHEDULED,
    RETRY
}

enum class SyncRunState {
    PLANNING,
    PREVIEW_READY,
    APPROVED,
    QUEUED,
    RUNNING,
    NEEDS_ATTENTION,
    SAFETY_BLOCKED,
    PAUSED,
    COMPLETED,
    COMPLETED_WITH_WARNINGS,
    FAILED,
    CANCELLED;

    val isTerminal: Boolean
        get() = this == COMPLETED || this == COMPLETED_WITH_WARNINGS ||
            this == FAILED || this == CANCELLED
}

object SyncRunStateMachine {
    private val transitions = mapOf(
        SyncRunState.PLANNING to setOf(
            SyncRunState.PREVIEW_READY,
            SyncRunState.SAFETY_BLOCKED,
            SyncRunState.FAILED,
            SyncRunState.CANCELLED
        ),
        SyncRunState.PREVIEW_READY to setOf(
            SyncRunState.APPROVED,
            SyncRunState.SAFETY_BLOCKED,
            SyncRunState.CANCELLED
        ),
        SyncRunState.APPROVED to setOf(SyncRunState.QUEUED, SyncRunState.CANCELLED),
        SyncRunState.QUEUED to setOf(
            SyncRunState.RUNNING,
            SyncRunState.PAUSED,
            SyncRunState.CANCELLED
        ),
        SyncRunState.RUNNING to setOf(
            SyncRunState.NEEDS_ATTENTION,
            SyncRunState.SAFETY_BLOCKED,
            SyncRunState.PAUSED,
            SyncRunState.COMPLETED,
            SyncRunState.COMPLETED_WITH_WARNINGS,
            SyncRunState.FAILED,
            SyncRunState.CANCELLED
        ),
        SyncRunState.NEEDS_ATTENTION to setOf(
            SyncRunState.QUEUED,
            SyncRunState.PAUSED,
            SyncRunState.CANCELLED
        ),
        SyncRunState.SAFETY_BLOCKED to setOf(SyncRunState.APPROVED, SyncRunState.CANCELLED),
        SyncRunState.PAUSED to setOf(SyncRunState.QUEUED, SyncRunState.CANCELLED),
        SyncRunState.FAILED to setOf(SyncRunState.QUEUED, SyncRunState.CANCELLED),
        SyncRunState.COMPLETED_WITH_WARNINGS to setOf(SyncRunState.QUEUED)
    )

    fun requireTransition(from: SyncRunState, to: SyncRunState) {
        require(from == to || to in transitions[from].orEmpty()) {
            "Illegal sync run transition: $from -> $to"
        }
    }
}
