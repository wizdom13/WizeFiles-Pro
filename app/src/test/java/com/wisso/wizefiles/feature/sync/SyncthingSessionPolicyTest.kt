// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncthingSessionPolicyTest {
    @Test fun foregroundSessionsCanOutliveBackgroundWindow() {
        val options = SyncthingSessionOptions(manualMinutes = 60)
        assertEquals(TimeUnit.HOURS.toMillis(1), options.durationMillis(true))
        assertEquals(TimeUnit.MINUTES.toMillis(8), options.durationMillis(false))
    }

    @Test fun untilCompleteStillBoundsExecutionWhenForegroundPromotionIsDenied() {
        val options = SyncthingSessionOptions(manualMinutes = 0)
        assertEquals(Long.MAX_VALUE, options.durationMillis(true))
        assertEquals(TimeUnit.MINUTES.toMillis(8), options.durationMillis(false))
        assertFalse(SyncthingSessionPolicy.expired(0, Long.MAX_VALUE, Long.MAX_VALUE))
    }

    @Test fun durationUsesElapsedTimeAndStopsAtTheConfiguredBoundary() {
        assertFalse(SyncthingSessionPolicy.expired(50, 1_000_049, 1))
        assertTrue(SyncthingSessionPolicy.expired(50, 1_000_050, 1))
    }

    @Test fun retryBudgetAndDelayAreBounded() {
        val options = SyncthingSessionOptions(retryMinutes = 5, retryLimit = 2)
        assertTrue(options.shouldRetry(0))
        assertTrue(options.shouldRetry(1))
        assertFalse(options.shouldRetry(2))
        assertFalse(options.copy(automaticRetry = false).shouldRetry(0))
        assertEquals(TimeUnit.MINUTES.toMillis(10), options.backoffMillis(1))
        assertEquals(TimeUnit.HOURS.toMillis(5), options.backoffMillis(Int.MAX_VALUE))
    }

    @Test fun optionsRoundTripWithoutLosingNetworkSettings() {
        val options = SyncthingSessionOptions(0, 2, false, 10, 3)
        val saved = options.mergeInto("{\"syncthingPublicNetwork\":true,\"custom\":42}")
        assertEquals(options, SyncthingSessionOptions.decode(saved))
        assertTrue(JSONObject(saved).getBoolean("syncthingPublicNetwork"))
        assertEquals(42, JSONObject(saved).getInt("custom"))
    }

    @Test fun olderProfilesHaveUsefulDefaultsAndCorruptValuesAreBounded() {
        assertEquals(SyncthingSessionOptions(), SyncthingSessionOptions.decode("{}"))
        val options = SyncthingSessionOptions.decode("""{"syncthingSession":{
            "manualMinutes":999,"backgroundMinutes":999,"retryMinutes":0,"retryLimit":-1}}
        """)
        assertEquals(SyncthingSessionOptions(360, 8, true, 1, 0), options)
    }

    @Test fun timeLimitsDoNotMisreportUnfinishedWorkAsFailureOrSuccess() {
        assertEquals(SyncthingSessionOutcome.PENDING, SyncthingSessionPolicy.unfinished(false))
        assertEquals(SyncthingSessionOutcome.WAITING_FOR_PEER, SyncthingSessionPolicy.unfinished(true))
    }
}
