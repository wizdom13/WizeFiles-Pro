// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filejobs

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException
import java.nio.file.AccessDeniedException

class OperationStatePolicyTest {

    @Test
    fun phasesAndDecisionsAreExplicitlyRepresented() {
        assertTrue(FileOperationPhase.values().contains(FileOperationPhase.SCAN))
        assertTrue(FileOperationPhase.values().contains(FileOperationPhase.CLEANUP))
        assertTrue(FileOperationPhase.values().contains(FileOperationPhase.ROLLBACK))
        val decisions = listOf(
            FileOperationFailureDecision.Retry,
            FileOperationFailureDecision.Skip,
            FileOperationFailureDecision.Abort,
            FileOperationFailureDecision.RollbackAndAbort
        )
        assertEquals(4, decisions.distinct().size)
    }

    @Test
    fun permissionFailuresAreCategorizedForHandledWarningLogging() {
        assertEquals(
            FileOperationFailureCategory.PERMISSION,
            FileOperationStatePolicy.categorize(AccessDeniedException("/remote/read-only"))
        )
    }

    @Test
    fun socketTimeoutIsTransientRatherThanCancellation() {
        assertEquals(
            FileOperationFailureCategory.TRANSIENT,
            FileOperationStatePolicy.categorize(
                SocketTimeoutException("FTP data connection timed out")
            )
        )
    }

    @Test
    fun wrappedProviderTimeoutIsTransient() {
        val exception = IOException(
            "Provider operation failed",
            RuntimeException(SocketTimeoutException("data connection timed out"))
        )

        assertEquals(
            FileOperationFailureCategory.TRANSIENT,
            FileOperationStatePolicy.categorize(exception)
        )
    }

    @Test
    fun wrappedAccessDeniedIsPermissionFailure() {
        val exception = IOException(
            "Provider operation failed",
            RuntimeException(AccessDeniedException("/remote/read-only"))
        )

        assertEquals(
            FileOperationFailureCategory.PERMISSION,
            FileOperationStatePolicy.categorize(exception)
        )
    }

    @Test
    fun retryCountIsBounded() {
        assertTrue(FileOperationStatePolicy.MAX_RETRY_ATTEMPTS > 0)
        assertTrue(FileOperationStatePolicy.MAX_RETRY_ATTEMPTS <= 3)
    }
}
