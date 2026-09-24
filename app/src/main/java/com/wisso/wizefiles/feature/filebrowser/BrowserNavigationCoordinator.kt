package com.wisso.wizefiles.feature.filebrowser

import java.nio.file.Path

/** Coordinates directory navigation without owning Fragment views or Android back callbacks. */
internal class BrowserNavigationCoordinator(
    private val currentPath: () -> Path?,
    private val storageRoot: (Path) -> Path?,
    private val hasSelection: () -> Boolean,
    private val clearSelection: () -> Unit,
    private val collapseSearch: () -> Unit,
    private val navigateUp: () -> Unit,
    private val navigateTo: (Path) -> Unit
) {
    fun canNavigateUp(): Boolean {
        val path = currentPath() ?: return false
        return BrowserNavigationPolicy.canNavigateUp(
            BrowserNavigationFactsResolver.resolve(path, storageRoot(path))
        )
    }

    fun navigateUp() {
        collapseSearch()
        navigateUp.invoke()
    }

    fun navigateUpOnBackPressed(): Boolean {
        val path = currentPath()
        val facts = path?.let { BrowserNavigationFactsResolver.resolve(it, storageRoot(it)) }
            ?: BrowserNavigationFacts(false, false, false, false)
        return when (val decision = BrowserNavigationPolicy.back(hasSelection(), facts)) {
            BrowserBackDecision.ClearSelection -> true.also { clearSelection() }
            is BrowserBackDecision.NavigateUp -> {
                if (decision.collapseSearch) collapseSearch()
                navigateUp.invoke()
                true
            }
            BrowserBackDecision.DeferToHost -> false
        }
    }

    fun navigateTo(path: Path) {
        if (BrowserNavigationPolicy.directNavigation().collapseSearch) collapseSearch()
        navigateTo.invoke(path)
    }
}
