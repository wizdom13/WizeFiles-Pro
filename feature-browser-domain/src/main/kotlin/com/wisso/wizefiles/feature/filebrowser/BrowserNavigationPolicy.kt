package com.wisso.wizefiles.feature.filebrowser

/** Hierarchy facts resolved by an app/provider adapter without exposing its path representation. */
data class BrowserNavigationFacts(
    val hasParent: Boolean,
    val configuredRootAvailable: Boolean,
    val isConfiguredRoot: Boolean,
    val isWithinConfiguredRoot: Boolean,
    val crossesContainerBoundary: Boolean = false
)

data class BrowserRestorationFacts(
    val targetResolved: Boolean,
    val targetAvailable: Boolean,
    val configuredRootAvailable: Boolean,
    val targetWithinConfiguredRoot: Boolean
)

sealed interface BrowserBackDecision {
    data object ClearSelection : BrowserBackDecision
    data class NavigateUp(val collapseSearch: Boolean) : BrowserBackDecision
    data object DeferToHost : BrowserBackDecision
}

data class BrowserDirectNavigationDecision(val collapseSearch: Boolean)

enum class BrowserRestorationDecision { RESTORE_TARGET, USE_FALLBACK }

/** Provider-neutral Back, up, direct-navigation, and restoration decisions. */
object BrowserNavigationPolicy {
    fun canNavigateUp(facts: BrowserNavigationFacts): Boolean = when {
        facts.crossesContainerBoundary -> true
        !facts.configuredRootAvailable -> facts.hasParent
        facts.isConfiguredRoot -> false
        !facts.isWithinConfiguredRoot -> false
        else -> facts.hasParent
    }

    fun back(hasSelection: Boolean, facts: BrowserNavigationFacts): BrowserBackDecision = when {
        hasSelection -> BrowserBackDecision.ClearSelection
        canNavigateUp(facts) -> BrowserBackDecision.NavigateUp(collapseSearch = true)
        else -> BrowserBackDecision.DeferToHost
    }

    fun directNavigation(): BrowserDirectNavigationDecision =
        BrowserDirectNavigationDecision(collapseSearch = true)

    fun restoration(facts: BrowserRestorationFacts): BrowserRestorationDecision =
        if (
            facts.targetResolved && facts.targetAvailable &&
            (!facts.configuredRootAvailable || facts.targetWithinConfiguredRoot)
        ) BrowserRestorationDecision.RESTORE_TARGET else BrowserRestorationDecision.USE_FALLBACK
}
