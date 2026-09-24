package com.wisso.wizefiles.feature.audioplayer

import com.wisso.wizefiles.core.files.mime.MimeType
import com.wisso.wizefiles.storage.path.LocalAppPath
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackSessionStoreTest {
    @Test
    fun `audio siblings retain browser order and exclude other media`() {
        val first = item("first.mp3", "audio/mpeg")
        val image = item("cover.jpg", "image/jpeg")
        val current = item("current.flac", "audio/flac")
        val video = item("clip.mp4", "video/mp4")
        val last = item("last.m4a", "audio/mp4")

        val resolved = AudioPlaybackSessionStore.resolveSiblings(
            listOf(first, image, current, video, last),
            current
        )

        assertEquals(listOf(first, current, last), resolved)
    }

    @Test
    fun `specialist extensions join a generic mime audio queue`() {
        val first = item("first.wv", "application/octet-stream")
        val current = item("current.dsf", "application/octet-stream")
        val image = item("cover.tiff", "application/octet-stream")
        val last = item("last.ape", "application/octet-stream")

        assertEquals(
            listOf(first, current, last),
            AudioPlaybackSessionStore.resolveSiblings(
                listOf(first, current, image, last),
                current
            )
        )
    }

    @Test
    fun `missing current audio falls back to a one item recovery queue`() {
        val current = item("current.ogg", "audio/ogg")

        assertEquals(
            listOf(current),
            AudioPlaybackSessionStore.resolveSiblings(
                listOf(item("other.mp3", "audio/mpeg")),
                current
            )
        )
    }

    @Test
    fun `non audio current never creates an audio queue`() {
        val current = item("notes.pdf", "application/pdf")

        assertEquals(
            listOf(current),
            AudioPlaybackSessionStore.resolveSiblings(
                listOf(item("song.mp3", "audio/mpeg"), current),
                current
            )
        )
    }

    @Test
    fun `large audio folders are bounded while retaining current item`() {
        val candidates = (0 until 2_500).map { item("track-$it.mp3", "audio/mpeg") }
        val current = candidates[2_200]

        val resolved = AudioPlaybackSessionStore.resolveSiblings(candidates, current)

        assertEquals(2_000, resolved.size)
        assertTrue(current in resolved)
    }

    @Test
    fun `session supports lookup and service owned cleanup`() {
        val current = item("current.wav", "audio/wav")
        val id = AudioPlaybackSessionStore.create(listOf(current), current)

        assertNotNull(AudioPlaybackSessionStore.get(id))
        AudioPlaybackSessionStore.remove(id)
        assertNull(AudioPlaybackSessionStore.get(id))
    }

    private fun item(name: String, mimeType: String) = AudioPlaybackItem(
        LocalAppPath(File("/audio-player-tests/$name")),
        MimeType(mimeType)
    )
}

