// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.pdfviewer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewItem
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.util.extraPath
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PdfViewerIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `pdf launch is explicit and retains only path and mime type`() {
        val current = item("document.pdf", "application/pdf")
        val intent = PdfViewerIntents.create(context, current)

        assertNotNull(intent)
        val launchIntent = requireNotNull(intent)
        assertEquals(PdfViewerActivity::class.java.name, launchIntent.component?.className)
        assertEquals("application/pdf", launchIntent.type)
        assertEquals(current.path.rawPath, launchIntent.extraPath?.rawPath)
    }

    @Test
    fun `non pdf files are rejected by the pdf destination`() {
        assertNull(PdfViewerIntents.create(context, item("photo.jpg", "image/jpeg")))
    }

    private fun item(name: String, mimeType: String) = MediaPreviewItem(
        LocalAppPath(File("/pdf-viewer-tests/$name")),
        MimeType(mimeType)
    )
}
