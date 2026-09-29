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
    @Test fun configurationImportAndEncryptedBackupPreserveIdentityWithoutTouchingPayload() = kotlinx.coroutines.runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val runtime = SyncthingRuntime.get(context).acquire()
        val id = try {
            assertThrows(IllegalStateException::class.java) { runtime.beginMaintenance() }
            SyncthingRestEngine(runtime).deviceId()
        } finally { runtime.release() }
        runtime.beginMaintenance()
        try {
            assertTrue(runtime.isMaintaining())
            assertThrows(IllegalStateException::class.java) { runtime.acquire() }
        } finally { runtime.endMaintenance() }
        assertFalse(runtime.isMaintaining())
        val home = File(context.noBackupFilesDir, "syncthing")
        val identity = SyncthingIdentity(File(home, "cert.pem").readBytes(), File(home, "key.pem").readBytes())
        assertEquals(id, identity.validate()) // Compare the JVM fingerprint/check digits with the actual engine.
        val previous = SyncthingSettings.configuration.snapshot()
        val folder = File(requireNotNull(context.getExternalFilesDir(null)), "syncthing-import-test").apply { mkdirs() }
        val payload = File(folder, "untouched.txt").apply { writeText("unchanged local data") }
        val peer = SyncthingDevice(SyncthingIdentity.deviceId("test-peer".toByteArray()), "Test peer")
        val source = SyncthingImportModel(listOf(peer), listOf(SyncthingImportedFolder("import-test", "Import test",
            folder.path, SyncMode.TWO_WAY, listOf(peer.id), keep = 7, ignores = listOf("*.tmp"))), id, identity)
        try {
            SyncthingMigration.apply(context, source, mapOf("import-test" to folder.path), true, false)
            val profile = SyncRepository.profiles().single { SyncthingEndpointCodec.decode(it.destinationUri)?.folderId == "import-test" }
            assertTrue(SyncthingSettings.configuration.isPaused(profile.id))
            assertEquals(peer.id, SyncthingSettings.configuration.peers(profile.id).single().id)
            assertEquals(7, SyncthingVersionPolicy.keep(profile.protectionJson))
            assertEquals("unchanged local data", payload.readText())
            val password = "native backup round trip".toCharArray()
            val restored = SyncthingImportParser.parse(SyncthingBackup.export(context, password), password)
            assertEquals(id, restored.identity!!.validate())
            assertEquals(listOf("*.tmp"), restored.folders.single().ignores)
            assertFalse(runtime.isRunning())
        } finally {
            SyncRepository.profiles().filter { SyncthingEndpointCodec.decode(it.destinationUri)?.folderId == "import-test" }
                .forEach { SyncRepository.deleteProfile(it.id); SyncScheduler.cancel(context, it.id) }
            runtime.acquire()
            try { runtime.request("DELETE", "/rest/config/folders/import-test", null) }
            finally { runtime.release() }
            SyncthingSettings.configuration.replace(previous)
            identity.privateKey.fill(0)
            folder.deleteRecursively()
        }
    }

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
