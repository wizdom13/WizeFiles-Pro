// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.app.Application
import android.content.Intent
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.util.extraPath
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class, manifest = Config.NONE)
class FileListPickerCoordinatorRobolectricTest {
    @Test
    fun `open document options preserve caller selection policy`() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .setType("text/plain")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            .putExtra(Intent.EXTRA_LOCAL_ONLY, true)
            .putExtra(FileListActivity.EXTRA_ALLOW_PICK_DIRECTORIES, true)
            .putExtra(FileListActivity.EXTRA_PICK_SELECT_WITH_LONG_PRESS, true)

        val options = requireNotNull(FileListPickerCoordinator.resolveOptions(intent))

        assertEquals(PickOptions.Mode.OPEN_FILE, options.mode)
        assertEquals(listOf(MimeType("text/plain")), options.mimeTypes)
        assertTrue(options.allowMultiple)
        assertTrue(options.localOnly)
        assertTrue(options.allowDirectories)
        assertTrue(options.selectWithLongPress)
        assertFalse(options.readOnly)
    }

    @Test
    fun `create document options derive a file name and remain single selection`() {
        val options = requireNotNull(
            FileListPickerCoordinator.resolveOptions(
                Intent(Intent.ACTION_CREATE_DOCUMENT).setType("text/plain")
            )
        )

        assertEquals(PickOptions.Mode.CREATE_FILE, options.mode)
        assertEquals("file.txt", options.fileName)
        assertFalse(options.allowMultiple)
        assertNull(FileListPickerCoordinator.resolveOptions(Intent(Intent.ACTION_VIEW)))
    }

    @Test
    fun `directory result keeps AppPath data and directory grant flags`() {
        val directory = LocalAppPath(File("/storage/emulated/0/Documents"))
        val options = requireNotNull(
            FileListPickerCoordinator.resolveOptions(Intent(Intent.ACTION_OPEN_DOCUMENT_TREE))
        )

        val result = FileListPickerCoordinator.createResultIntent(
            linkedSetOf(directory),
            options
        )

        assertEquals(directory, result.extraPath)
        assertNull(result.data)
        assertTrue(result.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(result.flags and Intent.FLAG_GRANT_WRITE_URI_PERMISSION != 0)
        assertTrue(result.flags and Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION != 0)
        assertTrue(result.flags and Intent.FLAG_GRANT_PREFIX_URI_PERMISSION != 0)
    }
}
