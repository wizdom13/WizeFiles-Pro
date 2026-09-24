package com.wisso.wizefiles.core.entitlement

/**
 * Process-wide enforcement point for Pro-only capabilities.
 *
 * UI entry points should use this before navigation. Service, worker, and repository boundaries
 * must call [require] before creating or resuming a Pro operation.
 */
object ProFeatureAccess {
    fun isAllowed(feature: ProFeature): Boolean =
        AppEntitlements.repository.check(feature) is ProFeatureGateResult.Allowed

    fun require(feature: ProFeature) {
        if (!isAllowed(feature)) {
            throw ProFeatureRequiredException(feature)
        }
    }
}

class ProFeatureRequiredException(
    val feature: ProFeature,
) : IllegalStateException("WizeFiles Pro is required for ${feature.name}")
