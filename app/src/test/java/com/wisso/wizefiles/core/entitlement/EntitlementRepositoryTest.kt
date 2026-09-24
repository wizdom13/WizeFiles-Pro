package com.wisso.wizefiles.core.entitlement

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementRepositoryTest {
    @Test
    fun freeStateRequiresProForEveryCataloguedFeature() {
        val repository = repositoryWith(EntitlementState.free())

        assertFalse(repository.state.value.isPro)
        ProFeature.entries.forEach { feature ->
            assertEquals(
                ProFeatureGateResult.RequiresPro(feature),
                repository.check(feature),
            )
        }
    }

    @Test
    fun proStateAllowsEveryCataloguedFeature() {
        val repository = repositoryWith(EntitlementState.pro())

        assertTrue(repository.has(Entitlement.PRO))
        ProFeature.entries.forEach { feature ->
            assertSame(ProFeatureGateResult.Allowed, repository.check(feature))
        }
    }

    @Test
    fun repositoryExposesSourceUpdatesWithoutPersistingAnAuthorityFlag() {
        val source = TestEntitlementSource(EntitlementState.free())
        val repository = DefaultEntitlementRepository(source)

        source.emit(EntitlementState.pro())

        assertSame(EntitlementState.pro(), repository.state.value)
        assertTrue(repository.state.value.isPro)
    }

    @Test
    fun featureCatalogueMatchesTheApprovedProBoundary() {
        assertEquals(
            setOf(
                "DUAL_PANE",
                "CROSS_PANE_DRAG_AND_DROP",
                "SAVED_TRANSFER_WORKFLOWS",
                "UNLIMITED_REMOTE_CONNECTIONS",
                "RCLONE_POWER_USER",
                "SYNC_PROFILES",
                "SCHEDULED_SYNC",
                "ADVANCED_ARCHIVE_OPERATIONS",
                "BATCH_APP_MANAGER_OPERATIONS",
                "PACKAGE_SIGNING",
                "MULTIPLE_VAULTS",
                "VAULT_AUTOMATION",
                "ROOT_TOOLS",
                "BUILT_IN_SERVERS",
                "ADVANCED_LOCAL_SHARE",
                "SAVED_CLEANUP_RULES",
                "AUTOMATIC_CLEANUP",
                "PREMIUM_APPEARANCE",
            ),
            ProFeature.entries.mapTo(mutableSetOf()) { it.name },
        )
    }

    private fun repositoryWith(state: EntitlementState): EntitlementRepository =
        DefaultEntitlementRepository(TestEntitlementSource(state))

    private class TestEntitlementSource(
        initialState: EntitlementState,
    ) : EntitlementSource {
        private val currentState = MutableStateFlow(initialState)

        override val state: StateFlow<EntitlementState> = currentState.asStateFlow()

        fun emit(state: EntitlementState) {
            currentState.value = state
        }
    }
}
