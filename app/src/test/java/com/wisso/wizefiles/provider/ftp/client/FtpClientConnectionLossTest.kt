// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import java.io.ByteArrayInputStream
import java.io.IOException
import java.io.InputStream
import java.net.SocketTimeoutException
import java.nio.file.Files
import com.wisso.wizefiles.provider.ftp.FtpFileSystemProvider
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPFile
import org.apache.commons.net.ftp.FTPReply
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class FtpClientConnectionLossTest {
    private val authority = Authority(
        protocol = Protocol.FTP,
        host = "example.com",
        port = 21,
        username = "user",
        mode = Mode.PASSIVE,
        encoding = Authority.DEFAULT_ENCODING
    )

    @Before
    fun setUp() {
        clearClientPool()
    }

    @Test
    fun `connection loss detection handles common socket failures`() {
        assertTrue(FtpClient.isConnectionLostIOException(IOException("Broken pipe")))
        assertTrue(FtpClient.isConnectionLostIOException(IOException("Connection reset")))
        assertTrue(FtpClient.isConnectionLostIOException(IOException("Connection is not open")))
        assertTrue(
            FtpClient.isConnectionLostIOException(
                IOException("Software caused connection abort")
            )
        )
        assertTrue(
            FtpClient.isConnectionLostIOException(
                IOException("426 Connection closed; transfer aborted.")
            )
        )
        assertFalse(FtpClient.isConnectionLostIOException(IOException("Permission denied")))
    }

    @Test
    fun `connection loss detection handles wrapped runtime exceptions`() {
        val wrapped = RuntimeException("read failed", IOException("Connection reset by peer"))

        assertTrue(FtpClient.isConnectionLostIOException(wrapped))
    }

    @Test
    fun `retrieve file retries once for connection-lost setup failure`() {
        val failingClient = FakeFtpClient(
            retrieveFileStreamFailure = IOException("Software caused connection abort")
        )
        val successfulClient = FakeFtpClient(
            retrieveFileStreamResult = ByteArrayInputStream("ok".toByteArray())
        )
        putPooledClients(authority, listOf(successfulClient, failingClient))

        val inputStream = FtpClient.retrieveFile(TestPath(authority, "/remote/file.txt"))
        inputStream.close()

        assertTrue(failingClient.disconnectCalls > 0)
        assertSame(successfulClient, lastPooledClient(authority))
    }

    @Test
    fun `retrieve file setup failure after retry discards broken clients`() {
        val firstClient = FakeFtpClient(
            retrieveFileStreamFailure = IOException("Software caused connection abort")
        )
        val secondClient = FakeFtpClient(
            retrieveFileStreamFailure = IOException("Software caused connection abort")
        )
        putPooledClients(authority, listOf(secondClient, firstClient))

        runCatching {
            FtpClient.retrieveFile(TestPath(authority, "/remote/file.txt"))
        }.onSuccess {
            throw AssertionError("Expected retrieveFile to fail")
        }

        assertTrue(firstClient.disconnectCalls > 0)
        assertTrue(secondClient.disconnectCalls > 0)
        assertTrue(pooledClientsFor(authority).isEmpty())
    }

    @Test
    fun `list directory retries once for data connection setup timeout failure`() {
        val failingClient = FakeFtpClient(
            listFilesFailure = IOException(
                "failed to connect to /192.0.2.1 (port 49152) from /10.0.2.15 (port 47500) after 60000ms"
            )
        )
        val successfulClient = FakeFtpClient(
            listFilesResult = arrayOf(FTPFile().apply { name = "ok" })
        )
        putPooledClients(authority, listOf(successfulClient, failingClient))

        val files = FtpClient.listDirectory(TestPath(authority, "/"))

        assertEquals(1, files.size)
        assertTrue(failingClient.disconnectCalls > 0)
        assertSame(successfulClient, lastPooledClient(authority))
    }

    @Test
    fun `list directory retries once for socket timeout setup failure`() {
        val failingClient = FakeFtpClient(
            listFilesFailure = IOException("failed to connect data socket", SocketTimeoutException("connect timed out"))
        )
        val successfulClient = FakeFtpClient(
            listFilesResult = arrayOf(FTPFile().apply { name = "ok" })
        )
        putPooledClients(authority, listOf(successfulClient, failingClient))

        val files = FtpClient.listDirectory(TestPath(authority, "/"))

        assertEquals(1, files.size)
        assertTrue(failingClient.disconnectCalls > 0)
        assertSame(successfulClient, lastPooledClient(authority))
    }

    @Test
    fun `list directory setup timeout failure after retry discards broken clients`() {
        val firstClient = FakeFtpClient(
            listFilesFailure = IOException(
                "failed to connect to /192.0.2.1 (port 49152) from /10.0.2.15 (port 47500) after 60000ms"
            )
        )
        val secondClient = FakeFtpClient(
            listFilesFailure = IOException(
                "failed to connect to /192.0.2.1 (port 49152) from /10.0.2.15 (port 47500) after 60000ms"
            )
        )
        putPooledClients(authority, listOf(secondClient, firstClient))

        runCatching {
            FtpClient.listDirectory(TestPath(authority, "/"))
        }.onSuccess {
            throw AssertionError("Expected listDirectory to fail")
        }

        assertTrue(firstClient.disconnectCalls > 0)
        assertTrue(secondClient.disconnectCalls > 0)
        assertTrue(pooledClientsFor(authority).isEmpty())
    }

    @Test
    fun `list file retries once with a fresh client after a data connection failure`() {
        val failingClient = FakeFtpClient(
            listFilesFailure = IOException(
                "failed to connect to /192.0.2.1 (port 49152) from /10.0.2.15 (port 47500) after 60000ms"
            )
        )
        val successfulClient = FakeFtpClient(
            listFilesResult = arrayOf(FTPFile().apply { name = "file.txt" })
        )
        putPooledClients(authority, listOf(successfulClient, failingClient))

        val file = FtpClient.listFile(
            TestPath(authority, "/remote/file.txt"),
            noFollowLinks = true
        )

        assertEquals("file.txt", file.name)
        assertTrue(failingClient.disconnectCalls > 0)
        assertSame(successfulClient, lastPooledClient(authority))
    }

    @Test
    fun `successful parent listing reports an absent entry as file unavailable`() {
        val client = FakeFtpClient(
            listFilesResult = emptyArray(),
            replyCodeResult = FTPReply.CLOSING_DATA_CONNECTION
        )
        putPooledClients(authority, listOf(client))

        val failure = runCatching {
            FtpClient.listFile(
                TestPath(authority, "/remote/missing.txt"),
                noFollowLinks = true
            )
        }.exceptionOrNull()

        assertTrue(failure is NegativeReplyCodeException)
        assertEquals(FTPReply.FILE_UNAVAILABLE, (failure as NegativeReplyCodeException).replyCode)
    }

    @Test
    fun `delete missing staging file avoids a directory listing`() {
        val client = FakeFtpClient(
            deleteFileResult = false,
            removeDirectoryResult = false,
            replyCodeResult = FTPReply.FILE_UNAVAILABLE
        )
        putPooledClients(authority, listOf(client))
        val temporary = FtpFileSystemProvider.getOrNewFileSystem(authority)
            .getPath("/remote/.wizefiles-part-12345678-1")

        assertFalse(Files.deleteIfExists(temporary))
        assertEquals(1, client.deleteFileCalls)
        assertEquals(1, client.removeDirectoryCalls)
        assertEquals(0, client.listFilesCalls)
    }

    @Test
    fun `delete unknown directory falls back from DELE to RMD without listing`() {
        val client = FakeFtpClient(
            deleteFileResult = false,
            removeDirectoryResult = true,
            replyCodeResult = FTPReply.FILE_UNAVAILABLE
        )
        putPooledClients(authority, listOf(client))
        val directory = FtpFileSystemProvider.getOrNewFileSystem(authority)
            .getPath("/remote/folder")

        FtpClient.delete(directory)

        assertEquals(1, client.deleteFileCalls)
        assertEquals(1, client.removeDirectoryCalls)
        assertEquals(0, client.listFilesCalls)
    }

    private fun clearClientPool() {
        FtpClient.closeAll()
    }

    private fun putPooledClients(authority: Authority, clients: List<FTPClient>) {
        FtpClient.replaceIdleConnectionsForTesting(authority, clients)
    }

    private fun pooledClientsFor(authority: Authority): List<FTPClient> =
        FtpClient.idleConnectionsForTesting(authority)

    private fun lastPooledClient(authority: Authority): FTPClient? = pooledClientsFor(authority).lastOrNull()

    private data class TestPath(
        override val authority: Authority,
        override val remotePath: String
    ) : FtpClient.Path {
        override fun resolve(other: String): FtpClient.Path = copy(remotePath = "$remotePath/$other")
    }

    private class FakeFtpClient(
        private val retrieveFileStreamFailure: IOException? = null,
        private val retrieveFileStreamResult: InputStream? = null,
        private val listFilesFailure: IOException? = null,
        private val listFilesResult: Array<FTPFile>? = null,
        private val deleteFileResult: Boolean = true,
        private val removeDirectoryResult: Boolean = true,
        private val replyCodeResult: Int = FTPReply.CLOSING_DATA_CONNECTION
    ) : FTPClient() {
        var disconnectCalls = 0
            private set
        var listFilesCalls = 0
            private set
        var deleteFileCalls = 0
            private set
        var removeDirectoryCalls = 0
            private set
        private var connected = true

        override fun isConnected(): Boolean = connected

        override fun sendNoOp(): Boolean = true

        override fun retrieveFileStream(remote: String?): InputStream? {
            retrieveFileStreamFailure?.let { throw it }
            return retrieveFileStreamResult ?: ByteArrayInputStream(ByteArray(0))
        }

        override fun completePendingCommand(): Boolean = true

        override fun hasFeature(feature: String?): Boolean = false

        override fun listFiles(pathname: String?): Array<FTPFile> {
            listFilesCalls++
            listFilesFailure?.let { throw it }
            return listFilesResult ?: emptyArray()
        }

        override fun deleteFile(pathname: String?): Boolean {
            deleteFileCalls++
            return deleteFileResult
        }

        override fun removeDirectory(pathname: String?): Boolean {
            removeDirectoryCalls++
            return removeDirectoryResult
        }

        override fun getReplyCode(): Int = replyCodeResult

        override fun getReplyString(): String = "$replyCodeResult Test reply\r\n"

        override fun logout(): Boolean = true

        override fun disconnect() {
            disconnectCalls++
            connected = false
        }
    }
}
