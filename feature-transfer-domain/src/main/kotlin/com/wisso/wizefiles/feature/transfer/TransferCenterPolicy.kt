// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.transfer

enum class TransferCenterPrimaryAction {
    NONE, PAUSE, RESUME, RETRY, OPEN_RESULT, OPEN_RECOVERY_FLOW
}

enum class TransferCenterSecondaryAction { CANCEL, DETAILS }

enum class TransferCenterSection { RUNNING, QUEUED, PAUSED, COMPLETED, FAILED }

enum class TransferCenterFilter { ALL, RUNNING, QUEUED, PAUSED, COMPLETED, FAILED }

/** Provider-neutral Transfer Center affordances derived from the durable operation type/state. */
object TransferCenterPolicy {
    private val signingTypes = setOf(
        TransferOperationType.APK_SIGN,
        TransferOperationType.AAB_SIGN,
        TransferOperationType.APKS_SIGN,
        TransferOperationType.XAPK_SIGN
    )
    private val nearbyTypes = setOf(
        TransferOperationType.NEARBY_SEND,
        TransferOperationType.NEARBY_RECEIVE
    )

    fun primaryAction(
        type: TransferOperationType,
        state: TransferOperationState
    ): TransferCenterPrimaryAction {
        if (type in signingTypes && state in recoveryStates) {
            return TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW
        }
        if (type in nearbyTypes) {
            return when (state) {
                TransferOperationState.PLANNING,
                TransferOperationState.RUNNING -> TransferCenterPrimaryAction.PAUSE
                TransferOperationState.PAUSED,
                TransferOperationState.RECOVERABLE ->
                    TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW
                TransferOperationState.COMPLETED,
                TransferOperationState.COMPLETED_WITH_WARNINGS ->
                    TransferCenterPrimaryAction.OPEN_RESULT
                else -> TransferCenterPrimaryAction.NONE
            }
        }
        return when (state) {
            TransferOperationState.PLANNING,
            TransferOperationState.RUNNING -> TransferCenterPrimaryAction.PAUSE
            TransferOperationState.PAUSED,
            TransferOperationState.RECOVERABLE -> TransferCenterPrimaryAction.RESUME
            TransferOperationState.FAILED -> TransferCenterPrimaryAction.RETRY
            TransferOperationState.COMPLETED,
            TransferOperationState.COMPLETED_WITH_WARNINGS ->
                TransferCenterPrimaryAction.OPEN_RESULT
            else -> TransferCenterPrimaryAction.NONE
        }
    }

    fun secondaryAction(state: TransferOperationState): TransferCenterSecondaryAction =
        if (state.isTerminal) TransferCenterSecondaryAction.DETAILS
        else TransferCenterSecondaryAction.CANCEL

    fun section(state: TransferOperationState): TransferCenterSection = when (state) {
        TransferOperationState.PLANNING,
        TransferOperationState.RUNNING,
        TransferOperationState.PAUSE_REQUESTED -> TransferCenterSection.RUNNING
        TransferOperationState.QUEUED -> TransferCenterSection.QUEUED
        TransferOperationState.PAUSED,
        TransferOperationState.WAITING_FOR_USER,
        TransferOperationState.RECOVERABLE -> TransferCenterSection.PAUSED
        TransferOperationState.COMPLETED,
        TransferOperationState.COMPLETED_WITH_WARNINGS -> TransferCenterSection.COMPLETED
        TransferOperationState.FAILED,
        TransferOperationState.CANCELLED -> TransferCenterSection.FAILED
    }

    fun matches(filter: TransferCenterFilter, state: TransferOperationState): Boolean {
        val section = section(state)
        return when (filter) {
            TransferCenterFilter.ALL -> true
            TransferCenterFilter.RUNNING -> section == TransferCenterSection.RUNNING
            TransferCenterFilter.QUEUED -> section == TransferCenterSection.QUEUED
            TransferCenterFilter.PAUSED -> section == TransferCenterSection.PAUSED
            TransferCenterFilter.COMPLETED -> section == TransferCenterSection.COMPLETED
            TransferCenterFilter.FAILED -> section == TransferCenterSection.FAILED
        }
    }

    fun canReorder(state: TransferOperationState): Boolean =
        state == TransferOperationState.QUEUED

    fun canClearHistory(section: TransferCenterSection): Boolean =
        section == TransferCenterSection.COMPLETED || section == TransferCenterSection.FAILED

    fun isSigning(type: TransferOperationType): Boolean = type in signingTypes
    fun isNearby(type: TransferOperationType): Boolean = type in nearbyTypes

    private val recoveryStates = setOf(
        TransferOperationState.PAUSED,
        TransferOperationState.RECOVERABLE,
        TransferOperationState.WAITING_FOR_USER,
        TransferOperationState.FAILED
    )
}
