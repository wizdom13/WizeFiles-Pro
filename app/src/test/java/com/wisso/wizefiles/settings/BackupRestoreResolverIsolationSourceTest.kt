// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.settings

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupRestoreResolverIsolationSourceTest {

    @Test
    fun `backup share uses dedicated backup mime type`() {
        val backupDialog = sourceFile(
            "src/main/java/com/wisso/wizefiles/feature/settings/BackupSettingsDialogFragment.kt"
        )
        assertTrue(backupDialog.contains("createSendStreamIntent(listOf(MimeType.WIZEFILES_BACKUP))"))
    }

    @Test
    fun `named external aliases share one internal router`() {
        val manifest = sourceFile("src/main/AndroidManifest.xml")
        val routerClass = "com.wisso.wizefiles.feature.filebrowser.ExternalViewRouterActivity"
        val router = activityBlock(manifest, routerClass)

        assertTrue(router.contains("android:exported=\"false\""))
        assertFalse(router.contains("android.intent.action.VIEW"))

        val aliases = listOf(
            "com.wisso.wizefiles.feature.filebrowser.ExternalAudioPlayer",
            "com.wisso.wizefiles.feature.filebrowser.ExternalVideoPlayer",
            "com.wisso.wizefiles.feature.filebrowser.ExternalTextEditor",
            "com.wisso.wizefiles.feature.filebrowser.ExternalPackageInstaller",
            "com.wisso.wizefiles.feature.filebrowser.ExternalArchiveBrowser",
            "com.wisso.wizefiles.feature.filebrowser.ExternalFolderBrowser",
            "com.wisso.wizefiles.feature.filebrowser.ExternalBackupRestore",
            "com.wisso.wizefiles.feature.filebrowser.ExternalFileBrowser"
        )
        aliases.forEach { aliasClass ->
            val alias = activityAliasBlock(manifest, aliasClass)
            assertTrue(alias.contains("android:exported=\"true\""))
            assertTrue(alias.contains("android:targetActivity=\"$routerClass\""))
            assertTrue(alias.contains("android.intent.action.VIEW"))
        }

        val backupAlias = activityAliasBlock(
            manifest,
            "com.wisso.wizefiles.feature.filebrowser.ExternalBackupRestore"
        )
        assertTrue(backupAlias.contains("application/x-wizefiles-backup"))
        assertTrue(backupAlias.contains("android:pathPattern=\".*\\.wzf\""))

        val fileBrowserAlias = activityAliasBlock(
            manifest,
            "com.wisso.wizefiles.feature.filebrowser.ExternalFileBrowser"
        )
        assertTrue(fileBrowserAlias.contains("application/octet-stream"))

        assertFalse(manifest.contains("com.wisso.wizefiles.settings.RestoreSettingsEntryActivity"))
    }

    @Test
    fun `browser and text editor no longer compete for public view intents`() {
        val manifest = sourceFile("src/main/AndroidManifest.xml")
        val browser = activityBlock(
            manifest,
            "com.wisso.wizefiles.feature.filebrowser.FileListActivity"
        )
        val textEditor = activityBlock(
            manifest,
            "com.wisso.wizefiles.viewer.text.TextEditorActivity"
        )

        assertFalse(browser.contains("android.intent.action.VIEW"))
        assertTrue(browser.contains("android.intent.category.LAUNCHER"))
        assertFalse(textEditor.contains("android.intent.action.VIEW"))
        assertTrue(textEditor.contains("android:exported=\"false\""))
    }

    private fun activityBlock(manifest: String, className: String): String =
        elementBlock(manifest, "activity", className)

    private fun activityAliasBlock(manifest: String, className: String): String =
        elementBlock(manifest, "activity-alias", className)

    private fun elementBlock(manifest: String, element: String, className: String): String {
        val nameIndex = manifest.indexOf("android:name=\"$className\"")
        require(nameIndex >= 0) { "Missing $element $className" }
        val start = manifest.lastIndexOf("<$element", nameIndex)
        require(start >= 0) { "Missing <$element for $className" }
        val openingTagEnd = manifest.indexOf('>', nameIndex)
        require(openingTagEnd >= 0) { "Unterminated opening tag for $className" }
        val openingTag = manifest.substring(start, openingTagEnd + 1)
        val closingTag = "</$element>"
        val regularEnd = manifest.indexOf(closingTag, openingTagEnd)
        val end = if (openingTag.trimEnd().endsWith("/>")) {
            openingTagEnd + 1
        } else {
            require(regularEnd >= 0) { "Unterminated $element $className" }
            regularEnd + closingTag.length
        }
        return manifest.substring(start, end)
    }

    private fun sourceFile(path: String): String {
        val direct = File(path)
        if (direct.exists()) return direct.readText()
        val fromRepoRoot = File("app", path)
        if (fromRepoRoot.exists()) return fromRepoRoot.readText()
        throw java.io.FileNotFoundException(path)
    }
}
