// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import android.view.View
import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.core.files.model.FileItem
import com.wisso.wizefiles.feature.audioplayer.SetRingtoneActivity
import com.wisso.wizefiles.storage.FileMetadata
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.File
import java.text.Collator
import java.nio.file.Paths
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BrowserRingtoneActionTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `single audio selection shows set as ringtone`() {
        val popup = PopupMenu(context, View(context)).apply {
            menuInflater.inflate(R.menu.menu_file_list_select, menu)
        }

        BrowserSelectionMenuConfigurator.configure(
            menu = popup.menu,
            files = fileItemSetOf(fileItem("song.mp3", "audio/mpeg")),
            pickOptions = null,
            currentPath = Paths.get("/storage/emulated/0/Music"),
            inRecycleBin = false,
            otherPanePath = null,
            isWritableLocation = { true },
            isMutableFile = { true }
        )

        assertTrue(popup.menu.findItem(R.id.action_set_ringtone).isVisible)
    }

    @Test
    fun `non audio selection hides set as ringtone`() {
        val popup = PopupMenu(context, View(context)).apply {
            menuInflater.inflate(R.menu.menu_file_list_select, menu)
        }

        BrowserSelectionMenuConfigurator.configure(
            menu = popup.menu,
            files = fileItemSetOf(fileItem("notes.txt", "text/plain")),
            pickOptions = null,
            currentPath = Paths.get("/storage/emulated/0/Documents"),
            inRecycleBin = false,
            otherPanePath = null,
            isWritableLocation = { true },
            isMutableFile = { true }
        )

        assertFalse(popup.menu.findItem(R.id.action_set_ringtone).isVisible)
    }

    @Test
    fun `ringtone action launches shared ringtone flow and clears selection`() {
        val selected = fileItem("song.mp3", "audio/mpeg")
        var launched: Intent? = null
        var cleared = false

        val handled = BrowserPackageSelectionActionHandler.handle(
            itemId = R.id.action_set_ringtone,
            file = selected,
            context = context,
            ensurePackageSigningAccess = { false },
            launch = { launched = it },
            clearSelection = { cleared = true }
        )

        assertEquals(true, handled)
        assertEquals(SetRingtoneActivity::class.java.name, launched?.component?.className)
        assertEquals("audio/mpeg", launched?.type)
        assertTrue(cleared)
    }

    private fun fileItem(name: String, mimeType: String): FileItem = FileItem(
        path = LocalAppPath(File("/storage/emulated/0/$name")),
        nameCollationKey = Collator.getInstance().getCollationKey(name),
        attributesNoFollowLinks = FileMetadata(
            isDirectory = false,
            sizeBytes = 1L,
            lastModifiedEpochMillis = 1L
        ),
        symbolicLinkTarget = null,
        symbolicLinkTargetAttributes = null,
        isHidden = false,
        mimeType = MimeType(mimeType)
    )
}
