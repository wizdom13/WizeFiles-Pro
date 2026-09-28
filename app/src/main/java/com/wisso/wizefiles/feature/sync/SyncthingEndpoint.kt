// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

internal const val SYNCTHING_URI_SCHEME = "syncthing"

internal data class SyncthingEndpoint(
    val deviceId: String,
    val folderId: String
) {
    init {
        require(deviceId.isNotBlank()) { "Syncthing device ID cannot be blank" }
        require(folderId.isNotBlank()) { "Syncthing folder ID cannot be blank" }
        require(deviceId.length <= MAX_DEVICE_ID_LENGTH) { "Syncthing device ID is too long" }
        require(folderId.length <= MAX_FOLDER_ID_LENGTH) { "Syncthing folder ID is too long" }
        require(deviceId.none(Char::isISOControl)) { "Syncthing device ID contains control characters" }
        require(folderId.none(Char::isISOControl)) { "Syncthing folder ID contains control characters" }
    }

    companion object {
        private const val MAX_DEVICE_ID_LENGTH = 128
        private const val MAX_FOLDER_ID_LENGTH = 128
    }
}

internal object SyncthingEndpointCodec {
    fun isSyncthingUri(uri: String): Boolean =
        runCatching { URI(uri).scheme.equals(SYNCTHING_URI_SCHEME, ignoreCase = true) }
            .getOrDefault(false)

    fun encode(endpoint: SyncthingEndpoint): String =
        buildString {
            append(SYNCTHING_URI_SCHEME)
            append("://")
            append(encodeComponent(endpoint.deviceId))
            append('/')
            append(encodeComponent(endpoint.folderId))
        }

    fun decode(uri: String): SyncthingEndpoint? = runCatching {
        val parsed = URI(uri)
        if (!parsed.scheme.equals(SYNCTHING_URI_SCHEME, ignoreCase = true)) return null
        val authority = parsed.rawAuthority?.takeIf(String::isNotBlank) ?: return null
        val rawPath = parsed.rawPath?.removePrefix("/")?.takeIf(String::isNotBlank) ?: return null
        if ('/' in rawPath) return null
        if (parsed.rawQuery != null || parsed.rawFragment != null || parsed.userInfo != null) return null
        SyncthingEndpoint(
            deviceId = decodeComponent(authority),
            folderId = decodeComponent(rawPath)
        )
    }.getOrNull()

    private fun encodeComponent(value: String): String =
        URLEncoder.encode(value, StandardCharsets.UTF_8).replace("+", "%20")

    private fun decodeComponent(value: String): String =
        URLDecoder.decode(value, StandardCharsets.UTF_8)
}
