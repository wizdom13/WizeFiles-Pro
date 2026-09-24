// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.atomic.AtomicReference
import java.io.File

class SftpHostKeyVerifierTest {
    @Test
    fun `unknown host key is rejected`() {
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)
        val key = generateRsaPublicKey()

        val verified = verifier.verify("example.com", 22, key)

        assertFalse(verified)
        val failure = verifier.consumeFailure()
        assertTrue(failure is SftpUnknownHostKeyException)
        failure as SftpUnknownHostKeyException
        assertEquals("example.com", failure.host)
        assertEquals(22, failure.port)
    }

    @Test
    fun `matching pinned host key is accepted`() {
        var rawStore = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { rawStore }, setRawStore = { rawStore = it })
        val key = generateRsaPublicKey()
        store.trustHostKey("example.com", 22, SftpPresentedHostKey.from(key))
        val verifier = PinnedSftpHostKeyVerifier(store)

        val verified = verifier.verify("example.com", 22, key)

        assertTrue(verified)
        assertNull(verifier.consumeFailure())
    }

    @Test
    fun `mismatched host key is rejected`() {
        var rawStore = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { rawStore }, setRawStore = { rawStore = it })
        val pinnedKey = generateRsaPublicKey()
        val changedKey = generateRsaPublicKey()
        store.trustHostKey("example.com", 22, SftpPresentedHostKey.from(pinnedKey))
        val verifier = PinnedSftpHostKeyVerifier(store)

        val verified = verifier.verify("example.com", 22, changedKey)

        assertFalse(verified)
        val failure = verifier.consumeFailure()
        assertTrue(failure is SftpHostKeyMismatchException)
        failure as SftpHostKeyMismatchException
        assertEquals("example.com", failure.host)
        assertEquals(22, failure.port)
        assertTrue(failure.expectedSha256Fingerprint.startsWith("SHA256:"))
        assertTrue(failure.presentedSha256Fingerprint.startsWith("SHA256:"))
    }

    @Test
    fun `verifier compares exact key material not only host and port`() {
        var rawStore = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { rawStore }, setRawStore = { rawStore = it })
        val pinnedKey = generateRsaPublicKey()
        val changedKey = generateRsaPublicKey()
        store.trustHostKey("same-host", 22, SftpPresentedHostKey.from(pinnedKey))
        val verifier = PinnedSftpHostKeyVerifier(store)

        assertFalse(verifier.verify("same-host", 22, changedKey))
        assertTrue(verifier.consumeFailure() is SftpHostKeyMismatchException)
    }

    @Test
    fun `trust once accepts retry without persisting`() {
        var rawStore = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { rawStore }, setRawStore = { rawStore = it })
        val key = generateRsaPublicKey()
        val verifier = PinnedSftpHostKeyVerifier(store)

        assertFalse(verifier.verify("once.example", 22, key))
        store.trustHostKeyOnce("once.example", 22, SftpPresentedHostKey.from(key))

        assertTrue(verifier.verify("once.example", 22, key))
        assertNull(store.getPinnedHostKey("once.example", 22))
        assertEquals("{}", rawStore)
    }

    @Test
    fun `trust and save persists and accepts reconnect`() {
        var rawStore = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { rawStore }, setRawStore = { rawStore = it })
        val key = generateRsaPublicKey()
        store.trustHostKey("persist.example", 22, SftpPresentedHostKey.from(key))
        val verifier = PinnedSftpHostKeyVerifier(store)

        assertTrue(verifier.verify("persist.example", 22, key))
        assertTrue(rawStore.contains("persist.example:22"))
    }

    @Test
    fun `without trust verification stays strict`() {
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)
        val key = generateRsaPublicKey()

        assertFalse(verifier.verify("cancel.example", 22, key))
        assertTrue(verifier.consumeFailure() is SftpUnknownHostKeyException)
    }

    @Test
    fun `failure is consumed exactly once`() {
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)

        assertFalse(verifier.verify("one-shot.example", 22, generateRsaPublicKey()))
        assertTrue(verifier.consumeFailure() is SftpUnknownHostKeyException)
        assertNull(verifier.consumeFailure())
    }

    @Test
    fun `verifier failure handoff uses verifier state instead of thread local`() {
        val field = PinnedSftpHostKeyVerifier::class.java.getDeclaredField("lastFailure")
        field.isAccessible = true
        val store = SftpHostKeyTrustStore(getRawStore = { "{}" }, setRawStore = {})
        val verifier = PinnedSftpHostKeyVerifier(store)

        assertTrue(field.get(verifier) is AtomicReference<*>)
    }

    @Test
    fun `verifier implementation does not use ThreadLocal`() {
        val source = sourceFile(
            "src/main/java/com/wisso/wizefiles/data/providers/sftp/client/SftpHostKeyTrust.kt"
        )

        assertFalse(source.contains("ThreadLocal<SftpHostKeyVerificationException?>"))
        assertTrue(source.contains("AtomicReference<SftpHostKeyVerificationException?>"))
        assertTrue(source.contains("getAndSet(null)"))
    }

    private fun sourceFile(path: String): String {
        val file = File(path)
        if (file.exists()) {
            return file.readText()
        }
        val appPrefixed = File("app/$path")
        require(appPrefixed.exists()) { "Unable to locate source file: $path" }
        return appPrefixed.readText()
    }

    private fun generateRsaPublicKey() =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair().public
}
