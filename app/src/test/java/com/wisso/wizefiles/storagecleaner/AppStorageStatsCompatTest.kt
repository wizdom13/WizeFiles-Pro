// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storagecleaner

import android.app.Application
import android.content.pm.ApplicationInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppStorageStatsCompatTest {
    @Test
    fun sdkBelow26DoesNotInvokeApi26Fetcher() {
        val appInfo = ApplicationInfo()
        var called = false

        val result = AppStorageStatsCompat.getForApp(
            context = Application(),
            applicationInfo = appInfo,
            sdkInt = 24,
            api26Fetcher = { _, _ ->
                called = true
                AppStorageBytes(1L, 2L, 3L)
            }
        )

        assertNull(result)
        assertTrue(!called)
    }

    @Test
    fun sdk26AndAboveUsesApi26FetcherResult() {
        val appInfo = ApplicationInfo()

        val result = AppStorageStatsCompat.getForApp(
            context = Application(),
            applicationInfo = appInfo,
            sdkInt = 26,
            api26Fetcher = { _, _ -> AppStorageBytes(100L, 20L, 300L) }
        )

        assertEquals(100L, result?.appBytes)
        assertEquals(20L, result?.cacheBytes)
        assertEquals(300L, result?.dataBytes)
    }
}
