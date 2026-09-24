// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import java.security.KeyPairGenerator
import java.io.File
import net.schmizz.sshj.transport.TransportException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpClientHostKeyMappingTest {
    @Test
    fun `prefers structured verifier failure when available`() {
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)
        val authority = Authority(host = "example.com", port = 22, username = "user")

        verifier.verify(authority.host, authority.port, generateRsaPublicKey())
        val mapped = SftpClient.mapHostKeyVerificationFailureOrNull(
            throwable = TransportException("[HOST_KEY_NOT_VERIFIABLE] noisy raw message"),
            authority = authority,
            hostKeyVerifier = verifier
        )

        assertNotNull(mapped)
        assertTrue(mapped is SftpUnknownHostKeyException)
        assertEquals("example.com", mapped!!.host)
        assertNull(verifier.consumeFailure())
    }

    @Test
    fun `parses raw host key failure when verifier did not capture failure`() {
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)
        val authority = Authority(host = "fallback.example", port = 2200, username = "user")

        val mapped = SftpClient.mapHostKeyVerificationFailureOrNull(
            throwable = TransportException(
                "[HOST_KEY_NOT_VERIFIABLE] Could not verify `ssh-ed25519` host key with " +
                    "fingerprint `SHA256:abc123` for `parsed.example` on port 22"
            ),
            authority = authority,
            hostKeyVerifier = verifier
        )

        assertNotNull(mapped)
        assertEquals("parsed.example", mapped!!.host)
        assertEquals(22, mapped.port)
        assertEquals("ssh-ed25519", mapped.algorithm)
    }

    @Test
    fun `returns null for non host key failures`() {
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)
        val authority = Authority(host = "fallback.example", port = 2200, username = "user")

        val mapped = SftpClient.mapHostKeyVerificationFailureOrNull(
            throwable = TransportException("[DISCONNECT] Connection reset"),
            authority = authority,
            hostKeyVerifier = verifier
        )

        assertNull(mapped)
    }

    @Test
    fun `getClient maps host key failures before generic wrapping`() {
        val source = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/providers/sftp/client/SftpConnectionManager.kt"
        )

        assertTrue(
            source.contains(
                "SftpErrorMapper.hostKeyFailure(failure, authority, verifier)\n" +
                    "                ?.let { throw SftpClientException(it) }\n" +
                    "            throw SftpClientException(failure)"
            )
        )
    }

    private fun generateRsaPublicKey() =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair().public

    private fun sourceFile(path: String): String {
        val file = File(path)
        if (file.exists()) {
            return file.readText()
        }
        val appPrefixed = File("app/$path")
        require(appPrefixed.exists()) { "Unable to locate source file: $path" }
        return appPrefixed.readText()
    }
}
