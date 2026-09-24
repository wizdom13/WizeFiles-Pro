package com.wisso.wizefiles.provider.ftp.client

import java.nio.file.AccessDeniedException
import java.nio.file.NoSuchFileException
import org.apache.commons.net.ftp.FTPClient
import org.apache.commons.net.ftp.FTPSClient
import org.apache.commons.net.ftp.FTPReply
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpClientFoundationBehaviorTest {
    @Test
    fun protocolCreatesTheExpectedClientAndRejectsUnknownSchemes() {
        assertTrue(Protocol.FTP.createClient()::class.java == FTPClient::class.java)
        assertTrue(Protocol.FTPS.createClient() is FTPSClient)
        assertEquals(Protocol.FTPES, Protocol.fromScheme("ftpes"))
        assertFalse(Protocol.SCHEMES.contains("http"))
    }

    @Test
    fun authorityOmitsDefaultPortAndEmptyUser() {
        val anonymous = Authority(
            Protocol.FTP,
            "example.com",
            FTPClient.DEFAULT_PORT,
            "",
            Mode.PASSIVE,
            Authority.DEFAULT_ENCODING
        )
        val custom = anonymous.copy(username = "user", port = 2121)

        assertNull(anonymous.toUriAuthority().userInfo)
        assertNull(anonymous.toUriAuthority().port)
        assertEquals("user@example.com:2121", custom.toString())
    }

    @Test
    fun negativeRepliesMapToNioFailuresWithTheirCause() {
        val denied = NegativeReplyCodeException(FTPReply.NOT_LOGGED_IN, "login")
            .toFileSystemException("/file")
        val missing = NegativeReplyCodeException(FTPReply.FILE_UNAVAILABLE, "missing")
            .toFileSystemException("/file")

        assertTrue(denied is AccessDeniedException)
        assertTrue(missing is NoSuchFileException)
        assertTrue(denied.cause is NegativeReplyCodeException)
    }
}
