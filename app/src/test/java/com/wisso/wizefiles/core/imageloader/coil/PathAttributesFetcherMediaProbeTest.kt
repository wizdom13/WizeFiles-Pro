package com.wisso.wizefiles.core.imageloader.coil

import com.wisso.wizefiles.core.files.mime.asMimeType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PathAttributesFetcherMediaProbeTest {

    @Test
    fun `video mime does not probe embedded picture`() {
        assertFalse(shouldProbeEmbeddedPicture("video/mp4".asMimeType()))
    }

    @Test
    fun `audio mime probes embedded picture`() {
        assertTrue(shouldProbeEmbeddedPicture("audio/mpeg".asMimeType()))
    }

    @Test
    fun `runtime video probe failure falls back to null`() {
        val result = runRuntimeMediaProbeOrNull<String> {
            throw RuntimeException("setDataSource failed")
        }

        assertNull(result)
    }

    @Test
    fun `successful video probe returns value`() {
        val result = runRuntimeMediaProbeOrNull { "thumbnail" }

        assertEquals("thumbnail", result)
    }
}
