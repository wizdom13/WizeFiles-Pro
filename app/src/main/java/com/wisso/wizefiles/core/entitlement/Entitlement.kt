package com.wisso.wizefiles.core.entitlement

/**
 * App-wide capabilities granted by a trusted entitlement source.
 *
 * Keep this list independent from billing products: lifetime purchases and subscriptions both
 * resolve to the same [PRO] entitlement.
 */
enum class Entitlement {
    PRO,
}

/**
 * Immutable snapshot of the user's currently granted entitlements.
 */
@ConsistentCopyVisibility
data class EntitlementState private constructor(
    val granted: Set<Entitlement>,
) {
    val isPro: Boolean
        get() = Entitlement.PRO in granted

    fun has(entitlement: Entitlement): Boolean = entitlement in granted

    companion object {
        private val FREE = EntitlementState(emptySet())
        private val PRO = EntitlementState(setOf(Entitlement.PRO))

        fun free(): EntitlementState = FREE

        fun pro(): EntitlementState = PRO

        fun of(granted: Set<Entitlement>): EntitlementState = when (granted) {
            emptySet<Entitlement>() -> FREE
            setOf(Entitlement.PRO) -> PRO
            else -> EntitlementState(granted.toSet())
        }
    }
}

/**
 * Central catalogue of workflows reserved for WizeFiles Pro.
 *
 * This foundation does not gate any feature yet. Call sites are added separately so each workflow
 * can be guarded at both its UI entry point and its execution boundary.
 */
enum class ProFeature {
    DUAL_PANE,
    CROSS_PANE_DRAG_AND_DROP,
    SAVED_TRANSFER_WORKFLOWS,
    UNLIMITED_REMOTE_CONNECTIONS,
    RCLONE_POWER_USER,
    SYNC_PROFILES,
    SCHEDULED_SYNC,
    ADVANCED_ARCHIVE_OPERATIONS,
    BATCH_APP_MANAGER_OPERATIONS,
    PACKAGE_SIGNING,
    MULTIPLE_VAULTS,
    VAULT_AUTOMATION,
    ROOT_TOOLS,
    BUILT_IN_SERVERS,
    ADVANCED_LOCAL_SHARE,
    SAVED_CLEANUP_RULES,
    AUTOMATIC_CLEANUP,
    PREMIUM_APPEARANCE,
}

sealed interface ProFeatureGateResult {
    data object Allowed : ProFeatureGateResult

    data class RequiresPro(
        val feature: ProFeature,
    ) : ProFeatureGateResult
}
