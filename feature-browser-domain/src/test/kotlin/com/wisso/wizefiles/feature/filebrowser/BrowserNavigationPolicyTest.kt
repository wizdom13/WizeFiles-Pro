package com.wisso.wizefiles.feature.filebrowser

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BrowserNavigationPolicyTest {
    @Test fun `selection consumes Back before otherwise eligible navigation`() {
        assertEquals(BrowserBackDecision.ClearSelection, BrowserNavigationPolicy.back(true, child()))
    }

    @Test fun `navigable child collapses search and navigates up`() {
        assertEquals(
            BrowserBackDecision.NavigateUp(collapseSearch = true),
            BrowserNavigationPolicy.back(false, child())
        )
    }

    @Test fun `configured root no-parent and provider roots defer Back to host`() {
        assertEquals(BrowserBackDecision.DeferToHost, BrowserNavigationPolicy.back(false, root()))
        assertEquals(BrowserBackDecision.DeferToHost, BrowserNavigationPolicy.back(false, noRootParent(false)))
        assertFalse(BrowserNavigationPolicy.canNavigateUp(root()))
        // These labels model facts supplied by remote and SAF adapters; provider type is irrelevant.
        listOf("ordinary", "remote", "saf").forEach {
            assertFalse(it, BrowserNavigationPolicy.canNavigateUp(root()))
        }
    }

    @Test fun `inside scope is eligible while outside scope never escapes through configured root`() {
        assertTrue(BrowserNavigationPolicy.canNavigateUp(child()))
        assertFalse(
            BrowserNavigationPolicy.canNavigateUp(
                BrowserNavigationFacts(true, true, false, false)
            )
        )
    }

    @Test fun `missing configured root uses ordinary parenthood`() {
        assertTrue(BrowserNavigationPolicy.canNavigateUp(noRootParent(true)))
        assertFalse(BrowserNavigationPolicy.canNavigateUp(noRootParent(false)))
    }

    @Test fun `archive boundary navigates to containing location despite ordinary root facts`() {
        assertTrue(
            BrowserNavigationPolicy.canNavigateUp(
                BrowserNavigationFacts(false, true, true, false, crossesContainerBoundary = true)
            )
        )
    }

    @Test fun `local and remote adapters with equivalent facts receive equivalent decisions`() {
        val localFacts = child()
        val remoteFacts = BrowserNavigationFacts(true, true, false, true)
        assertEquals(
            BrowserNavigationPolicy.back(false, localFacts),
            BrowserNavigationPolicy.back(false, remoteFacts)
        )
    }

    @Test fun `direct navigation always requests search collapse`() {
        assertTrue(BrowserNavigationPolicy.directNavigation().collapseSearch)
    }

    @Test fun `restoration accepts available in-scope targets and rejects invalid targets`() {
        assertEquals(
            BrowserRestorationDecision.RESTORE_TARGET,
            BrowserNavigationPolicy.restoration(BrowserRestorationFacts(true, true, true, true))
        )
        listOf(
            BrowserRestorationFacts(false, true, true, true),
            BrowserRestorationFacts(true, false, true, true),
            BrowserRestorationFacts(true, true, true, false)
        ).forEach {
            assertEquals(BrowserRestorationDecision.USE_FALLBACK, BrowserNavigationPolicy.restoration(it))
        }
    }

    @Test fun `restoration may use resolved child when configured root metadata is unavailable`() {
        assertEquals(
            BrowserRestorationDecision.RESTORE_TARGET,
            BrowserNavigationPolicy.restoration(BrowserRestorationFacts(true, true, false, false))
        )
    }

    private fun child() = BrowserNavigationFacts(true, true, false, true)
    private fun root() = BrowserNavigationFacts(false, true, true, true)
    private fun noRootParent(hasParent: Boolean) =
        BrowserNavigationFacts(hasParent, false, false, false)
}
