// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Intent
import com.wisso.wizefiles.core.files.mime.MimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PickerResultFlagsTest {

    @Test
    fun readOnlyPickResultDoesNotGrantWrite() {
        val flags = FileListPickerCoordinator.buildResultFlags(
            PickOptions(
                mode = PickOptions.Mode.OPEN_FILE,
                fileName = null,
                readOnly = true,
                mimeTypes = listOf(MimeType("*/*")),
                localOnly = false,
                allowMultiple = false
            )
        )

        assertTrue(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertEquals(0, flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
    }

    @Test
    fun writableOpenDirectoryGrantsWriteAndPrefix() {
        val flags = FileListPickerCoordinator.buildResultFlags(
            PickOptions(
                mode = PickOptions.Mode.OPEN_DIRECTORY,
                fileName = null,
                readOnly = false,
                mimeTypes = emptyList(),
                localOnly = false,
                allowMultiple = false
            )
        )

        assertTrue(flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
    }
}
