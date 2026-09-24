package com.wisso.wizefiles.feature.filebrowser

import com.wisso.wizefiles.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BrowserMenuCommandPolicyTest {
    @Test fun `menu resources resolve to stable commands`() {
        assertEquals(BrowserMenuCommandPolicy.Command.PASTE, BrowserMenuCommandPolicy.resolve(R.id.action_paste))
        assertEquals(BrowserMenuCommandPolicy.Command.SYNC_PANES, BrowserMenuCommandPolicy.resolve(R.id.action_sync_panes))
        assertEquals(BrowserMenuCommandPolicy.Command.OPEN_DRAWER, BrowserMenuCommandPolicy.resolve(android.R.id.home))
    }

    @Test fun `unknown resources remain unhandled`() {
        assertNull(BrowserMenuCommandPolicy.resolve(Int.MIN_VALUE))
    }
}
