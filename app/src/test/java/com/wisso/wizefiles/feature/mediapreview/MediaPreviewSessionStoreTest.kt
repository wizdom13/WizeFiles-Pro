package com.wisso.wizefiles.feature.mediapreview

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPreviewSessionStoreTest {
    @Test
    fun `mixed image and video siblings retain browser order`() {
        val first = item("first.jpg", "image/jpeg")
        val advanced = item("scan.tiff", "application/octet-stream")
        val video = item("clip.mp4", "video/mp4")
        val document = item("notes.pdf", "application/pdf")
        val current = item("current.png", "image/png")
        val secondVideo = item("second.webm", "video/webm")

        val resolved = MediaPreviewSessionStore.resolveSiblings(
            listOf(first, advanced, video, document, current, secondVideo),
            current
        )

        assertEquals(listOf(first, advanced, video, current, secondVideo), resolved)
    }

    @Test
    fun `missing current item falls back to a one item process death session`() {
        val current = item("current.jpg", "image/jpeg")

        assertEquals(
            listOf(current),
            MediaPreviewSessionStore.resolveSiblings(
                listOf(item("other.pdf", "application/pdf")),
                current
            )
        )
    }

    @Test
    fun `unsupported current item never creates a media sibling session`() {
        val current = item("notes.pdf", "application/pdf")

        assertEquals(
            listOf(current),
            MediaPreviewSessionStore.resolveSiblings(
                listOf(item("photo.jpg", "image/jpeg"), current),
                current
            )
        )
    }

    @Test
    fun `very large mixed folders are bounded while retaining the current item`() {
        val candidates = (0 until 2_500).map {
            if (it % 2 == 0) item("image-$it.jpg", "image/jpeg")
            else item("video-$it.mp4", "video/mp4")
        }
        val current = candidates[2_200]

        val resolved = MediaPreviewSessionStore.resolveSiblings(candidates, current)

        assertEquals(2_000, resolved.size)
        assertTrue(current in resolved)
    }

    @Test
    fun `session lifecycle supports lookup and explicit cleanup`() {
        val current = item("current.webm", "video/webm")
        val id = MediaPreviewSessionStore.create(listOf(current), current)

        assertNotNull(MediaPreviewSessionStore.get(id))
        MediaPreviewSessionStore.remove(id)
        assertNull(MediaPreviewSessionStore.get(id))
    }

    private fun item(name: String, mimeType: String) = MediaPreviewItem(
        LocalAppPath(File("/media-preview-tests/$name")),
        MimeType(mimeType)
    )
}
