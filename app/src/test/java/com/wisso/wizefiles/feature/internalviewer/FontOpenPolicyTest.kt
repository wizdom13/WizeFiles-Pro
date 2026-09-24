// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.internalviewer

import com.wisso.wizefiles.core.files.mime.MimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FontOpenPolicyTest {
    @Test
    fun `native font extensions are supported case insensitively`() {
        listOf("a.ttf", "a.OTF", "a.ttc").forEach {
            assertTrue(FontOpenPolicy.supports(it, null))
        }
    }

    @Test
    fun `native provider MIME types route without a useful extension`() {
        listOf("font/ttf", "font/otf", "font/collection", "font/sfnt").forEach { mime ->
            assertTrue(FontOpenPolicy.supports("download", mime))
            assertEquals(
                InternalOpenPolicy.Target.FONT_VIEWER,
                InternalOpenPolicy.targetAfterExtraction(MimeType(mime), "download")
            )
        }
    }

    @Test
    fun `native font extension routes even when provider reports octet stream`() {
        assertEquals(
            InternalOpenPolicy.Target.FONT_VIEWER,
            InternalOpenPolicy.targetAfterExtraction(MimeType("application/octet-stream"), "Demo.TTF")
        )
    }

    @Test
    fun `web fonts are not falsely advertised`() {
        assertFalse(FontOpenPolicy.supports("a.woff2", "application/octet-stream"))
        assertFalse(FontOpenPolicy.supports("a.woff", null))
    }
}
