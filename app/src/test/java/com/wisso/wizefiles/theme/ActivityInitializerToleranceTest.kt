package com.wisso.wizefiles.theme

import com.wisso.wizefiles.theme.custom.CustomThemeHelper
import com.wisso.wizefiles.theme.night.NightModeHelper
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ActivityInitializerToleranceTest {
    @Test
    fun `custom theme warning is emitted once per activity class`() {
        val activityClass = "com.wisso.wizefiles.storagecleaner.StorageCleanerActivity"

        assertTrue(CustomThemeHelper.shouldWarnForUntrackedActivity(activityClass))
        assertFalse(CustomThemeHelper.shouldWarnForUntrackedActivity(activityClass))
        assertTrue(CustomThemeHelper.shouldWarnForUntrackedActivity("com.wisso.wizefiles.OtherActivity"))
    }

    @Test
    fun `night mode warning is emitted once per activity class`() {
        val activityClass = "com.wisso.wizefiles.storagecleaner.StorageCleanerActivity"

        assertTrue(NightModeHelper.shouldWarnForUntrackedActivity(activityClass))
        assertFalse(NightModeHelper.shouldWarnForUntrackedActivity(activityClass))
        assertTrue(NightModeHelper.shouldWarnForUntrackedActivity("com.wisso.wizefiles.OtherActivity"))
    }
}
