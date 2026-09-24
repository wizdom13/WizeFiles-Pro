// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.viewer.common

import com.wisso.wizefiles.viewer.common.ExternalIntentValidator
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExternalIntentValidationTest {

    @Test
    fun rejectsUnknownScheme() {
        assertFalse(ExternalIntentValidator.isTrustedExternalUriString("https://attacker.test/a.jpg"))
    }

    @Test
    fun rejectsEmptyScheme() {
        assertFalse(ExternalIntentValidator.isTrustedExternalUriString("/sdcard/Download/file.txt"))
    }

    @Test
    fun rejectsDoubleEncodedFilePayload() {
        assertFalse(
            ExternalIntentValidator.isTrustedExternalUriString("content://provider/doc/file%253A%252F%252Fsdcard%252Fevil")
        )
    }

    @Test
    fun acceptsNormalContentUri() {
        assertTrue(ExternalIntentValidator.isTrustedExternalUriString("content://provider/doc/123"))
    }

    @Test
    fun `opaque local content uri remains eligible for raw package handoff`() {
        val uri = "content://provider/media/file%3A%2F%2Fstorage%2Femulated%2F0%2Fapp.apk"

        assertTrue(ExternalIntentValidator.isSupportedLocalExternalUriString(uri))
        assertFalse(ExternalIntentValidator.isTrustedExternalUriString(uri))
    }
}
