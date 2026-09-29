// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

internal data class SyncthingFileVersion(val path: String, val time: String, val size: Long)

/** Upstream version APIs require a running folder. Keep every peer paused during local recovery. */
internal class SyncthingVersionAccess(private val context: Context) {
    suspend fun list(profile: SyncProfile): List<SyncthingFileVersion> = withFolder(profile) { runtime, folder ->
        val json = JSONObject(runtime.request("GET", "/rest/folder/versions?folder=$folder", null))
        json.keys().asSequence().flatMap { path ->
            SyncthingVersionPolicy.safeFile(SyncthingLocalFolder.resolve(profile.sourceUri), path)
            json.getJSONArray(path).objects().asSequence().map {
                SyncthingFileVersion(path, it.getString("versionTime"), it.optLong("size"))
            }
        }.take(1000).toList().sortedWith(compareBy({ it.path }, { it.time }))
    }.orEmpty()

    suspend fun restore(profile: SyncProfile, version: SyncthingFileVersion) {
        SyncthingVersionPolicy.safeFile(SyncthingLocalFolder.resolve(profile.sourceUri), version.path)
        withFolder(profile) { runtime, folder ->
            val existing = JSONObject(runtime.request("GET", "/rest/folder/versions?folder=$folder", null))
            require(existing.optJSONArray(version.path)?.objects()?.any {
                it.optString("versionTime") == version.time
            } == true) { "The selected version is no longer available" }
            // The native simple versioner names archives to second precision. Restoring
            // in the same second could overwrite the selected archive with the current file.
            val wait = SyncthingVersionPolicy.restoreDelayMillis(existing.getJSONArray(version.path)
                .objects().map { it.getString("versionTime") }, System.currentTimeMillis())
            if (wait > 0) Thread.sleep(wait)
            val response = JSONObject(runtime.request("POST", "/rest/folder/versions?folder=$folder",
                JSONObject().put(version.path, version.time).toString()))
            require(!response.has(version.path) || response.isNull(version.path) ||
                response.optString(version.path).isBlank()) { response.optString(version.path) }
            true
        } ?: error("This folder has not been configured in the engine")
    }

    private suspend fun <T> withFolder(profile: SyncProfile, block: (SyncthingRuntime, String) -> T): T? =
        SyncthingCoordinator.whileIdle {
            val local = SyncthingLocalFolder.resolve(profile.sourceUri)
            SyncthingVersionPolicy.safeFile(local, ".stversions")
            val keep = SyncthingVersionPolicy.keep(profile.protectionJson)
            require(keep > 0) { "Enable version retention for this folder first" }
            val endpoint = SyncthingProfilePolicy.validate(profile)
            val runtime = SyncthingRuntime.get(context).acquire()
            try {
                val folders = JSONArray(runtime.request("GET", "/rest/config/folders", null))
                val config = folders.objects().firstOrNull { it.optString("id") == endpoint.folderId }
                    ?: return@whileIdle null
                require(File(config.getString("path")).canonicalFile == local)
                JSONArray(runtime.request("GET", "/rest/config/devices", null)).objects().forEach { device ->
                    runtime.request("PUT", "/rest/config/devices/" + SyncthingHttp.component(device.getString("deviceID")),
                        device.put("paused", true).toString())
                }
                val folder = SyncthingHttp.component(endpoint.folderId)
                config.put("paused", false).put("versioning", JSONObject().put("type", "simple")
                    .put("params", JSONObject().put("keep", keep.toString())))
                runtime.request("PUT", "/rest/config/folders/$folder", config.toString())
                val deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(10)
                while (true) {
                    val ready = runCatching { JSONObject(runtime.request("GET",
                        "/rest/db/status?folder=$folder", null)).optString("state") }
                        .getOrNull() in setOf("idle", "scanning", "scan-waiting", "syncing", "sync-waiting")
                    if (ready) break
                    check(System.nanoTime() < deadline) { "Folder did not become ready for version recovery" }
                    Thread.sleep(100)
                }
                try { block(runtime, folder) }
                finally { runtime.request("PUT", "/rest/config/folders/$folder", config.put("paused", true).toString()) }
            } finally { runtime.release() }
        }
}
