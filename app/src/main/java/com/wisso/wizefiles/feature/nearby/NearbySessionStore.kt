// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.content.Context
import org.json.JSONObject
import org.json.JSONArray
import com.wisso.wizefiles.storage.NearbyPayloadCheckpoint
import com.wisso.wizefiles.storage.NearbyPayloadDirection
import com.wisso.wizefiles.storage.NearbyPayloadRecoveryPolicy
import com.wisso.wizefiles.storage.NearbyPayloadState

internal data class NearbySessionSnapshot(
    val operationId: String,
    val sessionId: String,
    val role: NearbyRole,
    val peerName: String,
    val destinationUri: String,
    val conflictPolicy: NearbyConflictPolicy,
    val payloadCheckpoints: List<NearbyPayloadCheckpoint> = emptyList(),
    val updatedAtMillis: Long = System.currentTimeMillis()
)

internal class NearbySessionStore(context: Context) {
    private val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)

    fun save(snapshot: NearbySessionSnapshot) {
        val encoded = JSONObject()
            .put("version", FORMAT_VERSION)
            .put("operationId", snapshot.operationId)
            .put("sessionId", snapshot.sessionId)
            .put("role", snapshot.role.name)
            .put("peerName", snapshot.peerName)
            .put("destinationUri", snapshot.destinationUri)
            .put("conflictPolicy", snapshot.conflictPolicy.name)
            .put("updatedAtMillis", snapshot.updatedAtMillis)
            .put("payloadCheckpoints", JSONArray().apply {
                snapshot.payloadCheckpoints.take(NearbyPayloadRecoveryPolicy.MAX_CHECKPOINTS).forEach {
                    put(JSONObject().put("id", it.payloadId).put("direction", it.direction.name).put("state", it.state.name))
                }
            })
            .toString()
        if (encoded.toByteArray().size > MAX_SNAPSHOT_BYTES) return
        preferences.edit().putString(snapshot.operationId, encoded).apply()
    }

    fun load(operationId: String): NearbySessionSnapshot? {
        val raw = preferences.getString(operationId, null) ?: return null
        return runCatching {
            require(raw.toByteArray().size <= MAX_SNAPSHOT_BYTES) { "Nearby session snapshot is too large" }
            val root = JSONObject(raw)
            val payloads = root.optJSONArray("payloadCheckpoints")
            val checkpoints = buildList {
                if (payloads != null) {
                    require(payloads.length() <= NearbyPayloadRecoveryPolicy.MAX_CHECKPOINTS) {
                        "Too many Nearby payload checkpoints"
                    }
                    repeat(payloads.length()) { index ->
                        val item = payloads.getJSONObject(index)
                        add(NearbyPayloadCheckpoint(item.getLong("id"), NearbyPayloadDirection.valueOf(item.getString("direction")), NearbyPayloadState.valueOf(item.getString("state"))))
                    }
                }
            }
            NearbySessionSnapshot(
                operationId = root.getString("operationId"),
                sessionId = root.getString("sessionId"),
                role = NearbyRole.valueOf(root.getString("role")),
                peerName = root.optString("peerName"),
                destinationUri = root.optString("destinationUri"),
                conflictPolicy = NearbyConflictPolicy.valueOf(
                    root.optString("conflictPolicy", NearbyConflictPolicy.KEEP_BOTH.name)
                ),
                payloadCheckpoints = checkpoints,
                updatedAtMillis = root.optLong("updatedAtMillis", 0)
            )
        }.getOrElse {
            preferences.edit().remove(operationId).commit()
            null
        }
    }

    fun remove(operationId: String) {
        preferences.edit().remove(operationId).apply()
    }

    companion object {
        private const val PREFERENCES = "nearby_transfer_sessions_v1"
        private const val FORMAT_VERSION = 2
        private const val MAX_SNAPSHOT_BYTES = 64 * 1024
    }
}
