package com.wisso.wizefiles.feature.filejobs

import com.wisso.wizefiles.storage.FileOperationFailureKind
import com.wisso.wizefiles.storage.RetryClassification
import java.io.FileNotFoundException
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketTimeoutException
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderFailureBoundaryTest {
    @Test fun `common provider failures use shared classifications`() {
        val cases = listOf(
            SocketTimeoutException() to FileOperationFailureKind.TIMEOUT,
            AccessDeniedException("x") to FileOperationFailureKind.PERMISSION_REVOKED,
            FileAlreadyExistsException("x") to FileOperationFailureKind.CONFLICT,
            FileNotFoundException("x") to FileOperationFailureKind.STALE_RESOURCE,
            InterruptedIOException() to FileOperationFailureKind.INTERRUPTED
        )
        cases.forEach { (exception, kind) -> assertEquals(kind, ProviderFailureBoundary.map(exception).kind) }
    }

    @Test fun `cyclic cause chain is bounded and diagnostics are bounded`() {
        val first = IOException("x".repeat(5000))
        val second = IOException("second")
        first.initCause(second)
        second.initCause(first)
        val mapped = ProviderFailureBoundary.map(first, mutationStarted = true)
        assertEquals(RetryClassification.NEVER, mapped.retryClassification)
        assertTrue(mapped.mutationStarted)
        assertEquals(1024, mapped.message?.length)
    }
}
