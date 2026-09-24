// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.ftp.client

import java.io.IOException
import java.net.SocketTimeoutException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FtpErrorMapperTest {
    @Test
    fun `recognizes nested control connection loss`() {
        assertTrue(FtpErrorMapper.isConnectionLost(IOException("wrapper", IOException("broken pipe"))))
        assertFalse(FtpErrorMapper.isConnectionLost(IOException("permission denied")))
    }

    @Test
    fun `classifies nested data timeout`() {
        val message = FtpErrorMapper.dataConnectionReason(
            IOException("listing failed", SocketTimeoutException("timed out"))
        )
        assertTrue(message.orEmpty().contains("timed out"))
    }
}
