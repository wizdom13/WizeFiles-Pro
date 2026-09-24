// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Intent
import android.os.Bundle
import com.wisso.wizefiles.provider.archive.ArchiveDisplayNameRegistry
import com.wisso.wizefiles.provider.archive.archiveFile
import com.wisso.wizefiles.provider.archive.createArchiveRootPath
import com.wisso.wizefiles.storage.path.AppPath
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.storage.path.RawAppPath
import com.wisso.wizefiles.storage.path.toAppPath
import java.io.File
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserStateRestorerTest {
    @Before
    fun setUp() {
        ArchiveDisplayNameRegistry.clearForTests()
    }

    @After
    fun tearDown() {
        ArchiveDisplayNameRegistry.clearForTests()
    }

    @Test
    fun `unresolved requested path reports unavailable and leaves navigation unset`() {
        val restored = restore(RawAppPath("unknown:/target"))

        assertNull(restored.path)
        assertTrue(restored.unavailable)
    }

    @Test
    fun `resolved local requested path is restored without unavailable result`() {
        val requested = File("/storage/emulated/0/Documents")

        val restored = restore(LocalAppPath(requested))

        assertEquals(requested.path, restored.path?.toString())
        assertFalse(restored.unavailable)
    }

    @Test
    fun `external archive display name is kept separately from source identity`() {
        val source = File.createTempFile("opaque-provider-id-82877", ".zip")
        try {
            val intent = Intent(Intent.ACTION_VIEW)
                .putExtra(EXTRA_EXTERNAL_DISPLAY_NAME, "shared-archive.zip")
            val restored = BrowserStateRestorer.restore(
                savedState = null,
                arguments = Bundle(),
                intent = intent,
                argumentPath = LocalAppPath(source),
                stateKey = "path",
                downloadsAction = "downloads",
                shouldOpenArchive = { _, _, _ -> true }
            )
            val archiveRoot = requireNotNull(restored.path)

            assertEquals("shared-archive.zip", archiveRoot.name)
            assertEquals(source.name, archiveRoot.archiveFile.name)
        } finally {
            source.delete()
        }
    }

    @Test
    fun `restored archive path rebinds external display name from launch metadata`() {
        val source = File.createTempFile("opaque-provider-id-82983", ".zip")
        try {
            val archiveRoot = source.toPath().createArchiveRootPath()
            val savedState = Bundle().apply { putParcelable("path", archiveRoot.toAppPath()) }
            val intent = Intent(Intent.ACTION_VIEW)
                .putExtra(EXTRA_EXTERNAL_DISPLAY_NAME, "restored-archive.zip")

            val restored = BrowserStateRestorer.restore(
                savedState = savedState,
                arguments = Bundle(),
                intent = intent,
                argumentPath = null,
                stateKey = "path",
                downloadsAction = "downloads",
                shouldOpenArchive = { _, _, _ -> false }
            )

            assertEquals("restored-archive.zip", restored.path?.name)
        } finally {
            source.delete()
        }
    }

    private fun restore(path: AppPath) = BrowserStateRestorer.restore(
        savedState = null,
        arguments = Bundle(),
        intent = Intent(),
        argumentPath = path,
        stateKey = "path",
        downloadsAction = "downloads",
        shouldOpenArchive = { _, _, _ -> false }
    )
}
