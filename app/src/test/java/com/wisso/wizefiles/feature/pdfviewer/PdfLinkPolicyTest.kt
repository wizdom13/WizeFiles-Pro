// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfLinkPolicyTest {
    @Test
    fun `web and mail links are allowed case insensitively`() {
        listOf("http", "HTTPS", "mailto").forEach {
            assertTrue(PdfLinkPolicy.allowsScheme(it))
        }
    }

    @Test
    fun `local intent and custom schemes are blocked`() {
        listOf(null, "file", "content", "intent", "javascript", "data", "custom").forEach {
            assertFalse(PdfLinkPolicy.allowsScheme(it))
        }
    }
}
