package com.wisso.wizefiles.feature.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TransferCenterPolicyTest {
    @Test fun `normal operation primary action covers every state`() {
        assertPrimaryMatrix(
            TransferOperationType.COPY,
            mapOf(
                TransferOperationState.PLANNING to TransferCenterPrimaryAction.PAUSE,
                TransferOperationState.RUNNING to TransferCenterPrimaryAction.PAUSE,
                TransferOperationState.PAUSED to TransferCenterPrimaryAction.RESUME,
                TransferOperationState.RECOVERABLE to TransferCenterPrimaryAction.RESUME,
                TransferOperationState.FAILED to TransferCenterPrimaryAction.RETRY,
                TransferOperationState.COMPLETED to TransferCenterPrimaryAction.OPEN_RESULT,
                TransferOperationState.COMPLETED_WITH_WARNINGS to TransferCenterPrimaryAction.OPEN_RESULT
            )
        )
    }

    @Test fun `Nearby primary action covers every state and failed never advertises retry`() {
        listOf(TransferOperationType.NEARBY_SEND, TransferOperationType.NEARBY_RECEIVE).forEach { type ->
            assertPrimaryMatrix(
                type,
                mapOf(
                    TransferOperationState.PLANNING to TransferCenterPrimaryAction.PAUSE,
                    TransferOperationState.RUNNING to TransferCenterPrimaryAction.PAUSE,
                    TransferOperationState.PAUSED to TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW,
                    TransferOperationState.RECOVERABLE to TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW,
                    TransferOperationState.COMPLETED to TransferCenterPrimaryAction.OPEN_RESULT,
                    TransferOperationState.COMPLETED_WITH_WARNINGS to TransferCenterPrimaryAction.OPEN_RESULT
                )
            )
            assertEquals(
                TransferCenterPrimaryAction.NONE,
                TransferCenterPolicy.primaryAction(type, TransferOperationState.FAILED)
            )
        }
    }

    @Test fun `all signing types share dedicated recovery states and full state behavior`() {
        val signing = listOf(
            TransferOperationType.APK_SIGN,
            TransferOperationType.AAB_SIGN,
            TransferOperationType.APKS_SIGN,
            TransferOperationType.XAPK_SIGN
        )
        signing.forEach { type ->
            assertTrue(TransferCenterPolicy.isSigning(type))
            assertPrimaryMatrix(
                type,
                mapOf(
                    TransferOperationState.PLANNING to TransferCenterPrimaryAction.PAUSE,
                    TransferOperationState.RUNNING to TransferCenterPrimaryAction.PAUSE,
                    TransferOperationState.PAUSED to TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW,
                    TransferOperationState.RECOVERABLE to TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW,
                    TransferOperationState.WAITING_FOR_USER to TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW,
                    TransferOperationState.FAILED to TransferCenterPrimaryAction.OPEN_RECOVERY_FLOW,
                    TransferOperationState.COMPLETED to TransferCenterPrimaryAction.OPEN_RESULT,
                    TransferOperationState.COMPLETED_WITH_WARNINGS to TransferCenterPrimaryAction.OPEN_RESULT
                )
            )
        }
        assertFalse(TransferCenterPolicy.isSigning(TransferOperationType.COPY))
    }

    @Test fun `secondary action agrees with terminal state for every state`() {
        TransferOperationState.entries.forEach { state ->
            assertEquals(
                if (state.isTerminal) TransferCenterSecondaryAction.DETAILS
                else TransferCenterSecondaryAction.CANCEL,
                TransferCenterPolicy.secondaryAction(state)
            )
        }
    }

    @Test fun `every state belongs to exactly one section and matching filter`() {
        TransferOperationState.entries.forEach { state ->
            val section = TransferCenterPolicy.section(state)
            TransferCenterFilter.entries.forEach { filter ->
                assertEquals(
                    filter == TransferCenterFilter.ALL || filter.name == section.name,
                    TransferCenterPolicy.matches(filter, state)
                )
            }
        }
    }

    @Test fun `queue reorder and history clearing are section semantics`() {
        TransferOperationState.entries.forEach { state ->
            assertEquals(state == TransferOperationState.QUEUED, TransferCenterPolicy.canReorder(state))
        }
        TransferCenterSection.entries.forEach { section ->
            assertEquals(
                section == TransferCenterSection.COMPLETED || section == TransferCenterSection.FAILED,
                TransferCenterPolicy.canClearHistory(section)
            )
        }
    }

    private fun assertPrimaryMatrix(
        type: TransferOperationType,
        nonNone: Map<TransferOperationState, TransferCenterPrimaryAction>
    ) {
        TransferOperationState.entries.forEach { state ->
            assertEquals(
                "$type/$state",
                nonNone[state] ?: TransferCenterPrimaryAction.NONE,
                TransferCenterPolicy.primaryAction(type, state)
            )
        }
    }
}
