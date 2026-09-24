package com.wisso.wizefiles.core.entitlement

import kotlinx.coroutines.flow.StateFlow

/**
 * Single source of truth for app-wide entitlements.
 *
 * Production purchase state will be supplied by the verified license layer. This contract never
 * reads a local "isPro" preference and deliberately knows nothing about billing product IDs.
 */
interface EntitlementRepository {
    val state: StateFlow<EntitlementState>

    fun has(entitlement: Entitlement): Boolean = state.value.has(entitlement)

    fun check(feature: ProFeature): ProFeatureGateResult =
        if (state.value.isPro) {
            ProFeatureGateResult.Allowed
        } else {
            ProFeatureGateResult.RequiresPro(feature)
        }
}

internal interface EntitlementSource {
    val state: StateFlow<EntitlementState>
}

internal class DefaultEntitlementRepository(
    private val source: EntitlementSource,
) : EntitlementRepository {
    override val state: StateFlow<EntitlementState>
        get() = source.state
}
