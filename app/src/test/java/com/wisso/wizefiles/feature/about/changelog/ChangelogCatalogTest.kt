// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.about.changelog

import java.io.File
import org.junit.Assert.*
import org.junit.Test

class ChangelogCatalogTest {
    private fun release(version: String, order: Int, note: String) =
        "<section data-version=\"$version\" data-release-order=\"$order\"><h2>$version</h2><ul><li>$note</li></ul></section>"

    private val catalog = ChangelogCatalog.parse(
        "<section><h2>Unreleased</h2><ul><li>Future change</li></ul></section>" +
            release("1.2.0", 15, "Future release") + release("1.1.0", 14, "Syncthing") +
            release("1.0.0", 13, "Open source") + release("0.7.0", 12, "Installer")
    )

    @Test fun firstLaunchShowsOnlyTheInstalledRelease() {
        val notice = requireNotNull(catalog.notice("1.1.0", 0))
        assertEquals(14, notice.order)
        assertTrue(notice.html.contains("Syncthing"))
        assertFalse(notice.html.contains("Open source"))
        assertFalse(notice.html.contains("Future"))
    }

    @Test fun updateIncludesEveryMissedReleaseWithoutRepeatingAcknowledgedNotes() {
        val notice = requireNotNull(catalog.notice("1.1.0", 12))
        assertTrue(notice.html.contains("Open source"))
        assertTrue(notice.html.contains("Syncthing"))
        assertFalse(notice.html.contains("Installer"))
        assertFalse(notice.html.contains("Future"))
    }

    @Test fun acknowledgedVersionsDowngradesAndUnknownVersionsDoNotPrompt() {
        assertNull(catalog.notice("1.1.0", 14))
        assertNull(catalog.notice("1.0.0", 14))
        assertNull(catalog.notice("2.0.0", 14))
        assertNull(catalog.notice("1.1.01", 0))
        assertNotNull(catalog.notice("1.1.0-debug", 0))
    }

    @Test fun legacyVersionLabelsUseRecordedChronology() {
        val legacy = ChangelogCatalog.parse(release("0.6.0", 5, "Transfer Center") + release("0.70", 4, "Cloud"))
        val notice = requireNotNull(legacy.notice("0.6.0", 4))
        assertTrue(notice.html.contains("Transfer Center"))
        assertFalse(notice.html.contains("Cloud"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun duplicateReleaseMarkersAreRejected() {
        ChangelogCatalog.parse(release("1.0.0", 1, "One") + release("1.1.0", 1, "Two"))
    }

    @Test fun actualBundledHistoryContainsAllRecordedVersions() {
        val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
            .first { File(it, "app/src/main/assets/changelogs.html").isFile }
        val bundled = ChangelogCatalog.parse(File(root, "app/src/main/assets/changelogs.html").readText())
        val versions = listOf("0.64", "0.65", "0.66", "0.70", "0.6.0", "0.6.1", "0.6.2", "0.6.3", "0.6.4", "0.6.6", "0.6.7", "0.7.0", "1.0.0", "1.1.0", "1.2.0")
        versions.forEach { assertNotNull(it, bundled.notice(it, 0)) }
        val previous = requireNotNull(bundled.notice("1.1.0", 13))
        assertTrue(previous.html.contains("Syncthing"))
        assertFalse(previous.html.contains("Offline Changelog screen"))
        val current = requireNotNull(bundled.notice("1.2.0", 14))
        assertEquals(15, current.order)
        assertTrue(current.html.contains("Syncthing"))
        assertTrue(current.html.contains("Offline Changelog screen"))
        assertFalse(current.html.contains("PDF files that remained blank"))
        assertNull(bundled.notice("1.2.0", 15))
    }
}
