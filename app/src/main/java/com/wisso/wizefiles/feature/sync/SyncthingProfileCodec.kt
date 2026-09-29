// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only
package com.wisso.wizefiles.feature.sync

import org.json.JSONObject

internal object SyncthingProfileCodec {
    fun encode(profile: SyncProfile): JSONObject = JSONObject().put("id", profile.id).put("name", profile.name)
        .put("sourceUri", profile.sourceUri).put("destinationUri", profile.destinationUri).put("mode", profile.mode.name)
        .put("filtersJson", profile.filtersJson).put("protectionJson", profile.protectionJson)
        .put("scheduleJson", profile.scheduleJson).put("constraintsJson", profile.constraintsJson).put("enabled", profile.enabled)

    fun decode(json: JSONObject): SyncProfile = SyncProfile(id = json.getString("id"), name = json.getString("name"),
        sourceUri = json.getString("sourceUri"), destinationUri = json.getString("destinationUri"),
        mode = SyncMode.valueOf(json.getString("mode")), propagateDeletions = true,
        filtersJson = json.getString("filtersJson"), protectionJson = json.getString("protectionJson"),
        scheduleJson = json.getString("scheduleJson"), constraintsJson = json.getString("constraintsJson"),
        enabled = json.getBoolean("enabled"))
}
