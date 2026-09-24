// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.os.Handler
import android.os.Looper
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class SchedulingUtilityRobolectricTest {

    private val handler = Handler(Looper.getMainLooper())
    private val mainLooper = shadowOf(Looper.getMainLooper())

    @Test
    fun debouncedRunnableRunsOnlyAfterTheLatestDelay() {
        var calls = 0
        val debounced = DebouncedRunnable(handler, 100) { ++calls }

        debounced()
        mainLooper.idleFor(Duration.ofMillis(99))
        debounced()
        mainLooper.idleFor(Duration.ofMillis(99))
        assertEquals(0, calls)

        mainLooper.idleFor(Duration.ofMillis(1))
        assertEquals(1, calls)
    }

    @Test
    fun debouncedRunnableCanCancelPendingWork() {
        var calls = 0
        val debounced = DebouncedRunnable(handler, 100) { ++calls }

        debounced()
        debounced.cancel()
        mainLooper.idleFor(Duration.ofMillis(100))

        assertEquals(0, calls)
    }

    @Test
    fun throttledRunnableRunsImmediatelyAndCoalescesTheNextWindow() {
        var calls = 0
        val throttled = ThrottledRunnable(handler, 100) { ++calls }

        throttled()
        mainLooper.idle()
        assertEquals(1, calls)

        throttled()
        throttled()
        mainLooper.idleFor(Duration.ofMillis(99))
        assertEquals(1, calls)

        mainLooper.idleFor(Duration.ofMillis(1))
        assertEquals(2, calls)
    }

    @Test
    fun throttledRunnableCanCancelQueuedWork() {
        var calls = 0
        val throttled = ThrottledRunnable(handler, 100) { ++calls }

        throttled()
        mainLooper.idle()
        throttled()
        throttled.cancel()
        mainLooper.idleFor(Duration.ofMillis(100))

        assertEquals(1, calls)
    }

    @Test
    fun statefulLiveDataStartsReadyAndEnforcesRefreshResetInvariant() {
        val liveData = TestStatefulLiveData()

        assertTrue(liveData.isReady)
        liveData.publish(Loading("cached"))
        assertFalse(liveData.isReady)
        assertThrows(IllegalStateException::class.java) { liveData.reset() }

        liveData.publish(Success("fresh"))
        liveData.reset()
        assertTrue(liveData.isReady)
    }

    private class TestStatefulLiveData : StatefulLiveData<String>() {
        fun publish(state: Stateful<String>) {
            value = state
        }
    }
}
