package com.wisso.wizefiles.feature.audioplayer

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlayerSourceContractTest {
    private val root = generateSequence(File(System.getProperty("user.dir")!!)) { it.parentFile }
        .first { File(it, "app/src/main/AndroidManifest.xml").isFile }

    @Test
    fun `central media router separates audio from mixed image video preview`() {
        val router = source(
            "app/src/main/java/com/wisso/wizefiles/feature/internalviewer/InternalOpenIntents.kt"
        )
        val previewStore = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewSessionStore.kt"
        )
        val audioStore = source(
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlaybackSessionStore.kt"
        )

        assertTrue("InternalOpenPolicy.Target.AUDIO_PLAYER" in router)
        assertTrue("AudioPlayerIntents.create" in router)
        assertTrue("InternalOpenPolicy.Target.AUDIO_PLAYER," in previewStore)
        assertTrue("candidates.filter { it.isAudio() }" in audioStore)
        assertTrue("MAX_ITEMS_PER_SESSION = 2_000" in audioStore)
    }

    @Test
    fun `archive audio remains guarded and single item after extraction`() {
        val controller = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filebrowser/FileListExternalActionController.kt"
        )
        val executor = source(
            "app/src/main/java/com/wisso/wizefiles/feature/filejobs/FileOpenRenameJobs.kt"
        )

        val extractionPolicy = controller.indexOf(
            "InternalOpenPolicy.targetAfterExtraction(file.mimeType, file.path.name)"
        )
        val extractionCall = controller.indexOf(
            "FileOperationService.openInternalViewer(legacyPath, file.mimeType, context)"
        )
        val routerCall = controller.indexOf(
            "InternalOpenIntents.create(context, file, siblings)"
        )

        assertTrue(extractionPolicy >= 0)
        assertTrue(extractionCall > extractionPolicy)
        assertTrue(routerCall > extractionCall)
        assertTrue("InternalOpenIntents.create(" in executor)
        assertTrue("MediaPreviewItem(extractedPath, mimeType)" in executor)
    }

    @Test
    fun `service owns the player queue focus notification and trusted commands`() {
        val service = source(
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlaybackService.kt"
        )
        val controller = source(
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlaybackController.kt"
        )
        val manifest = source("app/src/main/AndroidManifest.xml")
        val gradle = source("app/build.gradle")

        assertTrue("class AudioPlaybackService : MediaSessionService()" in service)
        val fallback = source(
            "app/src/main/java/com/wisso/wizefiles/feature/playback/FallbackMediaPlayer.kt"
        )
        assertTrue("FallbackMediaPlayer.create(" in service)
        assertFalse("ExoPlayer.Builder" in service)
        assertTrue(fallback.split("ExoPlayer.Builder").size - 1 == 1)
        assertTrue("setAudioAttributes(audioAttributes, true)" in fallback)
        assertTrue("setHandleAudioBecomingNoisy(true)" in fallback)
        assertTrue("repeatMode = Player.REPEAT_MODE_OFF" in service)
        assertTrue("shuffleModeEnabled = false" in service)
        assertTrue("AudioPlaybackControllerPolicy.isAllowed(" in service)
        assertTrue("AudioPlaybackControllerPolicy.canUseCustomCommands(" in service)
        assertTrue("override fun onCustomCommand(" in service)
        assertTrue("sendCustomCommand(" in controller)
        assertFalse("startService(" in controller)
        assertTrue("androidx.media3:media3-session" in gradle)
        assertTrue("android.permission.FOREGROUND_SERVICE_MEDIA_PLAYBACK" in manifest)
        assertTrue("android:foregroundServiceType=\"mediaPlayback\"" in manifest)
        assertTrue("androidx.media3.session.MediaSessionService" in manifest)
    }

    @Test
    fun `activity has queue synchronized artwork transport and file actions`() {
        val activity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlayerActivity.kt"
        )
        val layout = source("app/src/main/res/layout/activity_audio_player.xml")
        val menu = source("app/src/main/res/menu/menu_audio_player.xml")

        assertTrue("androidx.viewpager2.widget.ViewPager2" in layout)
        assertTrue("android:id=\"@+id/seekBar\"" in layout)
        assertTrue("android:id=\"@+id/previousButton\"" in layout)
        assertTrue("android:id=\"@+id/playPauseButton\"" in layout)
        assertTrue("android:id=\"@+id/nextButton\"" in layout)
        assertTrue("registerOnPageChangeCallback" in activity)
        assertTrue("playbackController.playSourceIndex(position)" in activity)
        assertTrue("onMediaItemTransition" in activity)
        assertTrue("onMediaMetadataChanged" in activity)
        assertTrue("FilePropertiesDialogFragment.show" in activity)
        assertTrue("createSendStreamIntent" in activity)
        assertTrue("createViewIntent" in activity)
        assertTrue("action_audio_stop" in menu)
        assertFalse("ExoPlayer.Builder" in activity)
    }

    @Test
    fun `video and audio players both participate in audio focus`() {
        val audioService = source(
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlaybackService.kt"
        )
        val videoActivity = source(
            "app/src/main/java/com/wisso/wizefiles/feature/mediapreview/MediaPreviewActivity.kt"
        )

        val fallback = source(
            "app/src/main/java/com/wisso/wizefiles/feature/playback/FallbackMediaPlayer.kt"
        )
        assertTrue("FallbackMediaPlayer.create(" in audioService)
        assertTrue("FallbackMediaPlayer.create(" in videoActivity)
        assertTrue("LibVlcAudioFocus" in fallback)
        assertTrue("ACTION_AUDIO_BECOMING_NOISY" in fallback)
    }

    @Test
    fun `music library scope remains excluded`() {
        val audioSources = listOf(
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlaybackService.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlayerActivity.kt",
            "app/src/main/java/com/wisso/wizefiles/feature/audioplayer/AudioPlaybackSessionStore.kt"
        ).joinToString("\n", transform = ::source)

        listOf("MediaStore", "equalizer", "lyrics", "crossfade", "REPEAT_MODE_ALL").forEach {
            assertFalse(it in audioSources)
        }
    }

    private fun source(path: String): String = File(root, path).readText()
}
