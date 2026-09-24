package com.wisso.wizefiles.core.entitlement

import org.junit.Assert.assertTrue
import org.junit.Test

class ProFeatureAccessTest {
    @Test
    fun `all former Pro capabilities are available without an entitlement`() {
        ProFeature.entries.forEach { feature ->
            assertTrue(ProFeatureAccess.isAllowed(feature))
            ProFeatureAccess.require(feature)
        }
    }
}
