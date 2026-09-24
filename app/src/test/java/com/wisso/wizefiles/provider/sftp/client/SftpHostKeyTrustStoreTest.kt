// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import java.security.KeyPairGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SftpHostKeyTrustStoreTest {
    @Test
    fun `pinned host key round-trip survives serialization`() {
        var raw = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { raw }, setRawStore = { raw = it })
        val key = SftpPresentedHostKey.from(generateRsaPublicKey())

        store.trustHostKey("sftp.example.com", 22, key)

        val pinned = store.getPinnedHostKey("sftp.example.com", 22)
        assertNotNull(pinned)
        assertEquals(key.algorithm, pinned!!.algorithm)
        assertEquals(key.encodedKeyBase64, pinned.encodedKeyBase64)
        assertEquals(key.sha256Fingerprint, pinned.sha256Fingerprint)
    }

    @Test
    fun `host and port keying is normalized and username agnostic`() {
        var raw = "{}"
        val store = SftpHostKeyTrustStore(getRawStore = { raw }, setRawStore = { raw = it })
        val key = SftpPresentedHostKey.from(generateRsaPublicKey())

        store.trustHostKey("SFTP.EXAMPLE.COM", 22, key)

        assertNotNull(store.getPinnedHostKey("sftp.example.com", 22))
        assertNull(store.getPinnedHostKey("sftp.example.com", 2022))
        assertTrue(raw.contains("sftp.example.com:22"))
    }

    private fun generateRsaPublicKey() =
        KeyPairGenerator.getInstance("RSA").apply { initialize(2048) }.genKeyPair().public
}
