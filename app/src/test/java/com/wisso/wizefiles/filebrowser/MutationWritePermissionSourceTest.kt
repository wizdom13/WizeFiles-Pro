// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.filebrowser

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MutationWritePermissionSourceTest {

    @Test
    fun editIntentKeepsExplicitReadWriteGrant() {
        val intentExtensions = sourceFile("src/main/java/com/wisso/wizefiles/util/IntentExtensions.kt")

        assertTrue(intentExtensions.contains("Intent(Intent.ACTION_EDIT)"))
        assertTrue(
            intentExtensions.contains(
                "Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"
            )
        )
    }

    @Test
    fun filePickerOnlyAddsWriteGrantWhenCallerIsNotReadOnly() {
        val pickerCoordinator = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListPickerCoordinator.kt"
        )

        assertTrue(pickerCoordinator.contains("if (!options.readOnly)"))
        assertTrue(pickerCoordinator.contains("flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"))
    }

    @Test
    fun documentTreePermissionRequestsWriteWithReadFallback() {
        val documentTreeUri = sourceFile("src/main/java/com/wisso/wizefiles/core/files/uri/DocumentTreeUri.kt")

        assertTrue(
            documentTreeUri.contains(
                "Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"
            )
        )
        assertTrue(documentTreeUri.contains(") || value.takePersistablePermission(Intent.FLAG_GRANT_READ_URI_PERMISSION)"))
    }

    @Test
    fun writeGrantIsNotAddedToViewIntents() {
        val intentExtensions = sourceFile("src/main/java/com/wisso/wizefiles/util/IntentExtensions.kt")

        assertFalse(
            intentExtensions.contains(
                "createViewIntent(mimeType: MimeType): Intent =\n    Intent(Intent.ACTION_VIEW)\n        .setDataAndType(this, mimeType.intentType)\n        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)"
            )
        )
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) {
            return direct.readText()
        }
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) {
            return fromRepoRoot.readText()
        }
        throw java.io.FileNotFoundException(path)
    }
}
