package com.wisso.wizefiles.provider.sftp.client

import net.schmizz.sshj.transport.TransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpHostKeyFailureParserTest {

    @Test
    fun `maps HOST_KEY_NOT_VERIFIABLE transport error to unknown host key exception`() {
        val throwable = TransportException(
            "[HOST_KEY_NOT_VERIFIABLE] Could not verify ssh-ed25519 host key with fingerprint SHA256:abc123 for test.rebex.net on port 22"
        )

        val parsed = SftpHostKeyFailureParser.parseUnknownHostKey(
            throwable = throwable,
            fallbackHost = "fallback.example",
            fallbackPort = 2022
        )

        assertNotNull(parsed)
        assertEquals("test.rebex.net", parsed!!.host)
        assertEquals(22, parsed.port)
        assertEquals("ssh-ed25519", parsed.algorithm)
        assertEquals("SHA256:abc123", parsed.presentedSha256Fingerprint)
    }

    @Test
    fun `parses backtick wrapped host key message and normalizes values`() {
        val throwable = TransportException(
            "[HOST_KEY_NOT_VERIFIABLE] Could not verify `ssh-ed25519` host key with fingerprint " +
                "`SHA256:abc123` for `test.rebex.net` on port 22"
        )

        val parsed = SftpHostKeyFailureParser.parseUnknownHostKey(
            throwable = throwable,
            fallbackHost = "fallback.example",
            fallbackPort = 2022
        )

        assertNotNull(parsed)
        assertEquals("test.rebex.net", parsed!!.host)
        assertEquals("ssh-ed25519", parsed.algorithm)
        assertEquals("SHA256:abc123", parsed.presentedSha256Fingerprint)
    }

    @Test
    fun `non host key transport errors keep existing behavior`() {
        val throwable = TransportException("[DISCONNECT] Connection reset")

        val parsed = SftpHostKeyFailureParser.parseUnknownHostKey(
            throwable = throwable,
            fallbackHost = "example.com",
            fallbackPort = 22
        )

        assertNull(parsed)
    }

    @Test
    fun `uses fallback endpoint when message does not include host and port`() {
        val throwable = TransportException(
            "[HOST_KEY_NOT_VERIFIABLE] Could not verify ssh-rsa host key with fingerprint SHA256:fallback"
        )

        val parsed = SftpHostKeyFailureParser.parseUnknownHostKey(
            throwable = throwable,
            fallbackHost = "fallback.example",
            fallbackPort = 2222
        )

        assertNotNull(parsed)
        assertEquals("fallback.example", parsed!!.host)
        assertEquals(2222, parsed.port)
        assertTrue(parsed.presentedEncodedKeyBase64.isEmpty())
    }

    @Test
    fun `parses nested cause host key failure`() {
        val nested = IllegalStateException(
            "top-level",
            TransportException(
                "[HOST_KEY_NOT_VERIFIABLE] Could not verify `ssh-ed25519` host key with " +
                    "fingerprint `SHA256:nested` for `nested.example` on port 2200"
            )
        )

        val parsed = SftpHostKeyFailureParser.parseUnknownHostKey(
            throwable = nested,
            fallbackHost = "fallback.example",
            fallbackPort = 2022
        )

        assertNotNull(parsed)
        assertEquals("nested.example", parsed!!.host)
        assertEquals(2200, parsed.port)
        assertEquals("ssh-ed25519", parsed.algorithm)
        assertEquals("SHA256:nested", parsed.presentedSha256Fingerprint)
    }
}
