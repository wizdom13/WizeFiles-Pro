// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.junit.Assert.*
import org.junit.Test

class SyncthingBackupCodecTest {
    @Test fun authenticatedBackupRoundTripsAndUsesFreshSaltAndNonce() {
        val plain = "configuration and private identity material".toByteArray()
        val password = "a long backup passphrase".toCharArray()
        val first = SyncthingBackupCodec.encrypt(plain, password)
        val second = SyncthingBackupCodec.encrypt(plain, password)
        assertFalse(first.contentEquals(second))
        assertTrue(SyncthingBackupCodec.encrypted(first))
        assertArrayEquals(plain, SyncthingBackupCodec.decrypt(first, password))
        assertThrows(java.security.GeneralSecurityException::class.java) {
            SyncthingBackupCodec.decrypt(first, "an incorrect password".toCharArray())
        }
        first[first.lastIndex] = (first.last().toInt() xor 1).toByte()
        assertThrows(java.security.GeneralSecurityException::class.java) { SyncthingBackupCodec.decrypt(first, password) }
    }
}
