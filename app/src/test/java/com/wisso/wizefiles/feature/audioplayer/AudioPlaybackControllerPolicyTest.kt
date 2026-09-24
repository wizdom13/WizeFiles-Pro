package com.wisso.wizefiles.feature.audioplayer

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioPlaybackControllerPolicyTest {
    @Test
    fun `application controller is allowed`() {
        assertTrue(AudioPlaybackControllerPolicy.isAllowed(APP_PACKAGE, APP_PACKAGE, isTrusted = false))
    }

    @Test
    fun `trusted system controller is allowed`() {
        assertTrue(AudioPlaybackControllerPolicy.isAllowed(APP_PACKAGE, "android", isTrusted = true))
        assertFalse(AudioPlaybackControllerPolicy.canUseCustomCommands(APP_PACKAGE, "android"))
    }

    @Test
    fun `untrusted external controller is rejected`() {
        assertFalse(AudioPlaybackControllerPolicy.isAllowed(APP_PACKAGE, "example.attacker", isTrusted = false))
        assertFalse(AudioPlaybackControllerPolicy.canUseCustomCommands(APP_PACKAGE, "example.attacker"))
    }

    @Test
    fun `only application controller can use custom commands`() {
        assertTrue(AudioPlaybackControllerPolicy.canUseCustomCommands(APP_PACKAGE, APP_PACKAGE))
    }

    private companion object {
        const val APP_PACKAGE = "com.wisso.wizefiles"
    }
}
