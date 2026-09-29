// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.json.JSONArray
import org.json.JSONObject

internal data class SyncthingImportedFolder(
    val id: String,
    val name: String,
    val path: String,
    val mode: SyncMode,
    val peers: List<String>,
    val keep: Int = 5,
    val ignores: List<String> = emptyList(),
    val folderOptions: JSONObject = JSONObject(),
    val savedProfile: JSONObject? = null
) {
    init {
        require(id.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) { "Unsupported Syncthing folder ID" }
        require(mode in setOf(SyncMode.TWO_WAY, SyncMode.MIRROR))
        require(keep in 0..100 && peers.size <= 100)
        require(ignores.size <= 10_000 && ignores.none { it.trim().startsWith("#include") }) {
            "Flatten ignore include files before importing this configuration"
        }
    }
    fun profile(mappedPath: String, originalId: String, ownId: String, internet: Boolean): SyncProfile {
        val constraints = JSONObject(savedProfile?.optString("constraintsJson", "{}") ?: "{}")
            .put("syncthingPublicNetwork", internet).put("syncthingImportedIgnores", JSONArray(ignores))
            .put("syncthingFolderOptions", folderOptions)
        val peer = peers.firstOrNull { it != originalId && it != ownId } ?: ownId
        return SyncProfile(name = name.ifBlank { id }, sourceUri = java.io.File(mappedPath).toURI().toString(),
            destinationUri = SyncthingEndpointCodec.encode(SyncthingEndpoint(peer, id)), mode = mode,
            propagateDeletions = true,
            filtersJson = savedProfile?.optString("filtersJson", "{\"includeHidden\":true}") ?: "{\"includeHidden\":true}",
            protectionJson = SyncthingVersionPolicy.encode(savedProfile?.optString("protectionJson", "{}") ?: "{}", keep),
            scheduleJson = savedProfile?.optString("scheduleJson", "{}") ?: "{}",
            constraintsJson = constraints.toString(), enabled = savedProfile?.optBoolean("enabled", true) ?: true)
    }
}

internal data class SyncthingImportModel(
    val devices: List<SyncthingDevice>,
    val folders: List<SyncthingImportedFolder>,
    val originalId: String,
    val identity: SyncthingIdentity? = null,
    val options: JSONObject = JSONObject()
) {
    init {
        require(devices.size <= 100 && folders.size <= 100) { "Import supports up to 100 devices and folders" }
        require(devices.map { it.id }.distinct().size == devices.size) { "Duplicate devices in configuration" }
        require(folders.map { it.id }.distinct().size == folders.size) { "Duplicate folder IDs in configuration" }
        val ids = devices.map { it.id }.toSet() + originalId
        require(folders.all { folder -> folder.peers.all { it in ids } }) { "A folder shares an unknown device" }
    }
}
