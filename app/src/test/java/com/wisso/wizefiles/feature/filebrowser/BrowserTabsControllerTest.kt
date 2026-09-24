package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserTabsControllerTest {
    @Test
    fun addingTabs_selectsNewestAndStopsAtLimit() {
        val controller = BrowserTabsController(maximumTabCount = 3)
        val first = controller.initialize("Files")
        val second = controller.add("Downloads")!!
        val third = controller.add("Drive")!!

        assertEquals(listOf(first.id, second.id, third.id), controller.tabs.map { it.id })
        assertEquals(third.id, controller.activeId)
        assertFalse(controller.canAddTab)
        assertNull(controller.add("NAS"))
    }

    @Test
    fun closingActiveTab_selectsItsNextNeighbour() {
        val controller = BrowserTabsController()
        controller.initialize("Files")
        val middle = controller.add("Downloads")!!
        val last = controller.add("Drive")!!
        controller.select(middle.id)

        assertEquals(last.id, controller.close(middle.id))
        assertEquals(last.id, controller.activeId)
    }

    @Test
    fun finalTabCannotBeClosed() {
        val controller = BrowserTabsController()
        val only = controller.initialize("Files")

        assertNull(controller.close(only.id))
        assertEquals(only.id, controller.activeId)
    }

    @Test
    fun restorePreservesOrderTitlesAndActiveTab() {
        val controller = BrowserTabsController()

        assertTrue(
            controller.restore(
                ids = listOf(7L, 3L),
                titles = listOf("NAS", "Downloads"),
                requestedActiveId = 3L,
                requestedNextId = 9L
            )
        )
        assertEquals(listOf("NAS", "Downloads"), controller.tabs.map { it.title })
        assertEquals(3L, controller.activeId)
        assertEquals(9L, controller.add("Cloud")!!.id)
    }

    @Test
    fun workspaceState_preservesSecondaryPaneAndClampsDivider() {
        val controller = BrowserTabsController()
        val tab = controller.initialize("Files")

        controller.updateWorkspace(
            id = tab.id,
            dualPaneEnabled = true,
            activePane = BrowserPane.SECONDARY,
            dividerFraction = 0.9f
        )

        assertTrue(tab.dualPaneEnabled)
        assertEquals(BrowserPane.SECONDARY, tab.activePane)
        assertEquals(BrowserTabsController.MAXIMUM_DIVIDER_FRACTION, tab.dividerFraction)
        assertEquals("browser_tab_0_secondary", tab.secondaryPaneTag)
    }

    @Test
    fun restorePreservesPaneLayoutState() {
        val controller = BrowserTabsController()

        assertTrue(
            controller.restore(
                ids = listOf(4L),
                titles = listOf("NAS"),
                requestedActiveId = 4L,
                requestedNextId = 5L,
                dualPaneEnabled = listOf(true),
                activePanes = listOf(BrowserPane.SECONDARY),
                dividerFractions = listOf(0.6f)
            )
        )

        val tab = controller.activeTab!!
        assertTrue(tab.dualPaneEnabled)
        assertEquals(BrowserPane.SECONDARY, tab.activePane)
        assertEquals(0.6f, tab.dividerFraction)
    }
}
