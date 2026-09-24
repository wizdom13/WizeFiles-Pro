// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import org.junit.Assert.assertEquals
import org.junit.Test

class RawAppPathNameTest {

    @Test
    fun trailingSlashDirectoryUsesLeafName() {
        assertEquals("180", RawAppPath("/storage/emulated/0/Ads/180/").name)
    }

    @Test
    fun fileUriWithTrailingSlashUsesLeafName() {
        assertEquals("180", RawAppPath("file:///storage/emulated/0/Ads/180/").name)
    }

    @Test
    fun fileUriFileUsesLeafName() {
        assertEquals("photo.jpg", RawAppPath("file:///storage/emulated/0/DCIM/photo.jpg").name)
    }

    @Test
    fun uriBackedCloudNameDecodesOnlyItsLeafSegment() {
        assertEquals(
            "Q3 Sales & Margin.pdf",
            RawAppPath("rclone://box/Reports/Q3%20Sales%20%26%20Margin.pdf").name
        )
        assertEquals(
            "Café € 100%.pdf",
            RawAppPath("rclone://box/Caf%C3%A9%20%E2%82%AC%20100%25.pdf").name
        )
    }

    @Test
    fun uriBackedCloudNameKeepsPlusAndMalformedEscapesLiteral() {
        assertEquals("A+B.pdf", RawAppPath("rclone://box/A+B.pdf").name)
        assertEquals("bad%ZZ.pdf", RawAppPath("rclone://box/bad%ZZ.pdf").name)
    }

    @Test
    fun archiveEntryUsesInnerEntryNameInsteadOfEncodedArchiveName() {
        assertEquals(
            "WizeFiles_v0.6.6_debug.apk",
            RawAppPath(
                "archive:///file:/storage/emulated/0/Download/" +
                    "WizeFiles_v0.6.6%2520(10).zip?/WizeFiles_v0.6.6_debug.apk"
            ).name
        )
    }

    @Test
    fun archiveEntryDecodesNestedUnicodeNameAndKeepsLiteralPlus() {
        assertEquals(
            "Café & A+B.txt",
            RawAppPath(
                "archive:///file:/storage/emulated/0/Download/archive.zip?" +
                    "/nested/folder/Caf%C3%A9%20%26%20A+B.txt"
            ).name
        )
        assertEquals(
            "My Folder",
            RawAppPath(
                "archive:///file:/storage/emulated/0/Download/archive.zip?" +
                    "/nested/My%20Folder/"
            ).name
        )
    }

    @Test
    fun archiveRootKeepsRootName() {
        assertEquals(
            "/",
            RawAppPath(
                "archive:///file:/storage/emulated/0/Download/archive.zip?/"
            ).name
        )
    }

    @Test
    fun nonArchiveUriContinuesUsingPathLeafInsteadOfQuery() {
        assertEquals(
            "actual report.pdf",
            RawAppPath(
                "rclone://box/Reports/actual%20report.pdf?download=wrong.zip"
            ).name
        )
        assertEquals(
            "incoming file.txt",
            RawAppPath(
                "ftp://example.com/Incoming/incoming%20file.txt?mode=binary"
            ).name
        )
    }

    @Test
    fun rawFilesystemPathDoesNotDecodeLiteralPercentSequences() {
        assertEquals(
            "literal%20name.pdf",
            RawAppPath("/storage/emulated/0/literal%20name.pdf").name
        )
    }

    @Test
    fun rootPathKeepsRootName() {
        assertEquals("/", RawAppPath("/").name)
        assertEquals("/", RawAppPath("file:///").name)
        assertEquals("/", RawAppPath("rclone://box/").name)
    }
}
