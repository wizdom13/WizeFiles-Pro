package com.wisso.wizefiles.provider.ftp

import com.wisso.wizefiles.provider.ftp.client.NegativeReplyCodeException
import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class IOExceptionFtpExtensionsTest {

    @Test
    fun `connection refused data channel message is classified clearly`() {
        val ioException = IOException(
            "failed to connect to /192.0.2.1 (port 49152) from /10.0.2.15 (port 47500) after 60000ms: ECONNREFUSED"
        )

        val mapped = ioException.toFileSystemExceptionForFtp("/remote/file.txt")

        assertTrue(
            mapped.reason.orEmpty().contains("FTP data connection was refused by the server/network")
        )
        assertTrue(mapped.reason.orEmpty().contains("active/passive mode settings"))
        assertSame(ioException, mapped.cause)
    }

    @Test
    fun `connection timeout data channel message is classified clearly`() {
        val ioException = IOException(
            "failed to connect to /192.0.2.1 (port 49152) from /10.0.2.15 (port 47500) after 60000ms"
        )

        val mapped = ioException.toFileSystemExceptionForFtp("/remote/file.txt")

        assertTrue(mapped.reason.orEmpty().contains("FTP data connection timed out"))
        assertTrue(mapped.reason.orEmpty().contains("active/passive mode settings"))
        assertSame(ioException, mapped.cause)
    }

    @Test
    fun `socket timeout data channel failure is classified clearly`() {
        val ioException = IOException(
            "failed to connect data socket",
            SocketTimeoutException("connect timed out")
        )

        val mapped = ioException.toFileSystemExceptionForFtp("/remote/file.txt")

        assertTrue(mapped.reason.orEmpty().contains("FTP data connection timed out"))
        assertTrue(mapped.reason.orEmpty().contains("active/passive mode settings"))
        assertSame(ioException, mapped.cause)
    }

    @Test
    fun `connection loss message is classified clearly`() {
        val ioException = IOException("Broken pipe")

        val mapped = ioException.toFileSystemExceptionForFtp("/remote/file.txt")

        assertTrue(mapped.reason.orEmpty().contains("FTP control connection was lost"))
        assertSame(ioException, mapped.cause)
    }

    @Test
    fun `ftp transfer-aborted reply is classified as control connection loss`() {
        val ioException = IOException("426 Connection closed; transfer aborted.")

        val mapped = ioException.toFileSystemExceptionForFtp("/remote/file.txt")

        assertTrue(mapped.reason.orEmpty().contains("FTP control connection was lost"))
        assertSame(ioException, mapped.cause)
    }

    @Test
    fun `negative reply code mapping remains unchanged`() {
        val exception = NegativeReplyCodeException(550, "No such file")

        val mapped = exception.toFileSystemExceptionForFtp("/missing.txt")

        assertEquals("No such file", mapped.reason)
        assertSame(exception, mapped.cause)
    }
}
