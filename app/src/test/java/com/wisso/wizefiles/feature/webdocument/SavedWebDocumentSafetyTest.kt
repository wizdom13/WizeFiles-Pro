// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.webdocument

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class SavedWebDocumentSafetyTest {
    @Test
    fun `archive path segments reject traversal and separators`() {
        listOf("", ".", "..", "a/b", "a\\b", "bad\u0000name").forEach { value ->
            assertThrows(UnsafeSavedWebDocumentException::class.java) {
                SafeWebArchiveExtractor.normalizeSegment(value)
            }
        }
        assertEquals("index.html", SafeWebArchiveExtractor.normalizeSegment("index.html"))
    }

    @Test
    fun `external link policy is narrowly allowlisted`() {
        assertTrue(WebDocumentLinkPolicy.allowsScheme("https"))
        assertTrue(WebDocumentLinkPolicy.allowsScheme("mailto"))
        assertFalse(WebDocumentLinkPolicy.allowsScheme("file"))
        assertFalse(WebDocumentLinkPolicy.allowsScheme("content"))
        assertFalse(WebDocumentLinkPolicy.allowsScheme("javascript"))
    }
}
