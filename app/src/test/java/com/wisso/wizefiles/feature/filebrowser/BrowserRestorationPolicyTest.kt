package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserRestorationPolicyTest {
    @Test fun `root cannot navigate up but a child can`() {
        val root = Paths.get("/storage/root")
        assertFalse(BrowserRestorationPolicy.shouldNavigateUp(root, root))
        assertTrue(BrowserRestorationPolicy.shouldNavigateUp(root.resolve("child"), root))
    }

    @Test fun `restored path outside configured root never navigates through that root`() {
        assertFalse(
            BrowserRestorationPolicy.shouldNavigateUp(
                Paths.get("/different/location"),
                Paths.get("/storage/root")
            )
        )
    }

    @Test fun `scroll policy transitions only on direction changes`() {
        assertTrue(BrowserScrollPolicy.shouldHideSpeedDial(1, false))
        assertFalse(BrowserScrollPolicy.shouldHideSpeedDial(1, true))
        assertTrue(BrowserScrollPolicy.shouldShowSpeedDial(-1, true))
        assertFalse(BrowserScrollPolicy.shouldShowSpeedDial(0, true))
    }
}
