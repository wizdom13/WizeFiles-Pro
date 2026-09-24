package com.wisso.wizefiles.storage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.Assert.assertThrows

class CoreFileModelsTest {
    @Test
    fun `unknown values have safe legacy projections`() {
        val metadata = FileMetadata(false, null, null)

        assertTrue(metadata.isRegularFile)
        assertEquals(0L, metadata.size())
        assertEquals(0L, metadata.lastModifiedTime().toMillis())
    }

    @Test
    fun `directory is not projected as a regular file`() {
        assertFalse(FileMetadata(true, 0, 0).isRegularFile)
    }

    @Test
    fun `retry classifications never loop on permission or disk failures`() {
        assertEquals(
            RetryClassification.REQUIRES_USER_ACTION,
            FileOperationFailureKind.PERMISSION_REVOKED.retryClassification()
        )
        assertEquals(
            RetryClassification.NEVER,
            FileOperationFailureKind.DISK_FULL.retryClassification()
        )
        assertEquals(
            RetryClassification.TRANSIENT,
            FileOperationFailureKind.TIMEOUT.retryClassification()
        )
    }

    @Test
    fun `provider failures distinguish retry from user action`() {
        assertEquals(RetryClassification.TRANSIENT, FileOperationFailureKind.PROVIDER_UNAVAILABLE.retryClassification())
        assertTrue(FileOperationFailure(FileOperationFailureKind.STALE_RESOURCE).requiresUserAction)
        assertEquals(RetryClassification.NEVER, FileOperationFailureKind.MALFORMED_PROVIDER_RESPONSE.retryClassification())
    }

    @Test
    fun `conflict resolver rejects destructive type replacement and unsafe names`() {
        assertTrue(ConflictResolver.decide(ConflictPolicy.REPLACE, ConflictKind.FILE_REPLACES_DIRECTORY) is ConflictDecision.Reject)
        assertTrue(ConflictResolver.decide(ConflictPolicy.KEEP_BOTH, ConflictKind.SAME_TYPE, "../escape") is ConflictDecision.Reject)
        assertTrue(ConflictResolver.decide(ConflictPolicy.KEEP_BOTH, ConflictKind.SAME_TYPE, "bad\u0000name") is ConflictDecision.Reject)
        assertEquals(ConflictDecision.Rename("copy.txt"), ConflictResolver.decide(ConflictPolicy.KEEP_BOTH, ConflictKind.SAME_TYPE, "copy.txt"))
    }

    @Test
    fun `conflict resolver preserves skip overwrite cancel and directory behavior`() {
        assertEquals(ConflictDecision.Skip, ConflictResolver.decide(ConflictPolicy.SKIP, ConflictKind.SAME_TYPE))
        assertEquals(ConflictDecision.Proceed, ConflictResolver.decide(ConflictPolicy.REPLACE, ConflictKind.SAME_TYPE))
        assertEquals(ConflictDecision.Abort, ConflictResolver.decide(ConflictPolicy.ABORT, ConflictKind.SAME_TYPE))
        assertEquals(
            ConflictDecision.RequiresUserAction,
            ConflictResolver.decide(ConflictPolicy.ASK, ConflictKind.SAME_TYPE)
        )
        assertEquals(
            ConflictDecision.Rename("folder (2)"),
            ConflictResolver.decide(ConflictPolicy.KEEP_BOTH, ConflictKind.SAME_TYPE, "folder (2)")
        )
        assertEquals(ConflictDecision.Skip, ConflictResolver.decide(ConflictPolicy.REPLACE, ConflictKind.SAME_FILE))
        assertTrue(
            ConflictResolver.decide(ConflictPolicy.REPLACE, ConflictKind.DIRECTORY_REPLACES_FILE) is
                ConflictDecision.Reject
        )
    }

    @Test
    fun `resume checkpoints reject negative progress and oversized tokens`() {
        assertThrows(IllegalArgumentException::class.java) { ResumeCheckpoint(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { ResumeCheckpoint(0, 0, "x".repeat(ResumeCheckpoint.MAX_TOKEN_LENGTH + 1)) }
    }

    @Test
    fun `metadata loss is explicit and duplicate attributes are rejected`() {
        val report = MetadataPreservationReport(listOf(
            MetadataPreservation(MetadataAttribute.MODIFIED_TIME, PreservationStatus.PRESERVED),
            MetadataPreservation(MetadataAttribute.ACL, PreservationStatus.UNSUPPORTED, "Destination has no ACL support")
        ))
        assertFalse(report.isComplete)
        assertEquals(listOf(MetadataAttribute.ACL), report.warnings.map { it.attribute })
        assertThrows(IllegalArgumentException::class.java) {
            MetadataPreservationReport(listOf(
                MetadataPreservation(MetadataAttribute.ACL, PreservationStatus.PRESERVED),
                MetadataPreservation(MetadataAttribute.ACL, PreservationStatus.FAILED)
            ))
        }
    }

    @Test
    fun `sync planner forbids unsafe ambiguous plans`() {
        assertThrows(IllegalArgumentException::class.java) {
            SyncPlan("local", "local", SyncDirection.PUSH, ConflictPolicy.ASK)
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncPlan("local", "remote", SyncDirection.TWO_WAY, ConflictPolicy.ASK, deleteExtraneous = true)
        }
    }

    @Test
    fun `cancellation contract is deterministic`() {
        assertThrows(OperationCancelledException::class.java) { OperationCancellation { true }.throwIfCancelled() }
        OperationCancellation.NONE.throwIfCancelled()
    }

    @Test
    fun `partial results cannot claim impossible progress`() {
        assertThrows(IllegalArgumentException::class.java) {
            OperationResult.Partial(
                completedBytes = 1,
                completedItems = 0,
                failure = FileOperationFailure(FileOperationFailureKind.INTERRUPTED, mutationStarted = true),
                checkpoint = ResumeCheckpoint(2, 0),
                destinationMayExist = true
            )
        }
    }
}
