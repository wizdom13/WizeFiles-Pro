// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AppPathUriCodecTest {

    @Test
    fun localAppPathRoundTripsThroughUriString() {
        val appPath = LocalAppPath(File("/storage/emulated/0/Download/report.txt"))

        val encoded = appPath.toUriString()
        val decoded = encoded.toAppPathOrNull()

        assertEquals(appPath, decoded)
    }

    @Test
    fun contentUriRoundTripsAsRawAppPath() {
        val encoded = "content://com.example.documents/document/1234"

        val decoded = encoded.toAppPathOrNull()

        assertTrue(decoded is RawAppPath)
        assertEquals(encoded, (decoded as RawAppPath).rawPath)
    }
}
