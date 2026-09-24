package com.wisso.wizefiles.core.entitlement

import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class ProFeatureAccessTest {
    @After
    fun reset() {
        DebugEntitlementController.reset()
    }

    @Test
    fun freeBuildRejectsEveryProCapability() {
        DebugEntitlementController.setProEnabled(false)

        ProFeature.entries.forEach { feature ->
            assertFalse(ProFeatureAccess.isAllowed(feature))
            assertThrows(ProFeatureRequiredException::class.java) {
                ProFeatureAccess.require(feature)
            }
        }
    }

    @Test
    fun proBuildAllowsEveryProCapability() {
        DebugEntitlementController.setProEnabled(true)

        ProFeature.entries.forEach { feature ->
            assertTrue(ProFeatureAccess.isAllowed(feature))
            ProFeatureAccess.require(feature)
        }
    }
}
