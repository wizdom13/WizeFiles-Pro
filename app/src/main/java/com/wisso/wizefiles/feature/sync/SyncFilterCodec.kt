// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import org.json.JSONArray
import org.json.JSONObject

internal object SyncFilterCodec {
    fun encode(rules: SyncFilterRules): String = JSONObject()
        .put("includeHidden", rules.includeHidden)
        .put("includeSymlinks", rules.includeSymlinks)
        .put("minimumSizeBytes", rules.minimumSizeBytes)
        .put("maximumSizeBytes", rules.maximumSizeBytes)
        .put("allowedExtensions", JSONArray(rules.allowedExtensions.toList()))
        .put("excludedExtensions", JSONArray(rules.excludedExtensions.toList()))
        .put("excludedPathPrefixes", JSONArray(rules.excludedPathPrefixes.toList()))
        .put("maximumDepth", rules.maximumDepth)
        .toString()

    fun decode(value: String): SyncFilterRules = runCatching {
        val json = JSONObject(value)
        SyncFilterRules(
            includeHidden = json.optBoolean("includeHidden", false),
            includeSymlinks = json.optBoolean("includeSymlinks", false),
            minimumSizeBytes = json.optLong("minimumSizeBytes", 0),
            maximumSizeBytes = json.optLong("maximumSizeBytes", Long.MAX_VALUE),
            allowedExtensions = json.optJSONArray("allowedExtensions").strings(),
            excludedExtensions = json.optJSONArray("excludedExtensions").strings(),
            excludedPathPrefixes = json.optJSONArray("excludedPathPrefixes").strings(),
            maximumDepth = json.optInt("maximumDepth", Int.MAX_VALUE)
        )
    }.getOrElse { SyncFilterRules() }

    private fun JSONArray?.strings(): Set<String> = buildSet {
        val array = this@strings ?: return@buildSet
        repeat(array.length()) { index -> array.optString(index).takeIf(String::isNotBlank)?.let(::add) }
    }
}
