// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.mime

import org.junit.Assert.assertEquals
import org.junit.Test

class MimeTypeConversionExtensionsTest {

    @Test
    fun `product extensions are stable without a platform mapping`() {
        assertEquals(MimeType.WIZEFILES_BACKUP, MimeType.guessFromExtension(".WZF"))
        assertEquals("text/csv".asMimeType(), MimeType.guessFromExtension("CSV"))
        assertEquals("application/typescript".asMimeType(), MimeType.guessFromExtension("ts"))
    }

    @Test
    fun `portable fallbacks cover formats opened directly by WizeFiles`() {
        assertEquals(
            "application/x-7z-compressed".asMimeType(),
            ExtensionMimeCatalog.resolve("7z") { null }
        )
        assertEquals("image/avif".asMimeType(), ExtensionMimeCatalog.resolve("avif") { null })
    }

    @Test
    fun `platform result is preferred to the portable fallback`() {
        val resolved = ExtensionMimeCatalog.resolve("rar") { "application/x-platform-rar" }

        assertEquals("application/x-platform-rar".asMimeType(), resolved)
    }

    @Test
    fun `invalid platform result is safely rejected`() {
        val resolved = ExtensionMimeCatalog.resolve("unknown") { "not-a-mime-type" }

        assertEquals(MimeType.GENERIC, resolved)
    }

    @Test
    fun `path selection skips blanks and returns first recognized candidate`() {
        assertEquals(
            MimeType.WIZEFILES_BACKUP,
            MimeType.guessFromPaths(null, " ", "/Download/settings.wzf")
        )
    }

    @Test
    fun `empty path selection is generic`() {
        assertEquals(MimeType.GENERIC, MimeType.guessFromPaths(emptyList()))
    }
}
