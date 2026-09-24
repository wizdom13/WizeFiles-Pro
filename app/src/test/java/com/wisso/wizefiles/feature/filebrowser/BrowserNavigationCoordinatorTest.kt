package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Paths
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationCoordinatorTest {
    @Test
    fun `back clears selection before navigating`() {
        var selected = true
        var navigated = false
        val coordinator = coordinator(
            selected = { selected },
            clear = { selected = false },
            navigateUp = { navigated = true }
        )

        assertTrue(coordinator.navigateUpOnBackPressed())
        assertFalse(selected)
        assertFalse(navigated)
    }

    @Test
    fun `back navigates within storage root`() {
        var navigated = false
        var collapsed = false
        val coordinator = coordinator(
            collapse = { collapsed = true },
            navigateUp = { navigated = true }
        )

        assertTrue(coordinator.navigateUpOnBackPressed())
        assertTrue(navigated)
        assertTrue(collapsed)
    }

    @Test fun `direct navigation collapses search before dispatching concrete path`() {
        var collapsed = false
        var destination = Paths.get("/")
        val coordinator = BrowserNavigationCoordinator(
            currentPath = { Paths.get("/storage") },
            storageRoot = { Paths.get("/storage") },
            hasSelection = { false },
            clearSelection = {},
            collapseSearch = { collapsed = true },
            navigateUp = {},
            navigateTo = { destination = it }
        )
        val child = Paths.get("/storage/child")

        coordinator.navigateTo(child)

        assertTrue(collapsed)
        assertTrue(destination == child)
    }

    @Test
    fun `back is not consumed at storage root`() {
        val root = Paths.get("/storage")
        val coordinator = BrowserNavigationCoordinator(
            currentPath = { root },
            storageRoot = { root },
            hasSelection = { false },
            clearSelection = {},
            collapseSearch = {},
            navigateUp = {},
            navigateTo = {}
        )

        assertFalse(coordinator.navigateUpOnBackPressed())
    }

    @Test fun `unresolved current path delegates Back to host`() {
        val coordinator = BrowserNavigationCoordinator(
            currentPath = { null },
            storageRoot = { error("root must not be resolved without a current path") },
            hasSelection = { false },
            clearSelection = {},
            collapseSearch = {},
            navigateUp = { error("must not navigate") },
            navigateTo = {}
        )

        assertFalse(coordinator.navigateUpOnBackPressed())
        assertFalse(coordinator.canNavigateUp())
    }

    private fun coordinator(
        selected: () -> Boolean = { false },
        clear: () -> Unit = {},
        collapse: () -> Unit = {},
        navigateUp: () -> Unit
    ) = BrowserNavigationCoordinator(
        currentPath = { Paths.get("/storage/folder") },
        storageRoot = { Paths.get("/storage") },
        hasSelection = selected,
        clearSelection = clear,
        collapseSearch = collapse,
        navigateUp = navigateUp,
        navigateTo = {}
    )
}
