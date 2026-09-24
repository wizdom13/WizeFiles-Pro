// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp

import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.ftp.client.Authority
import com.wisso.wizefiles.provider.ftp.client.Mode
import com.wisso.wizefiles.provider.ftp.client.NegativeReplyCodeException
import com.wisso.wizefiles.provider.ftp.client.Protocol
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.PreservationStatus
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderJvmFailureClassifier
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.net.SocketTimeoutException
import java.nio.channels.ClosedByInterruptException
import java.nio.file.FileAlreadyExistsException
import java.time.Instant
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FtpMutationConformanceTest {
    @Test fun `preexisting interruption stops before requests and preserves state`() {
        val operations = FakeOperations()
        Thread.currentThread().interrupt()
        try {
            assertThrows(ClosedByInterruptException::class.java) {
                FtpCopyMove.copy(source, target, options(), operations)
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(operations.calls.isEmpty())
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun `stale source and target conflict stop before opening output`() {
        val stale = FakeOperations().apply { files.remove("source") }
        assertThrows(java.nio.file.NoSuchFileException::class.java) {
            FtpCopyMove.copy(source, target, options(), stale)
        }
        assertFalse(stale.calls.any { it == "store:target" })

        val conflict = FakeOperations().apply {
            files["target"] = regular("target", 3)
            data["target"] = "old".toByteArray()
        }
        assertThrows(FileAlreadyExistsException::class.java) {
            FtpCopyMove.copy(source, target, options(), conflict)
        }
        assertFalse(conflict.calls.any { it == "store:target" })
    }

    @Test fun `partial timeout cleans target closes streams and preserves source`() {
        val operations = FakeOperations().apply {
            writeFailure = SocketTimeoutException("timed out")
        }
        val failure = assertThrows(IOException::class.java) {
            FtpCopyMove.copy(source, target, options(), operations)
        }
        assertEquals(ProviderFailureSignal.TIMEOUT, ProviderJvmFailureClassifier.classify(failure))
        assertFalse(operations.data.containsKey("target"))
        assertTrue(operations.inputClosed)
        assertTrue(operations.outputClosed)
        assertTrue(operations.data.containsKey("source"))
    }

    @Test fun `cleanup failure is suppressed on primary copy failure`() {
        val operations = FakeOperations().apply {
            writeFailure = SocketTimeoutException("timed out")
            failures["delete:target"] = IOException("cleanup denied")
        }
        val failure = assertThrows(IOException::class.java) {
            FtpCopyMove.copy(source, target, options(), operations)
        }
        assertEquals(ProviderFailureSignal.TIMEOUT, ProviderJvmFailureClassifier.classify(failure))
        assertEquals(1, failure.suppressed.size)
    }

    @Test fun `replace existing deletes target before output opens`() {
        val operations = FakeOperations().apply {
            files["target"] = regular("target", 3)
            data["target"] = "old".toByteArray()
        }
        FtpCopyMove.copy(source, target, options(replace = true), operations)
        assertArrayEquals(CONTENT, operations.data.getValue("target"))
        assertTrue(operations.calls.indexOf("delete:target") < operations.calls.indexOf("store:target"))
    }

    @Test fun `safe rename failure falls back to copy then source delete`() {
        val operations = FakeOperations().apply {
            failures["rename"] = IOException("rename unsupported")
        }
        FtpCopyMove.move(source, target, options(), operations)
        assertFalse(operations.data.containsKey("source"))
        assertArrayEquals(CONTENT, operations.data.getValue("target"))
        assertTrue(operations.calls.indexOf("store:target") < operations.calls.lastIndexOf("delete:source"))
    }

    @Test fun `atomic rename failure never falls back`() {
        val operations = FakeOperations().apply {
            failures["rename"] = IOException("rename unsupported")
        }
        assertThrows(IOException::class.java) {
            FtpCopyMove.move(source, target, options(atomic = true), operations)
        }
        assertTrue(operations.data.containsKey("source"))
        assertFalse(operations.calls.any { it == "store:target" })
    }

    @Test fun `metadata report is bounded and records unsupported FTP attributes`() {
        val report = FtpMetadataPreservationPolicy.report(true, "x".repeat(2_000))
        assertFalse(report.isComplete)
        assertEquals(
            PreservationStatus.FAILED,
            report.entries.single { it.attribute == MetadataAttribute.MODIFIED_TIME }.status
        )
        assertEquals(
            PreservationStatus.UNSUPPORTED,
            report.entries.single { it.attribute == MetadataAttribute.POSIX_PERMISSIONS }.status
        )
        assertTrue(report.entries.mapNotNull { it.detail }.all {
            it.length <= com.wisso.wizefiles.storage.MetadataPreservation.MAX_DETAIL_LENGTH
        })
    }

    private fun options(replace: Boolean = false, atomic: Boolean = false) = CopyOptions(
        replace,
        copyAttributes = true,
        atomicMove = atomic,
        noFollowLinks = true,
        progressIntervalMillis = 0,
        progressListener = null
    )

    private class FakeOperations : FtpOperations {
        val files = mutableMapOf("source" to regular("source", CONTENT.size.toLong()))
        val data = mutableMapOf("source" to CONTENT.copyOf())
        val calls = mutableListOf<String>()
        val failures = mutableMapOf<String, IOException>()
        var writeFailure: IOException? = null
        var inputClosed = false
        var outputClosed = false

        override fun listFile(path: FtpPath, noFollowLinks: Boolean): FTPFile {
            val key = path.fileName.toString()
            calls += "list:$key"
            failures["list:$key"]?.let { throw it }
            return files[key] ?: throw NegativeReplyCodeException(
                FTPReply.FILE_UNAVAILABLE,
                "missing"
            )
        }

        override fun listFileOrNull(path: FtpPath, noFollowLinks: Boolean): FTPFile? {
            val key = path.fileName.toString()
            calls += "listOrNull:$key"
            failures["listOrNull:$key"]?.let { throw it }
            return files[key]
        }

        override fun delete(path: FtpPath, isDirectory: Boolean) {
            val key = path.fileName.toString()
            calls += "delete:$key"
            failures["delete:$key"]?.let { throw it }
            files.remove(key)
            data.remove(key)
        }

        override fun createDirectory(path: FtpPath) {
            val key = path.fileName.toString()
            calls += "mkdir:$key"
            files[key] = FTPFile().apply {
                name = key
                type = FTPFile.DIRECTORY_TYPE
            }
        }

        override fun retrieveFile(path: FtpPath): InputStream {
            val key = path.fileName.toString()
            calls += "retrieve:$key"
            val delegate = ByteArrayInputStream(data.getValue(key))
            return object : InputStream() {
                override fun read(): Int = delegate.read()
                override fun read(buffer: ByteArray, offset: Int, length: Int): Int =
                    delegate.read(buffer, offset, length)
                override fun close() {
                    inputClosed = true
                    delegate.close()
                }
            }
        }

        override fun storeFile(path: FtpPath): OutputStream {
            val key = path.fileName.toString()
            calls += "store:$key"
            files[key] = regular(key, 0)
            val delegate = ByteArrayOutputStream()
            return object : OutputStream() {
                override fun write(value: Int) {
                    writeFailure?.let { throw it }
                    delegate.write(value)
                    data[key] = delegate.toByteArray()
                }
                override fun write(buffer: ByteArray, offset: Int, length: Int) {
                    writeFailure?.let { throw it }
                    delegate.write(buffer, offset, length)
                    data[key] = delegate.toByteArray()
                }
                override fun close() {
                    outputClosed = true
                    delegate.close()
                }
            }
        }

        override fun renameFile(source: FtpPath, target: FtpPath) {
            calls += "rename"
            failures["rename"]?.let { throw it }
            val sourceKey = source.fileName.toString()
            val targetKey = target.fileName.toString()
            files[targetKey] = files.remove(sourceKey) ?: throw IOException("missing source")
            data[targetKey] = data.remove(sourceKey) ?: ByteArray(0)
        }

        override fun setLastModifiedTime(path: FtpPath, instant: Instant) {
            calls += "setTime"
            failures["setTime"]?.let { throw it }
        }
    }

    companion object {
        private val CONTENT = "content".toByteArray()
        private val authority = Authority(
            protocol = Protocol.FTP,
            host = "example.com",
            port = 21,
            username = "user",
            mode = Mode.PASSIVE,
            encoding = Authority.DEFAULT_ENCODING
        )
        private val fileSystem = FtpFileSystemProvider.getOrNewFileSystem(authority)
        private val source = fileSystem.getPath("/source") as FtpPath
        private val target = fileSystem.getPath("/target") as FtpPath

        private fun regular(name: String, size: Long) = FTPFile().apply {
            this.name = name
            type = FTPFile.FILE_TYPE
            this.size = size
        }
    }
}
