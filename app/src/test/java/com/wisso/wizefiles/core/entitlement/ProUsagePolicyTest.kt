package com.wisso.wizefiles.core.entitlement

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProUsagePolicyTest {
    @Test
    fun freeIncludesOneRemoteAndOneVault() {
        assertTrue(ProUsagePolicy.canAddRemoteConnection(0, isNew = true, isPro = false))
        assertTrue(ProUsagePolicy.canAddVault(0, isNew = true, isPro = false))
        assertFalse(ProUsagePolicy.canAddRemoteConnection(1, isNew = true, isPro = false))
        assertFalse(ProUsagePolicy.canAddVault(1, isNew = true, isPro = false))
    }

    @Test
    fun editsAndExistingDataRemainAvailableWithoutPro() {
        assertTrue(ProUsagePolicy.canAddRemoteConnection(4, isNew = false, isPro = false))
        assertTrue(ProUsagePolicy.canAddVault(4, isNew = false, isPro = false))
    }

    @Test
    fun proRemovesCardinalityLimits() {
        assertTrue(ProUsagePolicy.canAddRemoteConnection(100, isNew = true, isPro = true))
        assertTrue(ProUsagePolicy.canAddVault(100, isNew = true, isPro = true))
    }

    @Test
    fun onlyBatchAppManagerActionsRequirePro() {
        assertTrue(ProUsagePolicy.canRunAppManagerOperation(1, isPro = false))
        assertFalse(ProUsagePolicy.canRunAppManagerOperation(2, isPro = false))
        assertTrue(ProUsagePolicy.canRunAppManagerOperation(2, isPro = true))
    }
}
