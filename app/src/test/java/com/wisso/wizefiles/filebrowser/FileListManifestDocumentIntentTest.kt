// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Test

class FileListManifestDocumentIntentTest {

    @Test
    fun manifestDoesNotAdvertiseUnsupportedPickerActionsOrAnyDocumentsProvider() {
        val manifest = readProjectFile("src/main/AndroidManifest.xml", "app/src/main/AndroidManifest.xml")

        assertFalse(manifest.contains("<action android:name=\"android.intent.action.GET_CONTENT\" />"))
        assertFalse(manifest.contains("<action android:name=\"android.intent.action.OPEN_DOCUMENT\" />"))
        assertFalse(manifest.contains("<action android:name=\"android.intent.action.CREATE_DOCUMENT\" />"))
        assertFalse(manifest.contains("<action android:name=\"android.intent.action.OPEN_DOCUMENT_TREE\" />"))
        assertFalse(manifest.contains("android.content.action.DOCUMENTS_PROVIDER"))
        assertFalse(manifest.contains("android.permission.MANAGE_DOCUMENTS"))
    }

    private fun readProjectFile(vararg candidates: String): String {
        val file = candidates.map(::File).firstOrNull { it.exists() }
            ?: error("Unable to locate any of: ${candidates.joinToString()}")
        return file.readText()
    }
}
