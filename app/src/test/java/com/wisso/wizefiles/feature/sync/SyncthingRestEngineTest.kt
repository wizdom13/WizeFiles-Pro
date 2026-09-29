// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SyncthingRestEngineTest {
    @get:Rule val temporary = TemporaryFolder()
    private val endpoint = SyncthingEndpoint("PEER", "documents")
    private fun profile(uri: String = "file:///documents") = SyncProfile(name = "test", sourceUri = uri,
        destinationUri = SyncthingEndpointCodec.encode(endpoint), mode = SyncMode.TWO_WAY, propagateDeletions = true)

    @Test fun `local idle alone does not mean peer completed`() {
        var connected = false
        var remoteState = "unknown"
        var needed = 0L
        val engine = SyncthingRestEngine(SyncthingControl { _, path, _ ->
            when {
                path.startsWith("/rest/db/status") -> "{\"state\":\"idle\",\"needTotalItems\":0}"
                path.startsWith("/rest/db/completion") -> JSONObject().put("remoteState", remoteState)
                    .put("needItems", needed).toString()
                path == "/rest/system/connections" -> JSONObject().put("connections",
                    JSONObject().put("PEER", JSONObject().put("connected", connected))).toString()
                else -> error(path)
            }
        }, { profile() }, { emptyList() })
        assertEquals(SyncthingFolderState.DISCONNECTED, engine.folderStatus("id").state)
        connected = true
        assertEquals(SyncthingFolderState.DISCONNECTED, engine.folderStatus("id").state)
        remoteState = "valid"
        needed = 1
        assertEquals(SyncthingFolderState.SYNCING, engine.folderStatus("id").state)
        needed = 0
        assertEquals(SyncthingFolderState.IDLE, engine.folderStatus("id").state)
    }

    @Test fun `configure paused before writing ignores and preserve user settings`() {
        val local = temporary.newFolder()
        val saved = profile(local.toURI().toString())
        val writes = mutableListOf<Pair<String, String?>>()
        val engine = SyncthingRestEngine(SyncthingControl { method, path, body ->
            if (method != "GET") { writes.add(path to body); "" } else when {
                path.startsWith("/rest/svc/deviceid") -> JSONObject().put("id", path.substringAfter("id=")).toString()
                path == "/rest/system/status" -> "{\"myID\":\"SELF\"}"
                path == "/rest/config/devices" -> "[{\"deviceID\":\"PEER\",\"compression\":\"never\"}]"
                path == "/rest/config/folders" -> "[]"
                path == "/rest/config/defaults/folder" -> "{}"
                path == "/rest/config/defaults/device" -> "{}"
                path.startsWith("/rest/db/ignores") -> "{\"ignore\":[\"*.bak\"]}"
                else -> error(path)
            }
        }, { saved }, { listOf(saved) })
        engine.ensureFolder(SyncthingFolderRequest(saved.id, saved.sourceUri, endpoint,
            SyncthingFolderMode.SEND_RECEIVE, listOf("*.tmp"), devices = listOf(
                SyncthingDevice("PEER", "Desktop"), SyncthingDevice("SECOND", "Laptop")))).requireSuccess()
        assertEquals("never", JSONObject(writes[0].second!!).getString("compression"))
        assertEquals("Laptop", JSONObject(writes[1].second!!).getString("name"))
        assertTrue(JSONObject(writes[2].second!!).getBoolean("paused"))
        assertEquals(3, JSONObject(writes[2].second!!).getJSONArray("devices").length())
        assertTrue(writes[3].first.startsWith("/rest/db/ignores"))
        assertTrue(JSONObject(writes[3].second!!).getJSONArray("ignore").toString().contains("*.bak"))
    }

    @Test fun `every selected peer must acknowledge completion`() {
        var secondConnected = false
        var secondPending = 4L
        val engine = SyncthingRestEngine(SyncthingControl { _, path, _ ->
            when {
                path.startsWith("/rest/db/status") -> "{\"state\":\"idle\",\"needTotalItems\":0}"
                path.startsWith("/rest/db/completion") -> JSONObject().put("remoteState", "valid")
                    .put("needItems", if (path.endsWith("SECOND")) secondPending else 0).toString()
                path == "/rest/system/connections" -> JSONObject().put("connections", JSONObject()
                    .put("PEER", JSONObject().put("connected", true))
                    .put("SECOND", JSONObject().put("connected", secondConnected))).toString()
                else -> error(path)
            }
        }, { profile() }, { emptyList() }, { listOf("PEER", "SECOND") })
        assertEquals(SyncthingFolderState.DISCONNECTED, engine.folderStatus("id").state)
        secondConnected = true
        assertEquals(4L, engine.folderStatus("id").pendingItems)
        assertEquals(SyncthingFolderState.SYNCING, engine.folderStatus("id").state)
        secondPending = 0
        assertEquals(SyncthingFolderState.IDLE, engine.folderStatus("id").state)
    }

    @Test fun `same folder ID and nested local scopes cannot be assigned twice`() {
        val first = temporary.newFolder()
        val nested = java.io.File(first, "nested").apply { mkdir() }
        val saved = profile(first.toURI().toString())
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingProfilePolicy.validateUnique("another", nested.toURI().toString(),
                endpoint.copy(folderId = "different"), listOf(saved))
        }
        assertThrows(IllegalArgumentException::class.java) {
            SyncthingProfilePolicy.validateUnique("another", temporary.newFolder().toURI().toString(),
                endpoint, listOf(saved))
        }
    }
}
