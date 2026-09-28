// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import android.content.Context
import java.io.File
import java.util.concurrent.TimeUnit
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SyncthingRuntimeTest {
    @Test fun killingSupervisorCannotLeaveAnEngineRunning() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = SyncthingRuntime.get(context).acquire()
        try {
            // Fault injection at the process boundary, not a graceful REST shutdown.
            val child = SyncthingRuntime::class.java.getDeclaredField("child").apply { isAccessible = true }
                .get(runtime) as Process
            child.destroyForcibly()
            assertTrue(child.waitFor(5, TimeUnit.SECONDS))
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
            var responding = true
            while (responding && System.nanoTime() < deadline) {
                responding = runCatching { runtime.request("GET", "/rest/system/status", null) }.isSuccess
                if (responding) Thread.sleep(50)
            }
            assertFalse("Engine survived its supervisor", responding)
        } finally { runtime.release() }
    }

    @Test fun packagedEngineStartsWithPrivateControlAndStopsAfterLastLease() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val executable = File(context.applicationInfo.nativeLibraryDir, "libsyncthing.so")
        val process = ProcessBuilder(executable.absolutePath, "version").redirectErrorStream(true).start()
        val version = process.inputStream.bufferedReader().use { it.readText() }
        assertTrue(process.waitFor(20, TimeUnit.SECONDS))
        assertEquals("Engine version failed (${executable.absolutePath}): $version", 0, process.exitValue())
        assertTrue(version, version.contains("v2.1.5"))
        val runtime = SyncthingRuntime.get(context).acquire()
        val identity: String
        try {
            identity = SyncthingRestEngine(runtime).deviceId()
            assertTrue(identity.matches(Regex("[A-Z2-7-]{63}")))
            val options = JSONObject(runtime.request("GET", "/rest/config/options", null))
            assertFalse(options.getBoolean("globalAnnounceEnabled"))
            assertFalse(options.getBoolean("relaysEnabled"))
            assertFalse(options.getBoolean("localAnnounceEnabled"))
        } finally { runtime.release() }
        assertThrows(Exception::class.java) { runtime.request("GET", "/rest/system/status", null) }
        runtime.acquire()
        try { assertEquals(identity, SyncthingRestEngine(runtime).deviceId()) }
        finally { runtime.release() }
    }
}
