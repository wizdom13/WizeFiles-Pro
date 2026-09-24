package com.wisso.wizefiles.storage.local

import com.wisso.wizefiles.storage.FileOperationFailureKind
import com.wisso.wizefiles.storage.FileOperationRequest
import com.wisso.wizefiles.storage.OperationCancellation
import com.wisso.wizefiles.storage.OperationCancelledException
import com.wisso.wizefiles.storage.OperationResult
import com.wisso.wizefiles.storage.PreservationStatus
import com.wisso.wizefiles.storage.ProviderOperationRunner
import com.wisso.wizefiles.storage.RetryClassification
import java.io.IOException
import java.io.InterruptedIOException
import java.nio.file.AccessDeniedException
import java.nio.file.CopyOption
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.NoSuchFileException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class LocalProviderMutationPortTest {
    private val source = LocalPathNode(Paths.get("/source"))
    private val target = LocalPathNode(Paths.get("/target"))

    @Test fun `real adapter copy reports bytes and requested metadata`() {
        val operations = FakeOperations()
        val result = ProviderOperationRunner.run(
            LocalProviderMutationPort(operations, LocalMutationOptions(copyAttributes = true)),
            FileOperationRequest.Copy(source, target)
        ) as OperationResult.Complete
        assertEquals(12L, result.completedBytes)
        assertTrue(operations.copied)
        assertTrue(result.metadata.entries.all { it.status == PreservationStatus.PRESERVED })
    }

    @Test fun `cancellation before mutation never calls local filesystem`() {
        val operations = FakeOperations()
        assertThrows(OperationCancelledException::class.java) {
            ProviderOperationRunner.run(
                LocalProviderMutationPort(operations),
                FileOperationRequest.Move(source, target),
                cancellation = OperationCancellation { true }
            )
        }
        assertFalse(operations.moved)
    }

    @Test fun `cancellation after metadata lookup prevents the mutation`() {
        val operations = FakeOperations()
        var checks = 0
        val result = ProviderOperationRunner.run(
            LocalProviderMutationPort(operations),
            FileOperationRequest.Copy(source, target),
            cancellation = OperationCancellation { ++checks >= 3 }
        ) as OperationResult.Partial

        assertEquals(FileOperationFailureKind.INTERRUPTED, result.failure.kind)
        assertFalse(operations.copied)
    }

    @Test fun `legacy wrapper translates cancellation without stat or mutation`() {
        Thread.currentThread().interrupt()
        try {
            assertThrows(InterruptedIOException::class.java) {
                runLocalMutation(FileOperationRequest.Delete(source), cancellation = threadInterruptionCancellation)
            }
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun `delete does not require a metadata lookup`() {
        val operations = FakeOperations(sizeFailure = AccessDeniedException("/source"))
        val result = ProviderOperationRunner.run(
            LocalProviderMutationPort(operations),
            FileOperationRequest.Delete(source)
        ) as OperationResult.Complete
        assertEquals(0L, result.completedBytes)
        assertTrue(operations.deleted)
    }

    @Test fun `local permission and conflict failures require user action`() {
        listOf(AccessDeniedException("/target"), FileAlreadyExistsException("/target")).forEach { failure ->
            val result = ProviderOperationRunner.run(
                LocalProviderMutationPort(FakeOperations(failure)),
                FileOperationRequest.Copy(source, target)
            ) as OperationResult.Partial
            assertEquals(RetryClassification.REQUIRES_USER_ACTION, result.failure.retryClassification)
        }
    }

    @Test fun `local timeout is transient and destination remains explicit`() {
        val port = LocalProviderMutationPort(FakeOperations(java.net.SocketTimeoutException("timed out")))
        val result = ProviderOperationRunner.run(
            port,
            FileOperationRequest.Copy(source, target)
        ) as OperationResult.Partial
        assertEquals(RetryClassification.TRANSIENT, result.failure.retryClassification)
        assertTrue(result.destinationMayExist)
        assertTrue(port.isClosed)
    }

    @Test fun `disk full and stale local resources have common classifications`() {
        val diskFull = ProviderOperationRunner.run(
            LocalProviderMutationPort(FakeOperations(IOException("No space left on device"))),
            FileOperationRequest.Copy(source, target)
        ) as OperationResult.Partial
        assertEquals(RetryClassification.NEVER, diskFull.failure.retryClassification)
        assertTrue(diskFull.destinationMayExist)

        val stale = ProviderOperationRunner.run(
            LocalProviderMutationPort(FakeOperations(NoSuchFileException("/source"))),
            FileOperationRequest.Move(source, target)
        ) as OperationResult.Partial
        assertEquals(RetryClassification.REQUIRES_USER_ACTION, stale.failure.retryClassification)
        assertFalse(stale.destinationMayExist)
    }

    private class FakeOperations(
        private val failure: IOException? = null,
        private val sizeFailure: IOException? = null
    ) : LocalNioOperations {
        var copied = false
        var moved = false
        var deleted = false
        override fun size(path: Path): Long {
            sizeFailure?.let { throw it }
            return 12
        }
        override fun copy(source: Path, target: Path, vararg options: CopyOption) {
            failure?.let { throw it }
            copied = true
        }
        override fun move(source: Path, target: Path, vararg options: CopyOption) {
            failure?.let { throw it }
            moved = true
        }
        override fun delete(path: Path) {
            failure?.let { throw it }
            deleted = true
        }
    }
}
