// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import android.content.Context
import java.io.File
import java.util.Base64
import org.json.JSONArray
import org.json.JSONObject

internal object SyncthingBackup {
    suspend fun export(context: Context, password: CharArray): ByteArray = SyncthingCoordinator.whileIdle {
        SyncthingSettings.migrate()
        val configuration = SyncthingSettings.configuration
        val profiles = SyncRepository.profiles().filter { SyncBackendRouter.kind(it) == SyncBackendKind.SYNCTHING }
        val runtime = SyncthingRuntime.get(context).acquire()
        val folders = JSONArray()
        val ownId: String
        val options: JSONObject
        try {
            ownId = SyncthingRestEngine(runtime).deviceId()
            options = SyncthingMigration.compatibleOptions(JSONObject(runtime.request("GET", "/rest/config/options", null)))
            val configured = JSONArray(runtime.request("GET", "/rest/config/folders", null)).objects().map { it.getString("id") }.toSet()
            profiles.forEach { profile ->
                val id = SyncthingProfilePolicy.validate(profile).folderId
                val local = SyncthingLocalFolder.resolve(profile.sourceUri)
                val saved = JSONObject(profile.constraintsJson)
                val ignoreFile = SyncthingVersionPolicy.safeFile(local, ".stignore")
                val localRules = if (ignoreFile.isFile) ignoreFile.inputStream().use(SyncthingBackupCodec::read)
                    .toString(Charsets.UTF_8).lines() else emptyList()
                val rules = if (localRules.any { it.trim().startsWith("#include") }) {
                    require(id in configured) { "Run this folder once before exporting its included ignore files" }
                    JSONObject(runtime.request("GET", "/rest/db/ignores?folder=" + SyncthingHttp.component(id), null))
                        .getJSONArray("expanded").strings()
                } else SyncthingIgnorePolicy.merge(localRules, emptyList()).dropLast(2)
                folders.put(JSONObject().put("id", id).put("profile", SyncthingProfileCodec.encode(profile))
                    .put("peers", JSONArray(configuration.peers(profile.id).map { it.id }))
                    .put("ignores", JSONArray((rules + saved.optJSONArray("syncthingImportedIgnores")?.strings().orEmpty()).distinct()))
                    .put("options", saved.optJSONObject("syncthingFolderOptions") ?: JSONObject()))
            }
        } finally { runtime.release() }
        val home = File(context.noBackupFilesDir, "syncthing")
        val identity = SyncthingIdentity(File(home, "cert.pem").readBytes(), File(home, "key.pem").readBytes())
        try {
            require(identity.validate() == ownId)
            val plain = JSONObject().put("format", "WizeFilesSyncthing").put("version", 1).put("ownId", ownId)
                .put("devices", JSONArray(configuration.devices().map { it.json() })).put("folders", folders).put("options", options)
                .put("identity", JSONObject().put("certificate", Base64.getEncoder().encodeToString(identity.certificate))
                    .put("privateKey", Base64.getEncoder().encodeToString(identity.privateKey))).toString().toByteArray(Charsets.UTF_8)
            try { SyncthingBackupCodec.encrypt(plain, password) } finally { plain.fill(0) }
        } finally { identity.privateKey.fill(0) }
    }
}
