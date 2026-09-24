// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.archive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArchiveExceptionExtensionsTest {
    @Test
    fun `password error detection recognizes passphrase failures`() {
        assertTrue("Incorrect passphrase".isPasswordError())
        assertTrue("Passphrase required for this entry".isPasswordError())
    }

    @Test
    fun `user-friendly archive message mapping handles common failures`() {
        assertEquals(
            "A required archive part is missing (multipart archive)",
            "Cannot open file in multi-volume archive".toUserFriendlyArchiveMessage()
        )
        assertEquals(
            "Archive appears to be corrupt or incomplete",
            "Damaged data stream".toUserFriendlyArchiveMessage()
        )
        assertEquals(
            "Unsupported archive feature or compression method",
            "Method is not supported".toUserFriendlyArchiveMessage()
        )
    }
}
