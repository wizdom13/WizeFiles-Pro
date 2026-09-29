// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.json.JSONArray
import org.json.JSONObject

/** Counts observed wire bytes, including protocol overhead. Dataset size is never transfer progress. */
internal class SyncthingByteCounter(initial: Long) {
    private var previous = initial.coerceAtLeast(0)
    var bytes: Long = 0
        private set
    fun observe(current: Long): Long {
        val value = current.coerceAtLeast(0)
        bytes += if (value >= previous) value - previous else value
        previous = value
        return bytes
    }
}

internal class SyncthingProgressReader(
    private val control: SyncthingControl,
    private val profileId: String,
    private val runId: String,
    private val folderId: String,
    private val devices: List<SyncthingDevice>
) {
    private val folder = SyncthingHttp.component(folderId)
    private val baseline = get("/rest/system/connections").optJSONObject("total") ?: JSONObject()
    private val received = SyncthingByteCounter(baseline.optLong("inBytesTotal"))
    private val sent = SyncthingByteCounter(baseline.optLong("outBytesTotal"))
    private var eventId = 0L
    private val completed = linkedMapOf<String, JSONObject>()
    init {
        // Establish the subscription before resuming the folder; exclude earlier sessions.
        val events = events(0, 1)
        eventId = events.objects().maxOfOrNull { it.optLong("id") } ?: 0L
    }

    fun read(status: SyncthingFolderStatus): JSONObject {
        val connections = get("/rest/system/connections")
        val total = connections.optJSONObject("total") ?: JSONObject()
        val need = get("/rest/db/need?folder=$folder&page=1&perpage=100")
        val errors = get("/rest/folder/errors?folder=$folder&page=1&perpage=100")
            .optJSONArray("errors") ?: JSONArray()
        val events = events(eventId, 100)
        events.objects().forEach { event ->
            eventId = maxOf(eventId, event.optLong("id"))
            val data = event.optJSONObject("data") ?: return@forEach
            if (data.optString("folder") == folderId && (data.isNull("error") || data.optString("error").isBlank()) &&
                event.optString("type") == "ItemFinished") {
                val path = data.optString("item")
                completed.remove(path)
                completed[path] = JSONObject().put("name", path).put("action", data.optString("action"))
                    .put("time", event.optString("time"))
            }
        }
        while (completed.size > 100) completed.remove(completed.keys.first())
        val peers = devices.map { device ->
            val connection = connections.optJSONObject("connections")?.optJSONObject(device.id) ?: JSONObject()
            val completion = get("/rest/db/completion?folder=$folder&device=${SyncthingHttp.component(device.id)}")
            val remoteNeed = runCatching { get("/rest/db/remoteneed?folder=$folder&device=" +
                SyncthingHttp.component(device.id) + "&page=1&perpage=50").optJSONArray("files") }
                .getOrNull() ?: JSONArray()
            JSONObject().put("id", device.id).put("name", device.name).put("connected", connection.optBoolean("connected"))
                .put("address", connection.optString("address")).put("type", connection.optString("type"))
                .put("pendingItems", completion.optLong("needItems") + completion.optLong("needDeletes"))
                .put("pendingBytes", completion.optLong("needBytes"))
                .put("remoteState", completion.optString("remoteState")).put("files", names(remoteNeed))
        }
        return JSONObject().put("profileId", profileId).put("runId", runId)
            .put("sampledAt", System.currentTimeMillis()).put("state", status.state.name).put("active", true)
            .put("localBytes", status.localBytes).put("remoteBytes", status.remoteBytes)
            .put("pendingItems", status.pendingItems).put("pendingBytes", status.pendingBytes)
            .put("receivedBytes", received.observe(total.optLong("inBytesTotal")))
            .put("sentBytes", sent.observe(total.optLong("outBytesTotal")))
            .put("current", names(need.optJSONArray("progress")))
            .put("queued", names(need.optJSONArray("queued"), need.optJSONArray("rest")))
            .put("completed", JSONArray(completed.values.toList().asReversed()))
            .put("errors", JSONArray(errors.objects().map {
                JSONObject().put("name", it.optString("path")).put("error", it.optString("error").take(512))
            })).put("peers", JSONArray(peers))
    }

    private fun names(vararg arrays: JSONArray?): JSONArray = JSONArray(arrays.flatMap {
        it?.objects().orEmpty()
    }.take(100).map { JSONObject().put("name", it.optString("name")).put("size", it.optLong("size")) })
    private fun get(path: String) = JSONObject(control.request("GET", path, null))
    private fun events(since: Long, limit: Int) = JSONArray(control.request("GET",
        "/rest/events?since=$since&limit=$limit&timeout=0&events=ItemFinished", null))
}

internal fun JSONArray.objects(): List<JSONObject> = (0 until length()).map(::getJSONObject)
