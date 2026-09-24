// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.smb

import java.io.InterruptedIOException
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CountDownLatch
import org.junit.Assert.assertEquals
import org.junit.Before

class SmbWatchServiceInterruptionTest {

    @Before
    fun resetTelemetry() {
        SmbWatchLifecycleTelemetry.resetForTest()
    }

    @Test
    fun `classifies wrapped interrupted exceptions as expected shutdown`() {
        val exception = RuntimeException(RuntimeException(InterruptedException()))

        assertTrue(exception.isExpectedSmbWatchInterruption())
    }

    @Test
    fun `classifies interrupted io as expected shutdown`() {
        val exception = InterruptedIOException()

        assertTrue(exception.isExpectedSmbWatchInterruption())
    }


    @Test
    fun `classifies smbj interrupted credit wait message as expected shutdown`() {
        val exception = RuntimeException(
            "Got interrupted waiting for 1 to be available. Credits available at this moment: 0"
        )

        assertTrue(exception.isExpectedSmbWatchInterruption())
    }

    @Test
    fun `does not classify unrelated runtime failure as interruption`() {
        val exception = RuntimeException("SMB failure")

        assertFalse(exception.isExpectedSmbWatchInterruption())
    }

    @Test
    fun `bounded shutdown reports a notifier that remains alive`() {
        val release = CountDownLatch(1)
        val thread = Thread { release.await() }.apply { start() }
        try {
            assertFalse(awaitThreadShutdown(thread, 10))
        } finally {
            release.countDown()
            thread.join()
        }
    }

    @Test
    fun `shutdown interrupts closes change notify resource and records completion`() {
        val events = mutableListOf<String>()
        var clock = 1_000_000L

        val stopped = runSmbWatchShutdown(
            interrupt = { events += "interrupt" },
            closeDirectory = { events += "close" },
            awaitShutdown = { timeout -> events += "await:$timeout"; true },
            timeoutMillis = 5_000,
            nanoTime = { clock.also { clock += 2_000_000L } }
        )

        assertTrue(stopped)
        assertEquals(listOf("interrupt", "close", "await:5000"), events)
        assertEquals(SmbWatchLifecycleSnapshot(1, 1, 0, 2), SmbWatchLifecycleTelemetry.snapshot())
    }

    @Test
    fun `shutdown timeout is observable and never retries cancel`() {
        var closeCount = 0

        assertFalse(
            runSmbWatchShutdown(
                interrupt = {},
                closeDirectory = { closeCount++ },
                awaitShutdown = { false },
                timeoutMillis = 10
            )
        )

        assertEquals(1, closeCount)
        val telemetry = SmbWatchLifecycleTelemetry.snapshot()
        assertEquals(1L, telemetry.attempts)
        assertEquals(0L, telemetry.completed)
        assertEquals(1L, telemetry.timedOut)
    }
}
