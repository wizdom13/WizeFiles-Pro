// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProviderClientDecompositionSourceTest {
    private val root = generateSequence(File(requireNotNull(System.getProperty("user.dir")))) {
        it.parentFile
    }.first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `document resolver delegates query uri metadata and retry policy`() {
        val resolver = source("document/resolver/DocumentResolver.kt")
        listOf(
            "DocumentQueryClient",
            "DocumentUriResolver",
            "DocumentMetadataMapper",
            "DocumentRetryPolicy"
        ).forEach { assertTrue("Missing $it delegation", it in resolver) }
        assertFalse("ContentObserver" in resolver)
    }

    @Test
    fun `protocol clients delegate connection lifecycle and errors`() {
        val smb = source("smb/client/SmbClient.kt")
        val ftp = source("ftp/client/FtpClient.kt")
        val sftp = source("sftp/client/SftpClient.kt")
        assertTrue("SmbSessionManager" in smb && "SmbDiscoveryEngine" in smb)
        assertTrue("FtpConnectionManager" in ftp && "FtpErrorMapper" in ftp)
        assertTrue("FtpFeatureNegotiator" in ftp)
        assertTrue("SftpConnectionManager" in sftp && "SftpErrorMapper" in sftp)
        assertFalse("mutableMapOf<Authority, Session>()" in smb)
        assertFalse("mutableMapOf<Authority, ClientConnection>()" in sftp)
    }

    private fun source(relative: String): String = File(
        root,
        "app/src/main/java/com/wisso/wizefiles/data/providers/$relative"
    ).readText()
}
