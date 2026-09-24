package com.wisso.wizefiles.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MediaMetadataRetrieverPathExtensionsTest {

    @Test
    fun `setDataSource runtime failure returns null fallback`() {
        val result = runMediaProbeOrNull(
            setDataSource = { throw RuntimeException("setDataSource failed") },
            readValue = { byteArrayOf(1, 2, 3) }
        )

        assertNull(result)
    }

    @Test
    fun `embedded picture runtime failure returns null fallback`() {
        val result = runMediaProbeOrNull(
            setDataSource = {},
            readValue = { throw RuntimeException("getEmbeddedPicture failed") }
        )

        assertNull(result)
    }

    @Test
    fun `successful probe returns embedded picture bytes`() {
        val expected = byteArrayOf(9, 8, 7)

        val result = runMediaProbeOrNull(
            setDataSource = {},
            readValue = { expected }
        )

        assertEquals(expected.toList(), result?.toList())
    }
}
