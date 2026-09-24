// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb.client

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmbClientLifecycleHandlingTest {

    @Test
    fun `detects disconnected transport logoff failures`() {
        val exception = RuntimeException(
            "Cannot write Signed(SMB2_LOGOFF) as transport is disconnected"
        )

        assertTrue(exception.isDisconnectedSmbLogoffFailure())
    }

    @Test
    fun `does not classify unrelated cleanup failures as disconnected logoff`() {
        val exception = RuntimeException("Authentication failed")

        assertFalse(exception.isDisconnectedSmbLogoffFailure())
    }
}
