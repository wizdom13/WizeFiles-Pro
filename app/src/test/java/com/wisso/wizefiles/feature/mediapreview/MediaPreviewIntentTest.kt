package com.wisso.wizefiles.feature.mediapreview

import android.content.Context
import android.content.Intent
import androidx.test.core.app.ApplicationProvider
import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.storage.path.LocalAppPath
import com.wisso.wizefiles.util.extraPath
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class MediaPreviewIntentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `image launch is explicit and keeps siblings outside extras`() {
        val current = item("current.jpg", "image/jpeg")
        val intent = MediaPreviewIntents.create(
            context,
            current,
            listOf(item("first.jpg", "image/jpeg"), current)
        )

        assertNotNull(intent)
        val launchIntent = requireNotNull(intent)
        assertEquals(MediaPreviewActivity::class.java.name, launchIntent.component?.className)
        assertEquals("image/jpeg", launchIntent.type)
        assertEquals(current.path.rawPath, launchIntent.extraPath?.rawPath)
        assertFalse(launchIntent.extras!!.keySet().any { it.contains("LIST", ignoreCase = true) })
        MediaPreviewSessionStore.remove(
            launchIntent.getStringExtra(MediaPreviewIntents.EXTRA_SESSION_ID)
        )
    }

    @Test
    fun `video launches internally while audio remains delegated`() {
        assertNotNull(
            MediaPreviewIntents.create(context, item("clip.mp4", "video/mp4"))
        )
        assertNull(
            MediaPreviewIntents.create(context, item("song.mp3", "audio/mpeg"))
        )
    }

    @Test
    fun `invalid launch can finish and destroy before view binding initializes`() {
        val controller = Robolectric.buildActivity(
            MediaPreviewActivity::class.java,
            Intent(context, MediaPreviewActivity::class.java)
        )

        controller.create()
        assertTrue(controller.get().isFinishing)
        controller.destroy()
    }

    private fun item(name: String, mimeType: String) = MediaPreviewItem(
        LocalAppPath(File("/media-preview-tests/$name")),
        MimeType(mimeType)
    )
}
