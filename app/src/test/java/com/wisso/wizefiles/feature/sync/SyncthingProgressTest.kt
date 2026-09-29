// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class SyncthingProgressTest {
    @Test fun countersExcludeEarlierTrafficAndHandleConnectionReset() {
        val counter = SyncthingByteCounter(1000)
        assertEquals(40L, counter.observe(1040))
        assertEquals(50L, counter.observe(10))
        assertEquals(55L, counter.observe(15))
        assertEquals(55L, counter.observe(15))
    }

    @Test fun statusUsesWireDeltasAndKeepsFileErrorsAndPeerNeeds() {
        var bytes = 1000
        val reader = SyncthingProgressReader(SyncthingControl { _, path, _ ->
            when {
                path == "/rest/system/connections" -> """{"total":{"inBytesTotal":$bytes,"outBytesTotal":900},
                    "connections":{"PEER":{"connected":true,"address":"local","type":"TCP"}}}"""
                path.startsWith("/rest/events") -> "[]"
                path.startsWith("/rest/db/need") -> """{"progress":[{"name":"photo.jpg","size":9000}],"queued":[],"rest":[]}"""
                path.startsWith("/rest/folder/errors") -> """{"errors":[{"path":"locked.txt","error":"permission denied"}]}"""
                path.startsWith("/rest/db/completion") -> """{"needItems":2,"needBytes":100,"remoteState":"valid"}"""
                path.startsWith("/rest/db/remoteneed") -> """{"files":[{"name":"outgoing.txt","size":100}]}"""
                else -> error(path)
            }
        }, "profile", "run", "folder", listOf(SyncthingDevice("PEER")))
        bytes = 1200
        val snapshot = reader.read(SyncthingFolderStatus(SyncthingFolderState.SYNCING,
            localBytes = 9_000_000, pendingBytes = 100, pendingItems = 2))
        assertEquals(200L, snapshot.getLong("receivedBytes"))
        assertEquals(0L, snapshot.getLong("sentBytes"))
        assertEquals("photo.jpg", snapshot.getJSONArray("current").getJSONObject(0).getString("name"))
        assertEquals("permission denied", snapshot.getJSONArray("errors").getJSONObject(0).getString("error"))
        assertEquals("outgoing.txt", snapshot.getJSONArray("peers").getJSONObject(0)
            .getJSONArray("files").getJSONObject(0).getString("name"))
    }
}
