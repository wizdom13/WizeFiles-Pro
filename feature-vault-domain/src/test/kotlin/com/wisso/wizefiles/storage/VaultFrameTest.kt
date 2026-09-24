// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class VaultFrameTest {
    @Test fun `framing and relock policy run without Android or Keystore`() {
        val payload = byteArrayOf(1, 2, 3)
        assertArrayEquals(payload, VaultPayloadFrame.decode(VaultPayloadFrame.encode(payload)))
        val backgrounded = VaultLockReducer.reduce(VaultLockState.UNLOCKED, VaultLockEvent.AppBackgrounded)
        assertEquals(VaultLockState.RELOCK_REQUIRED, backgrounded)
    }
}
