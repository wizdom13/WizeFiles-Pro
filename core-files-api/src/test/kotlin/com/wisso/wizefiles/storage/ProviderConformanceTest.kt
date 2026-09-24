// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/** Contract tests for the neutral runner itself; concrete adapters own separate conformance suites. */
abstract class ProviderOperationRunnerContract {
    protected abstract fun port(outcome: ProviderMutationOutcome): RecordingPort

    private val request = FileOperationRequest.Copy(Node("source"), Node("target"))

    @Test
    fun `cancellation before mutation prevents execute and still closes`() {
        val port = port(ProviderMutationOutcome.Complete(1, 1))
        assertThrows(OperationCancelledException::class.java) {
            ProviderOperationRunner.run(port, request, cancellation = OperationCancellation { true })
        }
        assertFalse(port.executed)
        assertTrue(port.closed)
    }

    @Test
    fun `transient partial mutation is resumable and closes resources`() {
        val port = port(ProviderMutationOutcome.Failed(
            ProviderFailureSignal.TIMEOUT,
            completedBytes = 8,
            resumeToken = "offset:8",
            destinationMayExist = true
        ))
        val result = ProviderOperationRunner.run(port, request) as OperationResult.Partial
        assertEquals(RetryClassification.TRANSIENT, result.failure.retryClassification)
        assertEquals(ResumeCheckpoint(8, 0, "offset:8"), result.checkpoint)
        assertTrue(result.failure.mutationStarted)
        assertTrue(port.closed)
    }

    @Test
    fun `cancellation after partial mutation is terminal and reports possible destination`() {
        val result = ProviderOperationRunner.run(
            port(ProviderMutationOutcome.Failed(
                ProviderFailureSignal.INTERRUPTED,
                completedBytes = 4,
                destinationMayExist = true
            )),
            request
        ) as OperationResult.Partial
        assertEquals(FileOperationFailureKind.INTERRUPTED, result.failure.kind)
        assertTrue(result.failure.mutationStarted)
        assertTrue(result.destinationMayExist)
        assertNull(result.checkpoint)
    }

    @Test
    fun `adapter cancellation preserves partial progress without a resume token`() {
        val result = ProviderOperationRunner.run(
            port(
                ProviderMutationOutcome.Cancelled(
                    completedBytes = 5,
                    completedItems = 1,
                    destinationMayExist = true,
                    message = "cancelled during upload"
                )
            ),
            request
        ) as OperationResult.Partial

        assertEquals(FileOperationFailureKind.INTERRUPTED, result.failure.kind)
        assertEquals(5, result.completedBytes)
        assertEquals(1, result.completedItems)
        assertTrue(result.failure.mutationStarted)
        assertTrue(result.destinationMayExist)
        assertNull(result.checkpoint)
    }

