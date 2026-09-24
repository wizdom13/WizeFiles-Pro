package com.wisso.wizefiles.provider.sftp

import com.wisso.wizefiles.provider.common.CopyOptions
import com.wisso.wizefiles.provider.sftp.client.SftpClientException
import com.wisso.wizefiles.storage.MetadataAttribute
import com.wisso.wizefiles.storage.MetadataPreservation
import com.wisso.wizefiles.storage.PreservationStatus
import com.wisso.wizefiles.storage.ProviderFailureSignal
import com.wisso.wizefiles.storage.ProviderJvmFailureClassifier
import java.io.IOException
import java.io.InterruptedIOException
import java.net.SocketException
import java.net.SocketTimeoutException
import java.net.URI
import java.nio.ByteBuffer
import java.nio.channels.SeekableByteChannel
import java.nio.file.AccessDeniedException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.NoSuchFileException
import net.schmizz.sshj.sftp.FileAttributes
import net.schmizz.sshj.sftp.FileMode
import net.schmizz.sshj.sftp.OpenMode
import net.schmizz.sshj.sftp.Response
import net.schmizz.sshj.sftp.SFTPException
import net.schmizz.sshj.userauth.UserAuthException
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class SftpMutationConformanceTest {
    @Test fun `preexisting interruption stops before mutation and preserves interrupt state`() {
        val operations = FakeOperations()
        Thread.currentThread().interrupt()
        try {
            assertThrows(java.nio.channels.ClosedByInterruptException::class.java) {
                SftpCopyMove.copy(source, target, options(), operations)
            }
            assertTrue(Thread.currentThread().isInterrupted)
            assertTrue(operations.calls.isEmpty())
        } finally {
            Thread.interrupted()
        }
    }

    @Test fun `protocol status mapping remains specific through bounded wrapped causes`() {
        val cases = mapOf(
            Response.StatusCode.NO_SUCH_FILE to ProviderFailureSignal.STALE_RESOURCE,
            Response.StatusCode.NO_SUCH_PATH to ProviderFailureSignal.STALE_RESOURCE,
            Response.StatusCode.DELETE_PENDING to ProviderFailureSignal.STALE_RESOURCE,
            Response.StatusCode.PERMISSION_DENIED to ProviderFailureSignal.PERMISSION_REVOKED,
            Response.StatusCode.CANNOT_DELETE to ProviderFailureSignal.PERMISSION_REVOKED,
            Response.StatusCode.FILE_ALREADY_EXISTS to ProviderFailureSignal.CONFLICT,
            Response.StatusCode.WRITE_PROTECT to ProviderFailureSignal.READ_ONLY,
            Response.StatusCode.NO_SPACE_ON_FILESYSTEM to ProviderFailureSignal.DISK_FULL,
            Response.StatusCode.QUOTA_EXCEEDED to ProviderFailureSignal.DISK_FULL,
            Response.StatusCode.NO_CONNECTION to ProviderFailureSignal.UNAVAILABLE,
            Response.StatusCode.CONNECITON_LOST to ProviderFailureSignal.UNAVAILABLE,
            Response.StatusCode.BAD_MESSAGE to ProviderFailureSignal.MALFORMED_RESPONSE
        )
        cases.forEach { (status, expected) ->
            val wrapped = SftpClientException(IOException("wrapper", sftp(status)))
            assertEquals(status.toString(), expected, wrapped.providerFailureSignal)
            assertEquals(expected, ProviderJvmFailureClassifier.classify(wrapped.toFileSystemException("file")))
        }
        var cause: Throwable = sftp(Response.StatusCode.PERMISSION_DENIED)
        repeat(9) { cause = IOException("layer", cause) }
        assertEquals(ProviderFailureSignal.PERMANENT, SftpClientException(cause).providerFailureSignal)

        val diagnostic = SftpClientException("password=hunter2 " + "x".repeat(2_000))
            .toFileSystemException("file").reason!!
        assertFalse(diagnostic.contains("hunter2"))
        assertTrue(diagnostic.length <= 1_024)
    }

    @Test fun `timeouts interruption transport loss and capacity remain distinct`() {
        assertSignal(SocketTimeoutException("timeout"), ProviderFailureSignal.TIMEOUT)
        assertSignal(InterruptedIOException("cancelled"), ProviderFailureSignal.INTERRUPTED)
        assertSignal(SocketException("connection reset"), ProviderFailureSignal.UNAVAILABLE)
        assertSignal(IOException("remote disk full"), ProviderFailureSignal.DISK_FULL)
        assertSignal(UserAuthException("authentication rejected"), ProviderFailureSignal.PERMISSION_REVOKED)
    }

    @Test fun `conflict permission and stale source stop before opening output`() {
        listOf(
            sftp(Response.StatusCode.FILE_ALREADY_EXISTS) to FileAlreadyExistsException::class.java,
            sftp(Response.StatusCode.PERMISSION_DENIED) to AccessDeniedException::class.java,
            sftp(Response.StatusCode.NO_SUCH_FILE) to NoSuchFileException::class.java
        ).forEach { (fault, expected) ->
            val operations = FakeOperations().apply { failures["lstat:source"] = fault }
            assertThrows(expected) { SftpCopyMove.copy(source, target, options(), operations) }
            assertFalse(operations.calls.any { it.startsWith("open:target") })
            assertTrue(operations.data.containsKey("source"))
        }
    }

    @Test fun `partial write interruption and output close failure clean target and close channels`() {
        listOf<IOException>(
            InterruptedIOException("cancelled"),
            SocketTimeoutException("timed out"),
            sftp(Response.StatusCode.NO_SPACE_ON_FILESYSTEM),
            IOException("output close failed"),
            IOException("input close failed")
        ).forEachIndexed { index, failure ->
            val operations = FakeOperations().apply {
                when (index) {
                    3 -> outputCloseFailure = failure
                    4 -> inputCloseFailure = failure
                    else -> writeFailure = failure
                }
            }
            val exception = assertThrows(IOException::class.java) {
                SftpCopyMove.copy(source, target, options(), operations)
            }
            assertFalse(operations.data.containsKey("target"))
            assertTrue(operations.inputClosed)
            assertTrue(operations.outputClosed)
            assertTrue(operations.data.containsKey("source"))
            val expected = when (index) {
                0 -> ProviderFailureSignal.INTERRUPTED
                1 -> ProviderFailureSignal.TIMEOUT
                2 -> ProviderFailureSignal.DISK_FULL
                else -> ProviderFailureSignal.PERMANENT
            }
            assertEquals(expected, ProviderJvmFailureClassifier.classify(exception))
        }
    }

    @Test fun `cleanup failure is suppressed on primary copy failure`() {
        val operations = FakeOperations().apply {
            writeFailure = SocketTimeoutException("write timeout")
            failures["remove:target"] = sftp(Response.StatusCode.PERMISSION_DENIED)
        }
        val failure = assertThrows(IOException::class.java) {
            SftpCopyMove.copy(source, target, options(), operations)
        }
        assertEquals(ProviderFailureSignal.TIMEOUT, ProviderJvmFailureClassifier.classify(failure))
        assertEquals(1, failure.suppressed.size)
        assertTrue(operations.data.containsKey("source"))
    }

    @Test fun `open race conflict and replace existing behavior remain deterministic`() {
        val raced = FakeOperations().apply {
            failures["open:target"] = sftp(Response.StatusCode.FILE_ALREADY_EXISTS)
        }
        assertThrows(FileAlreadyExistsException::class.java) {
            SftpCopyMove.copy(source, target, options(), raced)
        }
        assertTrue(raced.data.containsKey("source"))
        assertFalse(raced.calls.contains("remove:target"))

        val replacing = FakeOperations().apply {
            attrs["target"] = attributes(FileMode.Type.REGULAR)
            data["target"] = "old".toByteArray()
        }
        SftpCopyMove.copy(source, target, options(replace = true), replacing)
        assertArrayEquals(CONTENT, replacing.data.getValue("target"))
        assertTrue(replacing.calls.indexOf("remove:target") < replacing.calls.indexOf("open:target"))
    }

    @Test fun `rename success performs no fallback mutations`() {
        val operations = FakeOperations()
        SftpCopyMove.move(source, target, options(), operations)
        assertFalse(operations.data.containsKey("source"))
        assertArrayEquals(CONTENT, operations.data.getValue("target"))
        assertFalse(operations.calls.any { it.startsWith("open:") })
    }

    @Test fun `safe rename failure falls back to copy then source delete`() {
        val operations = FakeOperations().apply {
            failures["rename"] = sftp(Response.StatusCode.FAILURE)
        }
        var metadataReported = false
        SftpCopyMove.move(
            source,
            target,
            options(metadata = { metadataReported = true }),
            operations
        )
        assertFalse(operations.data.containsKey("source"))
        assertArrayEquals(CONTENT, operations.data.getValue("target"))
        assertTrue(operations.calls.indexOf("open:target") < operations.calls.indexOf("remove:source"))
        assertTrue(metadataReported)
    }

    @Test fun `atomic rename failure never falls back`() {
        val operations = FakeOperations().apply { failures["rename"] = sftp(Response.StatusCode.FAILURE) }
        assertThrows(IOException::class.java) {
            SftpCopyMove.move(source, target, options(atomic = true), operations)
        }
        assertTrue(operations.data.containsKey("source"))
        assertFalse(operations.data.containsKey("target"))
        assertFalse(operations.calls.any { it.startsWith("open:") })
    }

    @Test fun `permission timeout conflict and unavailable rename failures do not trigger fallback`() {
        listOf<IOException>(
            sftp(Response.StatusCode.PERMISSION_DENIED),
            SocketTimeoutException("timeout"),
            sftp(Response.StatusCode.FILE_ALREADY_EXISTS),
            SocketException("connection reset")
        ).forEach { fault ->
            val operations = FakeOperations().apply { failures["rename"] = fault }
            assertThrows(IOException::class.java) { SftpCopyMove.move(source, target, options(), operations) }
            assertTrue(operations.data.containsKey("source"))
            assertFalse(operations.calls.any { it.startsWith("open:") })
        }
    }

    @Test fun `fallback copy failure preserves source and removes partial target`() {
        val operations = FakeOperations().apply {
            failures["rename"] = sftp(Response.StatusCode.FAILURE)
            writeFailure = SocketTimeoutException("timeout")
        }
        assertThrows(IOException::class.java) { SftpCopyMove.move(source, target, options(), operations) }
        assertTrue(operations.data.containsKey("source"))
        assertFalse(operations.data.containsKey("target"))
        assertFalse("source must not be deleted", operations.calls.contains("remove:source"))
    }

    @Test fun `source delete failure rolls back target and retains primary failure`() {
        val operations = FakeOperations().apply {
            failures["rename"] = sftp(Response.StatusCode.FAILURE)
            failures["remove:source"] = sftp(Response.StatusCode.CANNOT_DELETE)
        }
        val failure = assertThrows(AccessDeniedException::class.java) {
            SftpCopyMove.move(source, target, options(), operations)
        }
        assertTrue(operations.data.containsKey("source"))
        assertFalse(operations.data.containsKey("target"))
        assertEquals(ProviderFailureSignal.PERMISSION_REVOKED, ProviderJvmFailureClassifier.classify(failure))
    }

    @Test fun `rollback failure is suppressed when source delete fails`() {
        val operations = FakeOperations().apply {
            failures["rename"] = sftp(Response.StatusCode.FAILURE)
            failures["remove:source"] = sftp(Response.StatusCode.CANNOT_DELETE)
            failureQueues["remove:target"] = ArrayDeque(listOf(sftp(Response.StatusCode.PERMISSION_DENIED)))
        }
        val failure = assertThrows(AccessDeniedException::class.java) {
            SftpCopyMove.move(source, target, options(), operations)
        }
        assertEquals(1, failure.cause!!.suppressed.size)
        assertTrue(operations.data.containsKey("source"))
        assertTrue(operations.data.containsKey("target"))
    }

    @Test fun `metadata failure is a bounded warning after content success`() {
        val operations = FakeOperations().apply {
            failures["setstat"] = IOException("x".repeat(MetadataPreservation.MAX_DETAIL_LENGTH * 2))
        }
        var report = SftpMetadataPreservationPolicy.report(false, null)
        SftpCopyMove.copy(source, target, options(copyAttributes = true) { report = it }, operations)
        assertArrayEquals(CONTENT, operations.data.getValue("target"))
        assertTrue(report.warnings.any { it.attribute == MetadataAttribute.MODIFIED_TIME })
        assertTrue(report.warnings.all { it.detail!!.length <= MetadataPreservation.MAX_DETAIL_LENGTH })
    }

    @Test fun `symlink copies without following and replace conflict is deterministic`() {
        val operations = FakeOperations(sourceType = FileMode.Type.SYMLINK).apply {
            linkTargets["source"] = "relative-target"
            attrs["target"] = attributes(FileMode.Type.SYMLINK)
            linkTargets["target"] = "old"
        }
        assertThrows(FileAlreadyExistsException::class.java) {
            SftpCopyMove.copy(source, target, options(noFollow = true), operations)
        }
        SftpCopyMove.copy(source, target, options(replace = true, noFollow = true), operations)
        assertEquals("relative-target", operations.linkTargets["target"])
        assertTrue(operations.calls.contains("readlink:source"))
    }

    @Test fun `directory and symlink protocol failures do not produce false success`() {
        val directory = FakeOperations(sourceType = FileMode.Type.DIRECTORY)
        SftpCopyMove.copy(source, target, options(), directory)
        assertEquals(FileMode.Type.DIRECTORY, directory.attrs.getValue("target").type)

        val readlinkFailure = FakeOperations(sourceType = FileMode.Type.SYMLINK).apply {
            failures["readlink:source"] = sftp(Response.StatusCode.NO_SUCH_FILE)
        }
        assertThrows(NoSuchFileException::class.java) {
            SftpCopyMove.copy(source, target, options(), readlinkFailure)
        }
        assertFalse(readlinkFailure.attrs.containsKey("target"))

        val createFailure = FakeOperations(sourceType = FileMode.Type.SYMLINK).apply {
            linkTargets["source"] = "relative"
            failures["symlink:target"] = sftp(Response.StatusCode.PERMISSION_DENIED)
        }
        assertThrows(AccessDeniedException::class.java) {
            SftpCopyMove.copy(source, target, options(), createFailure)
        }
        assertFalse(createFailure.attrs.containsKey("target"))
    }

    private fun assertSignal(cause: IOException, expected: ProviderFailureSignal) {
        val exception = SftpClientException(cause).toFileSystemException("file")
        assertEquals(expected, ProviderJvmFailureClassifier.classify(exception))
    }

    private fun options(
        replace: Boolean = false,
        atomic: Boolean = false,
        noFollow: Boolean = true,
        copyAttributes: Boolean = false,
        metadata: ((com.wisso.wizefiles.storage.MetadataPreservationReport) -> Unit)? = null
    ) = CopyOptions(replace, copyAttributes, atomic, noFollow, 0, null, metadata)

    private class FakeOperations(sourceType: FileMode.Type = FileMode.Type.REGULAR) : SftpOperations {
        val attrs = mutableMapOf("source" to attributes(sourceType))
        val data = mutableMapOf("source" to CONTENT.copyOf())
        val linkTargets = mutableMapOf<String, String>()
        val calls = mutableListOf<String>()
        val failures = mutableMapOf<String, IOException>()
        val failureQueues = mutableMapOf<String, ArrayDeque<IOException>>()
        var writeFailure: IOException? = null
        var outputCloseFailure: IOException? = null
        var inputCloseFailure: IOException? = null
        var inputClosed = false
        var outputClosed = false

        override fun lstat(path: SftpPath): FileAttributes {
            call("lstat:${path.fileName}")
            return attrs[path.fileName.toString()] ?: throw SftpClientException(statusFailure(Response.StatusCode.NO_SUCH_FILE))
        }
        override fun stat(path: SftpPath): FileAttributes {
            call("stat:${path.fileName}")
            return attrs[path.fileName.toString()] ?: throw SftpClientException(statusFailure(Response.StatusCode.NO_SUCH_FILE))
        }
        override fun openByteChannel(path: SftpPath, flags: Set<OpenMode>, attributes: FileAttributes): SeekableByteChannel {
            val name = path.fileName.toString()
            call("open:$name")
            return if (OpenMode.READ in flags) MemoryChannel(
                data.getValue(name), false, closed = {
                    inputClosed = true
                    inputCloseFailure?.let { throw it }
                }
            )
            else {
                attrs[name] = attributes(FileMode.Type.REGULAR)
                data[name] = byteArrayOf()
                MemoryChannel(data[name]!!, true, { outputClosed = true }, { bytes -> data[name] = bytes })
            }
        }
        override fun mkdir(path: SftpPath, attributes: FileAttributes) {
            call("mkdir:${path.fileName}"); attrs[path.fileName.toString()] = attributes(FileMode.Type.DIRECTORY)
        }
        override fun remove(path: SftpPath) {
            val name = path.fileName.toString(); call("remove:$name")
            attrs.remove(name); data.remove(name); linkTargets.remove(name)
        }
        override fun rename(source: SftpPath, target: SftpPath) {
            call("rename")
            val sourceName = source.fileName.toString(); val targetName = target.fileName.toString()
            attrs[targetName] = attrs.remove(sourceName)!!
            data.remove(sourceName)?.let { data[targetName] = it }
            linkTargets.remove(sourceName)?.let { linkTargets[targetName] = it }
        }
        override fun readlink(path: SftpPath): String { call("readlink:${path.fileName}"); return linkTargets.getValue(path.fileName.toString()) }
        override fun symlink(link: SftpPath, target: String) {
            val name = link.fileName.toString(); call("symlink:$name")
            if (attrs.containsKey(name)) throw SftpClientException(statusFailure(Response.StatusCode.FILE_ALREADY_EXISTS))
            attrs[name] = attributes(FileMode.Type.SYMLINK); linkTargets[name] = target
        }
        override fun setstat(path: SftpPath, attributes: FileAttributes) { call("setstat") }

        private fun call(name: String) {
            calls += name
            failureQueues[name]?.removeFirstOrNull()?.let { throw SftpClientException(it) }
            failures[name]?.let { throw SftpClientException(it) }
        }

        private inner class MemoryChannel(
            initial: ByteArray,
            private val writable: Boolean,
            private val closed: () -> Unit,
            private val committed: (ByteArray) -> Unit = {}
        ) : SeekableByteChannel {
            private var bytes = initial.copyOf()
            private var position = 0
            private var open = true
            override fun read(dst: ByteBuffer): Int {
                if (position >= bytes.size) return -1
                val count = minOf(dst.remaining(), bytes.size - position)
                dst.put(bytes, position, count); position += count; return count
            }
            override fun write(src: ByteBuffer): Int {
                check(writable)
                val count = src.remaining()
                val next = bytes.copyOf(maxOf(bytes.size, position + count))
                src.get(next, position, count); position += count; bytes = next; committed(bytes)
                writeFailure?.let { throw it }
                return count
            }
            override fun position(): Long = position.toLong()
            override fun position(newPosition: Long): SeekableByteChannel { position = newPosition.toInt(); return this }
            override fun size(): Long = bytes.size.toLong()
            override fun truncate(size: Long): SeekableByteChannel { bytes = bytes.copyOf(size.toInt()); return this }
            override fun isOpen(): Boolean = open
            override fun close() {
                if (!open) return
                open = false; closed(); if (writable) outputCloseFailure?.let { throw it }
            }
        }
    }

    companion object {
        private val source = SftpFileSystemProvider.getPath(URI("sftp://user@example.test/source")) as SftpPath
        private val target = SftpFileSystemProvider.getPath(URI("sftp://user@example.test/target")) as SftpPath
        private val CONTENT = "sftp-content".toByteArray()
        private fun sftp(status: Response.StatusCode) = statusFailure(status)
        private fun statusFailure(status: Response.StatusCode) = SFTPException(status, status.toString())
        private fun attributes(type: FileMode.Type) = FileAttributes.Builder()
            .withType(type).withPermissions(0x1A4).withSize(if (type == FileMode.Type.REGULAR) CONTENT.size.toLong() else 0)
            .withUIDGID(10, 20).withAtimeMtime(30, 40).build()
    }
}
