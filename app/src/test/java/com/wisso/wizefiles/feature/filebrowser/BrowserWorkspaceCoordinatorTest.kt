package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class BrowserWorkspaceCoordinatorTest {
    @Test
    fun `single pane always normalizes the active pane to primary`() {
        val coordinator = BrowserWorkspaceCoordinator()

        val change = coordinator.updateVisibility(false, BrowserPane.SECONDARY)

        assertFalse(change.dualPaneVisible)
        assertEquals(BrowserPane.PRIMARY, change.activePane)
        assertFalse(coordinator.activate(BrowserPane.SECONDARY))
    }

    @Test
    fun `dual pane activation changes only when a different pane is selected`() {
        val coordinator = BrowserWorkspaceCoordinator()
        coordinator.updateVisibility(true, BrowserPane.PRIMARY)

        assertTrue(coordinator.activate(BrowserPane.SECONDARY))
        assertEquals(BrowserPane.SECONDARY, coordinator.activePane)
        assertFalse(coordinator.activate(BrowserPane.SECONDARY))
    }

    @Test
    fun `selecting a pane routes shell actions and selection state to that pane`() {
        val coordinator = BrowserWorkspaceCoordinator()
        val primary = FileListFragment()
        val secondary = FileListFragment()
        coordinator.updateVisibility(true, BrowserPane.PRIMARY)

        assertSame(primary, coordinator.active(primary, secondary))
        assertSame(secondary, coordinator.other(primary, secondary))

        assertTrue(coordinator.activate(BrowserPane.SECONDARY))

        assertSame(secondary, coordinator.active(primary, secondary))
        assertSame(primary, coordinator.other(primary, secondary))
    }

    @Test
    fun `leaving dual pane returns routing to primary and removes the other pane`() {
        val coordinator = BrowserWorkspaceCoordinator()
        val primary = FileListFragment()
        val secondary = FileListFragment()
        coordinator.updateVisibility(true, BrowserPane.SECONDARY)

        coordinator.updateVisibility(false, BrowserPane.SECONDARY)

        assertSame(primary, coordinator.active(primary, secondary))
        assertNull(coordinator.other(primary, secondary))
    }
}
