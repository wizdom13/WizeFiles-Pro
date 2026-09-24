// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.sftp.client

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class SftpClientPromiscuousVerifierRegressionTest {
    @Test
    fun `sftp client no longer uses promiscuous verifier`() {
        val source = File(
            "src/main/java/com/wisso/wizefiles/data/providers/sftp/client/SftpClient.kt"
        ).readText()

        assertFalse(source.contains("PromiscuousVerifier"))
    }
}
