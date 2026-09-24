package com.wisso.wizefiles.feature.filejobs

import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Test

class FileOperationCleanupPolicyTest {

    @Test
    fun partialOutputTriggersRollbackAndAbort() {
        val decision = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.MOVE,
                sourcePath = "/source",
                targetPath = "/target",
                phase = FileOperationPhase.CLEANUP,
                exception = IOException("failed after partial output"),
                retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = true,
                userCancelled = false,
                allowSkip = false
            )
        )
        assertEquals(FileOperationFailureDecision.RollbackAndAbort, decision)
    }

    @Test
    fun moveCopyFailureKeepsSourceByAbortingWithoutSuccessState() {
        val decision = FileOperationStatePolicy.decide(
            FileOperationFailureContext(
                type = FileOperationType.MOVE,
                sourcePath = "/source",
                targetPath = "/target",
                phase = FileOperationPhase.EXECUTE,
                exception = IOException("copy failed"),
                retryCount = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                maxRetries = FileOperationStatePolicy.MAX_RETRY_ATTEMPTS,
                hasPartialOutput = false,
                userCancelled = false,
                allowSkip = false
            )
        )
        assertEquals(FileOperationFailureDecision.Abort, decision)
    }
}
