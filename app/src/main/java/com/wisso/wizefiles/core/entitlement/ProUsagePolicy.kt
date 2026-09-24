package com.wisso.wizefiles.core.entitlement

/**
 * Free-tier cardinality rules kept separate from Android and persistence for deterministic tests.
 */
object ProUsagePolicy {
    fun canAddRemoteConnection(
        existingRemoteCount: Int,
        isNew: Boolean,
        isPro: Boolean,
    ): Boolean = !isNew || isPro || existingRemoteCount < FREE_REMOTE_LIMIT

    fun canAddVault(
        existingVaultCount: Int,
        isNew: Boolean,
        isPro: Boolean,
    ): Boolean = !isNew || isPro || existingVaultCount < FREE_VAULT_LIMIT

    fun canRunAppManagerOperation(itemCount: Int, isPro: Boolean): Boolean =
        itemCount <= 1 || isPro

    private const val FREE_REMOTE_LIMIT = 1
    private const val FREE_VAULT_LIMIT = 1
}
