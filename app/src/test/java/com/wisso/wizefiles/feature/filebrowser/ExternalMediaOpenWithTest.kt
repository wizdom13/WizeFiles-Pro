package com.wisso.wizefiles.feature.filebrowser

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.feature.audioplayer.AudioPlayerActivity
import com.wisso.wizefiles.feature.mediapreview.MediaPreviewActivity
import com.wisso.wizefiles.feature.packageinstaller.PackageInstallerActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class ExternalMediaOpenWithTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `external audio keeps its uri and opens the audio player`() {
        val sourceUri = Uri.parse("file:///sdcard/Music/song.mp3")
        val sourceIntent = Intent(Intent.ACTION_VIEW).setDataAndType(sourceUri, "audio/mpeg")
        val controller = Robolectric.buildActivity(ExternalViewRouterActivity::class.java, sourceIntent)
            .create()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(started)
        assertEquals(AudioPlayerActivity::class.java.name, started.component?.className)
        assertEquals(sourceUri, started.data)
        assertEquals("audio/mpeg", started.type)
        controller.destroy()
    }

    @Test
    fun `external video keeps its uri and opens the video player`() {
        val sourceUri = Uri.parse("file:///sdcard/Movies/movie.mp4")
        val sourceIntent = Intent(Intent.ACTION_VIEW).setDataAndType(sourceUri, "video/mp4")
        val controller = Robolectric.buildActivity(ExternalViewRouterActivity::class.java, sourceIntent)
            .create()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(started)
        assertEquals(MediaPreviewActivity::class.java.name, started.component?.className)
        assertEquals(sourceUri, started.data)
        assertEquals("video/mp4", started.type)
        controller.destroy()
    }

    @Test
    fun `external apk from opaque content provider opens the package installer`() {
        val sourceUri = Uri.parse(
            "content://org.telegram.messenger.provider/media/" +
                "file%3A%2F%2Fstorage%2Femulated%2F0%2FDownload%2Fapp.apk"
        )
        val sourceIntent = Intent(Intent.ACTION_VIEW).setDataAndType(
            sourceUri,
            "application/vnd.android.package-archive"
        )
        val controller = Robolectric.buildActivity(ExternalViewRouterActivity::class.java, sourceIntent)
            .create()

        val started = shadowOf(controller.get()).nextStartedActivity
        assertNotNull(started)
        assertEquals(PackageInstallerActivity::class.java.name, started.component?.className)
        assertEquals(sourceUri, started.data)
        assertTrue(started.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        controller.destroy()
    }

    @Test
    fun `open with exposes named handlers instead of the generic app label`() {
        assertEquals(
            listOf("Audio Player"),
            labelsFor("content://external.test/song.mp3", "audio/mpeg")
        )
        assertEquals(
            listOf("Video Player"),
            labelsFor("content://external.test/movie.mp4", "video/mp4")
        )
        assertEquals(
            listOf("Package Installer"),
            labelsFor(
                "content://external.test/app.apk",
                "application/vnd.android.package-archive"
            )
        )
    }

    private fun labelsFor(uri: String, mimeType: String): List<String> {
        val intent = Intent(Intent.ACTION_VIEW).setDataAndType(Uri.parse(uri), mimeType)
        return context.packageManager
            .queryIntentActivities(intent, PackageManager.MATCH_DEFAULT_ONLY)
            .filter { it.activityInfo.packageName == context.packageName }
            .map { it.loadLabel(context.packageManager).toString() }
            .distinct()
            .sorted()
    }
}
