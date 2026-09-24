package com.wisso.wizefiles.feature.mediapreview

import org.junit.Assert.assertEquals
import org.junit.Test

class MediaPreviewPageStateStoreTest {
    @Test
    fun `image rotation is isolated per page and normalized`() {
        val store = MediaPreviewPageStateStore()

        assertEquals(90f, store.rotateClockwise(2))
        assertEquals(180f, store.rotateClockwise(2))
        assertEquals(0f, store.rotationFor(3))

        store.setRotation(3, -90f)
        assertEquals(270f, store.rotationFor(3))
    }

    @Test
    fun `video playback state is restored independently for each page`() {
        val store = MediaPreviewPageStateStore()
        val first = MediaPreviewPlaybackState(12_345L, true, 1.5f)
        val second = MediaPreviewPlaybackState(987L, false, 0.75f)

        store.setPlayback(1, first)
        store.setPlayback(4, second)

        assertEquals(first, store.playbackFor(1))
        assertEquals(second, store.playbackFor(4))
        assertEquals(MediaPreviewPlaybackState(), store.playbackFor(0))
    }

    @Test
    fun `invalid restored playback values are constrained`() {
        val store = MediaPreviewPageStateStore()

        store.setPlayback(
            0,
            MediaPreviewPlaybackState(positionMs = -1L, playWhenReady = true, speed = 20f)
        )

        assertEquals(
            MediaPreviewPlaybackState(positionMs = 0L, playWhenReady = true, speed = 5f),
            store.playbackFor(0)
        )
    }
}
