// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.app.Application
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnusedAppsAnalyzerTest {
    @Test
    fun systemAndUpdatedSystemApplicationsAreExcluded() {
        assertFalse(isSystemApplication(0))
        assertTrue(isSystemApplication(ApplicationInfo.FLAG_SYSTEM))
        assertTrue(isSystemApplication(ApplicationInfo.FLAG_UPDATED_SYSTEM_APP))
        assertTrue(
            isSystemApplication(
                ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
            )
        )
    }

    @Test
    fun missingUsageServiceReturnsDegradedResult() {
        val unavailableMessage = "Usage statistics are unavailable on this device"
        val analyzer = UnusedAppsAnalyzer(
            context = Application(),
            usageStatsManagerProvider = { null },
            stringProvider = { unavailableMessage }
        )

        val result = analyzer.analyze(120)

        assertTrue(result.apps.isEmpty())
        assertEquals(unavailableMessage, result.missingCapabilityMessage)
    }
}
