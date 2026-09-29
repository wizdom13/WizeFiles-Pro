// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.core.app.application
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import org.json.JSONObject

internal object SyncthingProgressJournal {
    private val live = ConcurrentHashMap<String, String>()
    private val lastWrite = ConcurrentHashMap<String, Long>()
    private val store get() = SyncthingJsonStore(File(application.noBackupFilesDir, "syncthing-state/progress.json"))
    fun read(profileId: String): JSONObject? = live[profileId]?.let(::JSONObject)
        ?: store.read().optJSONObject(profileId)

    fun publish(profileId: String, snapshot: JSONObject, force: Boolean = false) {
        live[profileId] = snapshot.toString()
        val now = android.os.SystemClock.elapsedRealtime()
        if (force || now - lastWrite.getOrDefault(profileId, 0L) >= 15_000) {
            store.update { root ->
                root.put(profileId, snapshot)
                val existing = SyncRepository.profiles().map { it.id }.toSet()
                root.keys().asSequence().toList().filter { it !in existing }.forEach(root::remove)
            }
            lastWrite[profileId] = now
        }
    }

    fun finish(profileId: String, runId: String, outcome: SyncthingSessionOutcome) {
        val snapshot = read(profileId)?.takeIf { it.optString("runId") == runId } ?: return
        publish(profileId, snapshot.put("active", false).put("outcome", outcome.name), force = true)
    }
}