    @Test
    fun `cancelled adapter cannot move progress behind a resume checkpoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(
                port(ProviderMutationOutcome.Cancelled(completedBytes = 9)),
                request,
                ResumeCheckpoint(10, 0)
            )
        }
    }

    @Test
    fun `read only permission disk full and conflict never create automatic resume`() {
        listOf(
            ProviderFailureSignal.READ_ONLY to RetryClassification.REQUIRES_USER_ACTION,
            ProviderFailureSignal.PERMISSION_REVOKED to RetryClassification.REQUIRES_USER_ACTION,
            ProviderFailureSignal.DISK_FULL to RetryClassification.NEVER,
            ProviderFailureSignal.CONFLICT to RetryClassification.REQUIRES_USER_ACTION
        ).forEach { (signal, classification) ->
            val result = ProviderOperationRunner.run(
                port(ProviderMutationOutcome.Failed(signal, destinationMayExist = true)),
                request
            ) as OperationResult.Partial
            assertEquals(classification, result.failure.retryClassification)
            assertNull(result.checkpoint)
        }
    }

    @Test
    fun `provider disappearance connection loss stale and malformed responses are explicit`() {
        val expectations = listOf(
            ProviderFailureSignal.UNAVAILABLE to RetryClassification.TRANSIENT,
            ProviderFailureSignal.STALE_RESOURCE to RetryClassification.REQUIRES_USER_ACTION,
            ProviderFailureSignal.MALFORMED_RESPONSE to RetryClassification.NEVER
        )
        expectations.forEach { (signal, expected) ->
            val result = ProviderOperationRunner.run(port(ProviderMutationOutcome.Failed(signal)), request)
                as OperationResult.Partial
            assertEquals(expected, result.failure.retryClassification)
        }
    }

    @Test
    fun `partial metadata preservation remains visible on completion`() {
        val metadata = MetadataPreservationReport(listOf(
            MetadataPreservation(MetadataAttribute.MODIFIED_TIME, PreservationStatus.PRESERVED),
            MetadataPreservation(MetadataAttribute.ACL, PreservationStatus.UNSUPPORTED)
        ))
        val result = ProviderOperationRunner.run(
            port(ProviderMutationOutcome.Complete(12, 1, metadata)),
            request
        ) as OperationResult.Complete
        assertFalse(result.metadata.isComplete)
        assertEquals(listOf(MetadataAttribute.ACL), result.metadata.warnings.map { it.attribute })
    }

    @Test
    fun `resume checkpoint is supplied unchanged to adapter`() {
        val port = port(ProviderMutationOutcome.Complete(20, 2))
        val checkpoint = ResumeCheckpoint(10, 1, "resume")
        ProviderOperationRunner.run(port, request, checkpoint)
        assertEquals(checkpoint, port.checkpoint)
    }

    @Test
    fun `unsupported mutation is rejected before adapter execution and still closes`() {
        val port = RecordingPort(
            ProviderMutationOutcome.Complete(0, 1),
            ProviderMutationCapabilities(setOf(ProviderMutationKind.DELETE))
        )
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(port, request)
        }
        assertFalse(port.executed)
        assertTrue(port.closed)
    }

    @Test
    fun `resume checkpoint requires an adapter resume capability`() {
        val port = RecordingPort(
            ProviderMutationOutcome.Complete(10, 1),
            ProviderMutationCapabilities(setOf(ProviderMutationKind.COPY))
        )
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(port, request, ResumeCheckpoint(5, 0))
        }
        assertFalse(port.executed)
        assertTrue(port.closed)
    }

    @Test
    fun `same-node copy is rejected before provider mutation`() {
        val port = port(ProviderMutationOutcome.Complete(0, 1))
        val sameNode = Node("same")
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(
                port,
                FileOperationRequest.Copy(sameNode, sameNode)
            )
        }
        assertFalse(port.executed)
        assertTrue(port.closed)
    }

    @Test
    fun `blank provider node name is rejected before adapter execution`() {
        val port = port(ProviderMutationOutcome.Complete(0, 1))
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(
                port,
                FileOperationRequest.Delete(Node(""))
            )
        }
        assertFalse(port.executed)
        assertTrue(port.closed)
    }

    @Test
    fun `cross-provider copy requires explicit adapter capability`() {
        val port = RecordingPort(
            ProviderMutationOutcome.Complete(0, 1),
            ProviderMutationCapabilities(setOf(ProviderMutationKind.COPY))
        )
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(
                port,
                FileOperationRequest.Copy(Node("source", "one"), Node("target", "two"))
            )
        }
        assertFalse(port.executed)
        assertTrue(port.closed)
    }

    @Test
    fun `adapter cannot move progress behind a resume checkpoint`() {
        assertThrows(IllegalArgumentException::class.java) {
            ProviderOperationRunner.run(
                port(ProviderMutationOutcome.Failed(ProviderFailureSignal.TIMEOUT, completedBytes = 9)),
                request,
                ResumeCheckpoint(10, 0)
            )
        }
    }

    protected class RecordingPort(
        private val outcome: ProviderMutationOutcome,
        override val capabilities: ProviderMutationCapabilities = ProviderMutationCapabilities(
            supportedKinds = ProviderMutationKind.entries.toSet(),
            supportsResume = true,
            supportsCrossProviderCopy = true,
            supportsCrossProviderMove = true
        )
    ) : ProviderMutationPort {
        var executed = false
        var closed = false
        var checkpoint: ResumeCheckpoint? = null

        override fun execute(
            request: FileOperationRequest,
            checkpoint: ResumeCheckpoint,
            cancellation: OperationCancellation
        ): ProviderMutationOutcome {
            executed = true
            this.checkpoint = checkpoint
            return outcome
        }

        override fun close() { closed = true }
    }

    private data class Node(
        override val name: String,
        override val backendId: String = "test"
    ) : FileNode {
        override val path: String = name
    }
}

class ProviderOperationRunnerSuccessContractTest : ProviderOperationRunnerContract() {
    override fun port(outcome: ProviderMutationOutcome) = RecordingPort(outcome)
}

class ProviderOperationRunnerFailureContractTest : ProviderOperationRunnerContract() {
    override fun port(outcome: ProviderMutationOutcome) = RecordingPort(outcome)
}
