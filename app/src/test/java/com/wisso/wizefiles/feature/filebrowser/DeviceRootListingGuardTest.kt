// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import java.io.IOException
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

class DeviceRootListingGuardTest {

    @Test
    fun `device root listing path returns root path when su is available`() {
        val rootPath = Paths.get("/")

        val resolved = resolveDeviceRootPathForListing(rootPath, isSuAvailable = true)

        assertSame(rootPath, resolved)
    }

    @Test
    fun `device root listing throws controlled error when su is unavailable`() {
        val exception = org.junit.Assert.assertThrows(IOException::class.java) {
            resolveDeviceRootPathForListing(Paths.get("/"), isSuAvailable = false)
        }

        assertEquals("Root access required", exception.message)
    }
}
