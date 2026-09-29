// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.Context
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object SyncthingMigration {
    @Volatile var applying = false
        private set
    private fun transaction(context: Context) = SyncthingImportTransaction(context.noBackupFilesDir,
        insertProfile = { SyncRepository.saveProfile(SyncthingProfileCodec.decode(it)) },
        deleteProfile = { id ->
            if (SyncRepository.profile(id) != null) check(SyncRepository.deleteProfile(id)) { "Cancel unfinished sync before importing" }
            SyncScheduler.cancel(context, id)
        },
        writeConfiguration = { replacement -> SyncthingSettings.configuration.replace(replacement) },
        scheduleProfile = { json ->
            val profile = SyncthingProfileCodec.decode(json)
            SyncScheduler.apply(context, profile.id, SyncScheduleCodec.decode(profile.scheduleJson))
        })

    @Synchronized fun recover(context: Context) {
        if (!applying) transaction(context).recover()
    }

    suspend fun apply(context: Context, source: SyncthingImportModel, paths: Map<String, String>,
        preserveIdentity: Boolean, internet: Boolean) = SyncthingCoordinator.whileIdle {
        recover(context)
        require(source.originalId.isNotBlank()) { "Choose the original device ID" }
        val old = SyncRepository.profiles().filter { SyncBackendRouter.kind(it) == SyncBackendKind.SYNCTHING }
        require(old.none { profile -> SyncRepository.runs(profile.id).any { !it.state.isTerminal } }) {
            "Cancel unfinished Syncthing runs before importing"
        }
        val live = SyncthingRuntime.get(context)
        check(!live.isRunning()) { "Stop Syncthing before importing" }
        val currentHome = File(context.noBackupFilesDir, "syncthing")
        // Ensure this installation has an identity to retain when identity replacement is not selected.
        live.acquire().release()
        live.beginMaintenance()
        try {
            val previousConfiguration = SyncthingSettings.configuration.snapshot()
            val identity = if (preserveIdentity) requireNotNull(source.identity) { "The original identity keys are missing" }
                else SyncthingIdentity(File(currentHome, "cert.pem").readBytes(), File(currentHome, "key.pem").readBytes())
            val ownId = identity.validate()
            val devices = source.devices.filter { it.id != source.originalId && it.id != ownId }
            val profiles = source.folders.map { folder ->
                val path = requireNotNull(paths[folder.id]) { "Choose every local folder path" }
                require(File(path).isAbsolute) { "Map this folder to an absolute local path" }
                val local = SyncthingLocalFolder.resolve(File(path).toURI().toString())
                require(!SyncEndpointValidator.scopesOverlap(local.toURI().toString(), File(context.applicationInfo.dataDir)
                    .canonicalFile.toURI().toString())) { "Choose a folder outside app-private storage" }
                folder.profile(local.path, source.originalId, ownId, internet)
            }
            profiles.forEach { SyncthingProfilePolicy.validateUnique(it.id, it.sourceUri, SyncthingProfilePolicy.validate(it), profiles) }
            val replacement = JSONObject().put("ownId", ownId).put("devices", JSONObject()).put("shares", JSONObject())
                .put("paused", JSONObject()).put("options", compatibleOptions(source.options))
            devices.forEach { replacement.getJSONObject("devices").put(it.id, it.json()) }
            source.folders.zip(profiles).forEach { (folder, profile) ->
                replacement.getJSONObject("shares").put(profile.id, JSONArray(folder.peers.filter { id -> devices.any { it.id == id } }))
                replacement.getJSONObject("paused").put(profile.id, true)
            }
            val transaction = transaction(context)
            transaction.discardStage()
            check(transaction.stage.mkdirs())
            android.system.Os.chmod(transaction.stage.path, 448)
            File(transaction.stage, "cert.pem").writeBytes(identity.certificate)
            File(transaction.stage, "key.pem").writeBytes(identity.privateKey)
            android.system.Os.chmod(File(transaction.stage, "key.pem").path, 384)
            val staged = SyncthingRuntime.staged(context, transaction.stage)
            try {
                staged.acquire()
                try {
                    require(SyncthingRestEngine(staged).deviceId() == ownId) { "Staged engine identity differs from the imported identity" }
                    devices.forEach { device ->
                        val id = JSONObject(staged.request("GET", "/rest/svc/deviceid?id=" + SyncthingHttp.component(device.id), null)).getString("id")
                        require(id == device.id) { "Use canonical complete device IDs" }
                        val config = JSONObject(staged.request("GET", "/rest/config/defaults/device", null))
                        device.json().keys().forEach { key -> config.put(key, device.json().get(key)) }
                        staged.request("PUT", "/rest/config/devices/${SyncthingHttp.component(id)}",
                            config.put("paused", true).put("introducer", false).put("autoAcceptFolders", false).toString())
                    }
                    val network = JSONObject(staged.request("GET", "/rest/config/options", null))
                    compatibleOptions(source.options).let { options -> options.keys().forEach { network.put(it, options.get(it)) } }
                    staged.request("PUT", "/rest/config/options", network.toString())
                    source.folders.zip(profiles).forEach { (folder, profile) ->
                        val config = JSONObject(staged.request("GET", "/rest/config/defaults/folder", null))
                            .put("id", folder.id).put("label", profile.name).put("path", File(java.net.URI(profile.sourceUri)).path)
                            .put("filesystemType", "basic").put("type", if (profile.mode == SyncMode.TWO_WAY) "sendreceive" else "sendonly")
                            .put("paused", true).put("devices", JSONArray((listOf(ownId) +
                                replacement.getJSONObject("shares").getJSONArray(profile.id).strings()).map { JSONObject().put("deviceID", it) }))
                        staged.request("PUT", "/rest/config/folders/${SyncthingHttp.component(folder.id)}", config.toString())
                    }
                } finally { staged.release() }
                require(SyncRepository.profiles().filter { SyncBackendRouter.kind(it) == SyncBackendKind.SYNCTHING } == old &&
                    SyncthingSettings.configuration.snapshot().toString() == previousConfiguration.toString()) {
                    "Syncthing settings changed during the import preview; retry the import"
                }
                applying = true
                try { transaction.commit(old.map { it.id }, profiles.map(SyncthingProfileCodec::encode),
                    previousConfiguration, replacement) }
                finally { applying = false }
            } catch (failure: Exception) {
                runCatching { transaction.discardStage() }
                throw failure
            }
            ownId
        } finally { live.endMaintenance() }
    }

    fun compatibleOptions(source: JSONObject): JSONObject = JSONObject().apply {
        listOf("maxSendKbps", "maxRecvKbps", "reconnectionIntervalS").forEach {
            if (source.has(it)) put(it, source.optInt(it).coerceIn(0, 1_000_000))
        }
        if (source.has("limitBandwidthInLan")) put("limitBandwidthInLan", source.optBoolean("limitBandwidthInLan"))
    }
}
