// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.io.File
import java.net.URI
import org.json.JSONArray
import org.json.JSONObject

/** Uses upstream defaults and preserves existing device settings and the reconciliation database. */
internal class SyncthingRestEngine(
    private val control: SyncthingControl,
    private val profile: (String) -> SyncProfile? = SyncRepository::profile,
    private val profiles: () -> List<SyncProfile> = SyncRepository::profiles,
    private val peers: (String) -> List<String> = { id ->
        listOf(SyncthingProfilePolicy.validate(requireNotNull(profile(id))).deviceId)
    }
) : SyncthingEnginePort {
    fun deviceId(): String = get("/rest/system/status").getString("myID")

    override fun ensureFolder(request: SyncthingFolderRequest): SyncthingEngineResult = result {
        val endpoint = request.endpoint
        require(endpoint.folderId.matches(Regex("[A-Za-z0-9][A-Za-z0-9._-]{0,63}"))) {
            "Folder ID must contain 1–64 letters, numbers, dots, underscores or hyphens"
        }
        val local = SyncthingLocalFolder.resolve(request.localFolderUri)
        SyncthingProfilePolicy.validateUnique(request.profileId, local.toURI().toString(), endpoint,
            profiles())
        val ownId = deviceId()
        val devices = JSONArray(control.request("GET", "/rest/config/devices", null))
        val selected = request.devices ?: listOf(SyncthingDevice(endpoint.deviceId))
        require(selected.isNotEmpty()) { "Select at least one shared device" }
        selected.forEach { saved ->
            val validated = get("/rest/svc/deviceid?id=${part(saved.id)}").getString("id")
            require(validated == saved.id && ownId != validated) { "Select another device using its complete ID" }
            val device = (0 until devices.length()).map(devices::getJSONObject)
                .firstOrNull { it.optString("deviceID") == validated }
                ?: get("/rest/config/defaults/device").put("deviceID", validated)
            device.put("paused", false).put("introducer", false).put("autoAcceptFolders", false)
            // Legacy callers preserve existing addresses; managed peers use the saved configuration.
            if (request.devices != null) device.put("name", saved.name)
                .put("addresses", JSONArray(saved.addresses))
            control.request("PUT", "/rest/config/devices/${part(validated)}", device.toString())
        }

        val folders = JSONArray(control.request("GET", "/rest/config/folders", null))
        val existing = (0 until folders.length()).map(folders::getJSONObject)
            .firstOrNull { it.optString("id") == endpoint.folderId }
        require(existing == null || File(existing.getString("path")).canonicalFile == local) {
            "This Syncthing folder ID is already assigned to another local folder"
        }
        val folder = existing ?: get("/rest/config/defaults/folder")
        folder.put("id", endpoint.folderId).put("label", profile(request.profileId)?.name ?: endpoint.folderId)
            .put("path", local.absolutePath).put("filesystemType", "basic")
            .put("type", if (request.mode == SyncthingFolderMode.SEND_RECEIVE) "sendreceive" else "sendonly")
            .put("devices", JSONArray((listOf(ownId) + selected.map { it.id }).distinct()
                .map { JSONObject().put("deviceID", it) }))
            .put("ignorePerms", true).put("fsWatcherEnabled", true)
            .put("rescanIntervalS", 60).put("paused", true)
            .put("versioning", JSONObject().put("type", if (request.keepVersions > 0) "simple" else "")
                .put("params", JSONObject().put("keep", request.keepVersions.coerceIn(0, 100).toString())))
        val folderOptions = JSONObject(request.folderOptions)
        folder.put("rescanIntervalS", folderOptions.optInt("rescanIntervalS", 60).coerceIn(0, 31_536_000))
            .put("fsWatcherEnabled", folderOptions.optBoolean("fsWatcherEnabled", true))
            .put("ignorePerms", folderOptions.optBoolean("ignorePerms", true))
        control.request("PUT", "/rest/config/folders/${part(endpoint.folderId)}", folder.toString())
        // Only change ignores while paused. Ignore rules are not a file-by-file WizeFiles plan.
        val ignorePath = "/rest/db/ignores?folder=${part(endpoint.folderId)}"
        val current = get(ignorePath).optJSONArray("ignore") ?: JSONArray()
        val lines = (0 until current.length()).map(current::getString)
        control.request("POST", ignorePath, JSONObject().put("ignore",
            JSONArray(SyncthingIgnorePolicy.merge(lines, request.ignorePatterns))).toString())
    }

    override fun requestScan(profileId: String) = result {
        control.request("POST", "/rest/db/scan?folder=${part(endpoint(profileId).folderId)}", null)
    }

    override fun folderStatus(profileId: String): SyncthingFolderStatus {
        val endpoint = endpoint(profileId)
        val folder = part(endpoint.folderId)
        val local = get("/rest/db/status?folder=$folder")
        val selected = peers(profileId)
        val remote = selected.map { get("/rest/db/completion?folder=$folder&device=${part(it)}") }
        val connections = get("/rest/system/connections").optJSONObject("connections")
        val connected = selected.isNotEmpty() && selected.all {
            connections?.optJSONObject(it)?.optBoolean("connected") == true
        } && remote.all { it.optString("remoteState") == "valid" }
        val localPending = local.optLong("needTotalItems").coerceAtLeast(0)
        val remotePending = remote.sumOf { it.optLong("needItems").coerceAtLeast(0) +
            it.optLong("needDeletes").coerceAtLeast(0) }
        val pending = localPending + remotePending
        val errors = local.optInt("pullErrors")
        val state = when {
            errors > 0 || local.optString("error").isNotBlank() -> SyncthingFolderState.ERROR
            !connected -> SyncthingFolderState.DISCONNECTED
            local.optString("state").contains("scanning") -> SyncthingFolderState.SCANNING
            local.optString("state") != "idle" || pending > 0 -> SyncthingFolderState.SYNCING
            else -> SyncthingFolderState.IDLE
        }
        return SyncthingFolderStatus(
            state, local.optLong("localBytes").coerceAtLeast(0),
            remote.maxOfOrNull { it.optLong("globalBytes").coerceAtLeast(0) } ?: 0,
            local.optLong("needBytes").coerceAtLeast(0) + remote.sumOf { it.optLong("needBytes").coerceAtLeast(0) },
            pending, local.optString("error").ifBlank {
                if (errors > 0) "Syncthing reported $errors file errors" else ""
            }
        )
    }

    override fun pause(profileId: String) = setPaused(profileId, true)
    override fun resume(profileId: String) = setPaused(profileId, false)

    override fun removeFolder(profileId: String) = result {
        control.request("DELETE", "/rest/config/folders/${part(endpoint(profileId).folderId)}", null)
    }

    private fun setPaused(profileId: String, paused: Boolean) = result {
        val path = "/rest/config/folders/${part(endpoint(profileId).folderId)}"
        val folder = get(path).put("paused", paused)
        control.request("PUT", path, folder.toString())
    }

    private fun endpoint(id: String) = SyncthingProfilePolicy.validate(requireNotNull(profile(id)))
    private fun get(path: String) = JSONObject(control.request("GET", path, null))
    private fun part(value: String) = SyncthingHttp.component(value)

    private inline fun result(block: () -> Unit): SyncthingEngineResult = try {
        block()
        SyncthingEngineResult.Success
    } catch (interrupted: InterruptedException) {
        Thread.currentThread().interrupt()
        throw interrupted
    } catch (failure: Exception) {
        SyncthingEngineResult.Failure("SYNCTHING_CONTROL", failure.message.orEmpty().take(320))
    }
}

internal object SyncthingLocalFolder {
    fun resolve(uri: String): File {
        val parsed = URI(uri)
        require(parsed.scheme == "file" && parsed.authority.isNullOrEmpty()) {
            "Select a directly accessible local folder"
        }
        val folder = File(parsed).canonicalFile
        if (!folder.isDirectory || !folder.canRead() || !folder.canWrite()) {
            throw java.io.IOException("The local folder is unavailable or read-only")
        }
        return folder
    }
}

internal fun SyncthingEngineResult.requireSuccess() {
    if (this is SyncthingEngineResult.Failure) throw java.io.IOException(message.ifBlank { code })
}
