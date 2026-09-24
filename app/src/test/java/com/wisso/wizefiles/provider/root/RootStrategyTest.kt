package com.wisso.wizefiles.provider.root

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RootStrategyTest {
    @Test
    fun selectionPolicyIsExplicit() {
        assertFalse(RootStrategy.NEVER.selectsRoot(true))
        assertFalse(RootStrategy.AUTOMATIC.selectsRoot(false))
        assertTrue(RootStrategy.AUTOMATIC.selectsRoot(true))
        assertTrue(RootStrategy.ALWAYS.selectsRoot(false))
    }
}
