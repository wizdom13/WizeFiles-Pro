// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.audioplayer

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioRingtoneSetterTest {
    @Test
    fun `display name is reduced to a safe leaf`() {
        assertEquals("song.mp3", AudioRingtoneSetter.safeDisplayName("/Music/song.mp3"))
        assertEquals("tone.ogg", AudioRingtoneSetter.safeDisplayName("folder\\tone.ogg"))
    }

    @Test
    fun `dot paths and empty names use ringtone fallback`() {
        assertEquals("ringtone", AudioRingtoneSetter.safeDisplayName(".."))
        assertEquals("ringtone", AudioRingtoneSetter.safeDisplayName("."))
        assertEquals("ringtone", AudioRingtoneSetter.safeDisplayName("   "))
    }
}
