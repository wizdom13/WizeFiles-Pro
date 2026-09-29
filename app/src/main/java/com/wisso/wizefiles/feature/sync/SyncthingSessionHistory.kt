// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import com.wisso.wizefiles.core.app.application
import java.io.File
import org.json.JSONArray
import org.json.JSONObject

internal object SyncthingSessionHistory {
    private val store get() = SyncthingJsonStore(File(application.noBackupFilesDir,
        "syncthing-state/sessions.json"))

    fun milestones(profileId: String): JSONObject = store.read().optJSONObject("milestones")
        ?.optJSONObject(profileId) ?: JSONObject()

    fun records(profileId: String): List<JSONObject> {
        val rows = store.read().optJSONArray("sessions") ?: return emptyList()
        return (0 until rows.length()).map(rows::getJSONObject)
            .filter { it.optString("profileId") == profileId }
    }

    fun record(profileId: String, runId: String, outcome: SyncthingSessionOutcome,
        pendingItems: Long = 0, pendingBytes: Long = 0, message: String = "",
        nextRetryAt: Long = 0, foreground: Boolean = false) {
        store.update { root ->
            val previous = root.optJSONArray("sessions") ?: JSONArray()
            val rows = (0 until previous.length()).map(previous::getJSONObject).toMutableList()
            val latest = rows.firstOrNull { it.optString("runId") == runId }
            val entry = if (outcome == SyncthingSessionOutcome.RUNNING) {
                JSONObject().put("profileId", profileId).put("runId", runId)
                    .put("startedAt", System.currentTimeMillis())
                    .put("attempt", (latest?.optInt("attempt") ?: 0) + 1)
                    .put("foreground", foreground)
            } else latest?.takeIf { it.optString("outcome") == "RUNNING" } ?: JSONObject()
                .put("profileId", profileId).put("runId", runId)
                .put("startedAt", System.currentTimeMillis())
            rows.remove(entry)
            entry.put("outcome", outcome.name).put("updatedAt", System.currentTimeMillis())
                .put("pendingItems", pendingItems).put("pendingBytes", pendingBytes)
                .put("message", message.take(320)).put("nextRetryAt", nextRetryAt)
            if (outcome != SyncthingSessionOutcome.RUNNING) {
                SyncthingProgressJournal.read(profileId)?.takeIf { it.optString("runId") == runId }?.let {
                    entry.put("receivedBytes", it.optLong("receivedBytes")).put("sentBytes", it.optLong("sentBytes"))
                        .put("errors", it.optJSONArray("errors") ?: JSONArray())
                }
            }
            val milestones = root.objectOrCreate("milestones").objectOrCreate(profileId)
            if (outcome == SyncthingSessionOutcome.COMPLETED) milestones.put("success", System.currentTimeMillis())
            if (outcome in setOf(SyncthingSessionOutcome.FAILED, SyncthingSessionOutcome.INTERRUPTED)) {
                milestones.put("failure", System.currentTimeMillis())
            }
            rows.add(0, entry)
            val counts = mutableMapOf<String, Int>()
            root.put("sessions", JSONArray(rows.filter { row ->
                val id = row.optString("profileId")
                val count = counts.getOrDefault(id, 0) + 1
                counts[id] = count
                count <= 20
            }.take(500)))
        }
    }
}
