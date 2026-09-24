// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.viewer

import java.nio.file.Files
import java.nio.file.Path
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaEditorRemovalSourceTest {

    @Test
    fun `built-in media editor wiring and dependencies remain removed`() {
        val manifest = readProjectFile(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml"
        )
        val buildGradle = readProjectFile(
            "build.gradle",
            "app/build.gradle"
        )
        val strings = readProjectFile(
            "src/main/res/values/strings.xml",
            "app/src/main/res/values/strings.xml"
        )

        listOf(
            "viewer.imageedit.ImageEditorActivity",
            "viewer.imageedit.ImageCropActivity",
            "viewer.videoedit.VideoEditorActivity"
        ).forEach { assertFalse(manifest.contains(it)) }

        listOf(
            "androidx.media3:media3-effect",
            "androidx.media3:media3-transformer",
            "com.burhanrashid52:photoeditor",
            "com.vanniktech:android-image-cropper"
        ).forEach { assertFalse(buildGradle.contains(it)) }

        assertTrue(buildGradle.contains("androidx.media3:media3-session"))
        assertFalse(strings.contains("name=\"image_editor_"))
        assertFalse(strings.contains("name=\"video_editor_"))
    }

    @Test
    fun `external editing remains delegated through Android ACTION_EDIT`() {
        val manifest = readProjectFile(
            "src/main/AndroidManifest.xml",
            "app/src/main/AndroidManifest.xml"
        )
        val intentExtensions = readProjectFile(
            "src/main/java/com/wisso/wizefiles/util/IntentExtensions.kt",
            "app/src/main/java/com/wisso/wizefiles/util/IntentExtensions.kt"
        )

        assertTrue(manifest.contains("com.wisso.wizefiles.feature.filebrowser.EditFileActivity"))
        assertTrue(intentExtensions.contains("Intent(Intent.ACTION_EDIT)"))
        assertTrue(
            intentExtensions.contains(
                "Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION"
            )
        )
    }

    private fun readProjectFile(vararg candidates: String): String {
        for (candidate in candidates) {
            val path = Path.of(candidate)
            if (Files.exists(path)) return String(Files.readAllBytes(path))
        }
        error("Unable to locate any candidate paths: ${candidates.joinToString()}")
    }
}
