package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import com.wisso.wizefiles.provider.common.InvalidFileNameException
import org.junit.Assert.assertEquals
import org.junit.Test

class ArchiveExtractOperationPolicyTest {

    @Test
    fun archiveValidationFailureAbortsWithoutRetry() {
        val decision = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.EXTRACT,
                sourcePath = "/archive.zip",
                targetPath = "/extract",
                phase = FileOperationPhase.PLAN,
                exception = InvalidFileNameException("Unsafe archive entry name"),
                retryCount = 0,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = false,
                userCancelled = false,
                allowSkip = false
            )
        )
        assertEquals(FileOperationFailureDecision.Abort, decision)
    }

    @Test
    fun zipTraversalLikeFailureWithPartialOutputRollsBackAndAborts() {
        val decision = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.EXTRACT,
                sourcePath = "/archive.zip",
                targetPath = "/extract",
                phase = FileOperationPhase.CLEANUP,
                exception = IOException("Unsafe archive entry traversal"),
                retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = true,
                userCancelled = false,
                allowSkip = false
            )
        )
        assertEquals(FileOperationFailureDecision.RollbackAndAbort, decision)
    }
}
