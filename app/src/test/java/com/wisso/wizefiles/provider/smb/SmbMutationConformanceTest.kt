package com.wisso.wizefiles.provider.smb

import com.hierynomus.msdtyp.FileTime
import com.hierynomus.msfscc.FileAttributes
import com.hierynomus.msfscc.fileinformation.FileSettableInformation
import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.smb.client.FileInformation
import com.wisso.wizefiles.provider.smb.client.PathInformation
import com.wisso.wizefiles.provider.smb.client.SmbClientException
import com.wisso.wizefiles.provider.smb.client.SymbolicLinkReparseData
import java.io.InterruptedIOException
import java.net.URI
import java.nio.channels.ClosedByInterruptException
import java.nio.file.FileAlreadyExistsException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbMutationConformanceTest {
    @Test fun `preexisting interruption stops before SMB requests and preserves state`() {
        val operations = FakeOperations()
        Thread.currentThread().interrupt()
        try {
            assertThrows(ClosedByInterruptException::class.java) {
                SmbCopyMove.copy(source, target, options(), operations)
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(operations.calls.isEmpty())
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun `target conflict stops before content mutation`() {
        val operations = FakeOperations().apply {
            information[target.key] = regularInformation(fileId = 2)
        }
        assertThrows(FileAlreadyExistsException::class.java) {
            SmbCopyMove.copy(source, target, options(), operations)
        }
        assertFalse(operations.calls.any { it.startsWith("copy:") })
    }

    @Test fun `copy interruption remains an interruption and leaves source intact`() {
        val operations = FakeOperations().apply {
            copyFailure = SmbClientException(InterruptedIOException("cancelled"))
        }
        assertThrows(InterruptedIOException::class.java) {
            SmbCopyMove.copy(source, target, options(), operations)
        }
        assertTrue(operations.information.containsKey(source.key))
        assertFalse(operations.information.containsKey(target.key))
    }

    @Test fun `safe rename failure falls back to copy then source delete`() {
        val operations = FakeOperations().apply {
            failures["rename"] = SmbClientException("rename unsupported")
        }
        SmbCopyMove.move(source, target, options(), operations)
        assertFalse(operations.information.containsKey(source.key))
        assertTrue(operations.information.containsKey(target.key))
        assertTrue(operations.calls.indexOf("copy:${source.key}->${target.key}") <
            operations.calls.lastIndexOf("delete:${source.key}"))
    }

    @Test fun `atomic rename failure never falls back`() {
        val operations = FakeOperations().apply {
            failures["rename"] = SmbClientException("rename unsupported")
        }
        assertThrows(java.io.IOException::class.java) {
            SmbCopyMove.move(source, target, options(atomic = true), operations)
        }
        assertTrue(operations.information.containsKey(source.key))
        assertFalse(operations.calls.any { it.startsWith("copy:") })
    }

    @Test fun `failed source deletion removes fallback target and keeps source`() {
        val operations = FakeOperations().apply {
            failures["rename"] = SmbClientException("rename unsupported")
            failures["delete:${source.key}"] = SmbClientException("delete denied")
        }
        assertThrows(java.io.IOException::class.java) {
            SmbCopyMove.move(source, target, options(), operations)
        }
        assertTrue(operations.information.containsKey(source.key))
        assertFalse(operations.information.containsKey(target.key))
        assertEquals(1, operations.calls.count { it == "delete:${target.key}" })
    }

    private fun options(atomic: Boolean = false) = CopyOptions(
        replaceExisting = false,
        copyAttributes = true,
        atomicMove = atomic,
        noFollowLinks = true,
        progressIntervalMillis = 0,
        progressListener = null
    )

    private class FakeOperations : SmbMutationOperations {
        val information = mutableMapOf(source.key to regularInformation(fileId = 1))
        val calls = mutableListOf<String>()
        val failures = mutableMapOf<String, SmbClientException>()
        var copyFailure: SmbClientException? = null

        override fun getPathInformation(
            path: SmbPath,
            openReparsePoint: Boolean
        ): PathInformation? {
            calls += "info:${path.key}"
            failures["info:${path.key}"]?.let { throw it }
            return information[path.key]
        }

        override fun delete(path: SmbPath) {
            calls += "delete:${path.key}"
            failures["delete:${path.key}"]?.let { throw it }
            information.remove(path.key)
        }

        override fun copyFile(
            source: SmbPath,
            target: SmbPath,
            copyAttributes: Boolean,
            openReparsePoint: Boolean,
            intervalMillis: Long,
            listener: ((Long) -> Unit)?
        ) {
            calls += "copy:${source.key}->${target.key}"
            copyFailure?.let { throw it }
            information[target.key] = regularInformation(fileId = 2)
        }

        override fun createDirectory(path: SmbPath, fileAttributes: Set<FileAttributes>) {
            calls += "mkdir:${path.key}"
            information[path.key] = regularInformation(fileId = 2, directory = true)
        }

        override fun readSymbolicLink(path: SmbPath) =
            SymbolicLinkReparseData("target", "target", true)

        override fun createSymbolicLink(
            path: SmbPath,
            reparseData: SymbolicLinkReparseData,
            fileAttributes: Set<FileAttributes>
        ) {
            information[path.key] = regularInformation(fileId = 2)
        }

        override fun setFileInformation(
            path: SmbPath,
            openReparsePoint: Boolean,
            fileInformation: FileSettableInformation
        ) {
            calls += "metadata:${path.key}"
            failures["metadata"]?.let { throw it }
        }

        override fun rename(source: SmbPath, target: SmbPath) {
            calls += "rename"
            failures["rename"]?.let { throw it }
            information[target.key] = information.remove(source.key)
                ?: throw SmbClientException("missing source")
        }
    }

    companion object {
        private val source =
            SmbFileSystemProvider.getPath(URI.create("smb://user@example.com/share/source"))
                as SmbPath
        private val target =
            SmbFileSystemProvider.getPath(URI.create("smb://user@example.com/share/target"))
                as SmbPath

        private val SmbPath.key: String
            get() = toAbsolutePath().normalize().toString()

        private fun regularInformation(fileId: Long, directory: Boolean = false) =
            FileInformation(
                creationTime = FileTime(0),
                lastAccessTime = FileTime(0),
                lastWriteTime = FileTime(0),
                changeTime = FileTime(0),
                endOfFile = 7,
                fileAttributes = if (directory) {
                    FileAttributes.FILE_ATTRIBUTE_DIRECTORY.value
                } else {
                    FileAttributes.FILE_ATTRIBUTE_NORMAL.value
                },
                fileId = fileId
            )
    }
}
