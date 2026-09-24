// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import com.wisso.wizefiles.provider.ftp.client.Authority as FtpAuthority
import com.wisso.wizefiles.provider.ftp.client.Mode
import com.wisso.wizefiles.provider.ftp.client.Protocol as FtpProtocol
import com.wisso.wizefiles.storage.FtpServer
import com.wisso.wizefiles.storage.LegacyRemoteStorage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageSettingsLegacyRemoteMigrationTest {

    @Test
    fun sanitizeLegacyStoragesFiltersRemovedRemoteEntriesAndKeepsOtherStorageTypes() {
        val removed = LegacyRemoteStorage(id = 1L, customName = "Legacy")
        val ftp = FtpServer(
            id = 2L,
            customName = "FTP",
            authority = FtpAuthority(
                protocol = FtpProtocol.FTP,
                host = "ftp.example.com",
                port = 21,
                username = "user",
                mode = Mode.PASSIVE,
                encoding = "UTF-8"
            ),
            password = "secret",
            relativePath = ""
        )

        val sanitized = sanitizeLegacyStorages(listOf(removed, ftp))

        assertEquals(1, sanitized.size)
        assertTrue(sanitized.single() is FtpServer)
        assertEquals(2L, sanitized.single().id)
    }
}
