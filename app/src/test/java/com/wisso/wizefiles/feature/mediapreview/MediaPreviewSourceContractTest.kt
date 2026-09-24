// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.mediapreview

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPreviewSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `browser defaults to preview while Open with remains external`() {
        val fragment = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListFragment.kt"
        )
        val externalActions = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val previewCall = externalActions.indexOf(
            "InternalOpenIntents.create(context, file, siblings)"
        )
        val externalCall = externalActions.indexOf(
            "openWithIntent(file, withChooser = false)",
            previewCall
        )

        assertTrue(previewCall >= 0)
        assertTrue(externalCall > previewCall)
        assertTrue("private fun openFileWith(file: FileItem)" in fragment)
        assertTrue("externalActionController.openWith(file)" in fragment)
        assertTrue("openWithIntent(file, withChooser = true)" in externalActions)
    }

    @Test
    fun `archive media uses the guarded extraction job before internal preview`() {
        val externalActions = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val service = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationService.kt"
        ) + source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOperationCommandCoordinator.kt"
        )
        val executor = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOpenRenameJobs.kt"
        )

        val extractionPolicy = externalActions.indexOf(
            "InternalOpenPolicy.targetAfterExtraction(file.mimeType, file.path.name)"
        )
        val extractionCall = externalActions.indexOf(
            "FileOperationService.openInternalViewer(legacyPath, file.mimeType, context)"
        )
        val directPreview = externalActions.indexOf(
            "InternalOpenIntents.create(context, file, siblings)"
        )

        assertTrue(extractionPolicy >= 0)
        assertTrue(extractionCall > extractionPolicy)
        assertTrue(directPreview > extractionCall)
        assertTrue("fun openInternalViewer(file: Path, mimeType: MimeType, context: Context)" in service)
        assertTrue("OpenInternalViewerFileOperationJob(file, mimeType)" in service)
        assertTrue("class OpenInternalViewerFileOperationJob(" in executor)
        assertTrue("InternalOpenIntents.create(" in executor)
        assertTrue("MediaPreviewItem(extractedPath, mimeType)" in executor)
    }

    @Test
    fun `preview is private themed and uses a mixed media pager`() {
        val manifest = source("app/src/main/AndroidManifest.xml")
        val activityLayout = source("app/src/main/res/layout/activity_media_preview.xml")
        val pageLayout = source("app/src/main/res/layout/item_media_preview_page.xml")
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewActivity.kt"
        )
        val adapter = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewPagerAdapter.kt"
        )

        assertTrue("feature.mediapreview.MediaPreviewActivity" in manifest)
        assertTrue("android:exported=\"false\"" in manifest)
        assertTrue("androidx.viewpager2.widget.ViewPager2" in activityLayout)
        assertTrue("SubsamplingScaleImageView" in pageLayout)
        assertTrue("PhotoView" in pageLayout)
        assertTrue("androidx.media3.ui.PlayerView" in pageLayout)
        assertTrue("ORIENTATION_USE_EXIF" in adapter)
        assertTrue("setFullscreenButtonClickListener" in activity)
        assertTrue("setPlaybackSpeed" in activity)
        assertTrue("FilePropertiesDialogFragment.show" in activity)
        assertTrue("createViewIntent" in activity)
        assertTrue("registerOnPageChangeCallback" in activity)
        assertTrue("MediaPreviewPagerAdapter" in activity)
        assertFalse("GestureDetector" in activity)
        assertFalse("navigateBy(" in activity)
    }

    @Test
    fun `pager protects zoom gestures and owns only one visible video player`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewActivity.kt"
        )
        val adapter = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewPagerAdapter.kt"
        )
        val stateStore = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewPageStateStore.kt"
        )

        assertTrue("binding.mediaPager.isUserInputEnabled = isAtBaseZoom" in activity)
        assertTrue("fun isAtBaseZoom(): Boolean" in adapter)
        assertTrue("setOnScaleChangeListener" in adapter)
        assertTrue("setOnStateChangedListener" in adapter)
        assertTrue("activeVideoIndex" in activity)
        assertTrue("page.playerView.player = fallbackPlayer" in activity)
        assertTrue("pagerAdapter.pageAt(it)?.playerView?.player = null" in activity)
        assertTrue("MediaPreviewStateViewModel" in stateStore)
        assertTrue("MediaPreviewPageStateStore" in stateStore)
        val fallback = source(
            "app/src/main/java/com/wisso/wizefiles/feature/playback/FallbackMediaPlayer.kt"
        )
        assertTrue("FallbackMediaPlayer.create(" in activity)
        assertFalse("ExoPlayer.Builder" in activity)
        assertTrue(fallback.split("ExoPlayer.Builder").size - 1 == 1)
        assertFalse("ExoPlayer.Builder" in adapter)
    }

    @Test
    fun `dependency boundary allows audio session but excludes media editing`() {
        val gradle = source("app/build.gradle")

        listOf("media3-common", "media3-exoplayer", "media3-session", "media3-ui").forEach {
            assertTrue("androidx.media3:$it" in gradle)
        }
        assertTrue("androidx.viewpager2:viewpager2" in gradle)
        assertTrue("org.videolan.android:libvlc-all:3.7.5" in gradle)
        listOf("media3-effect", "media3-transformer").forEach {
            assertFalse("androidx.media3:$it" in gradle)
        }
    }

    private fun source(path: String): String = File(root, path).readText()
}


