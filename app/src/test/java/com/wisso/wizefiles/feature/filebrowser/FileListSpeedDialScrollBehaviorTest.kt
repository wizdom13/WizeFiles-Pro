package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FileListSpeedDialScrollBehaviorTest {
    @Test
    fun shouldHideSpeedDialOnScroll_returnsTrue_onlyWhenScrollingDownAndFabIsVisible() {
        assertTrue(BrowserScrollPolicy.shouldHideSpeedDial(deltaY = 5, hiddenByScroll = false))
        assertFalse(BrowserScrollPolicy.shouldHideSpeedDial(deltaY = 0, hiddenByScroll = false))
        assertFalse(BrowserScrollPolicy.shouldHideSpeedDial(deltaY = -5, hiddenByScroll = false))
        assertFalse(BrowserScrollPolicy.shouldHideSpeedDial(deltaY = 5, hiddenByScroll = true))
    }

    @Test
    fun shouldShowSpeedDialOnScroll_returnsTrue_onlyWhenScrollingUpAndFabIsHidden() {
        assertTrue(BrowserScrollPolicy.shouldShowSpeedDial(deltaY = -5, hiddenByScroll = true))
        assertFalse(BrowserScrollPolicy.shouldShowSpeedDial(deltaY = 0, hiddenByScroll = true))
        assertFalse(BrowserScrollPolicy.shouldShowSpeedDial(deltaY = 5, hiddenByScroll = true))
        assertFalse(BrowserScrollPolicy.shouldShowSpeedDial(deltaY = -5, hiddenByScroll = false))
    }
}
