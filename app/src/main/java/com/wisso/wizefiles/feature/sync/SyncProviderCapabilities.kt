// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import java.net.URI

internal data class SyncProviderCapabilities(
    val stableIdentity: Boolean = false,
    val stableRevision: Boolean = false,
    val serverChecksum: Boolean = false,
    val serverSideCopy: Boolean = false,
    val serverSideMove: Boolean = false,
    val reliableTrash: Boolean = false,
    val atomicRename: Boolean = false,
    val caseSensitive: Boolean = true,
    val timestampPrecisionMillis: Long = 1_000
)

internal interface SyncProviderCapabilityResolver {
    fun capabilities(uri: String): SyncProviderCapabilities
    fun storageIdentity(uri: String): String?
}

internal object DefaultSyncProviderCapabilityResolver : SyncProviderCapabilityResolver {
    override fun capabilities(uri: String): SyncProviderCapabilities = when {
        uri.startsWith("rclone://") -> SyncProviderCapabilities(
            stableIdentity = true,
            stableRevision = true,
            serverChecksum = true,
            serverSideCopy = true,
            serverSideMove = true,
            reliableTrash = false,
            atomicRename = true,
            timestampPrecisionMillis = 1_000
        )
        uri.startsWith("file:") -> SyncProviderCapabilities(
            stableIdentity = true,
            serverChecksum = false,
            serverSideCopy = true,
            serverSideMove = true,
            atomicRename = true,
            timestampPrecisionMillis = 1
        )
        uri.startsWith("sftp://") || uri.startsWith("smb://") -> SyncProviderCapabilities(
            stableIdentity = true,
            serverSideMove = true,
            atomicRename = true,
            timestampPrecisionMillis = 1_000
        )
        else -> SyncProviderCapabilities(timestampPrecisionMillis = 2_000)
    }

    override fun storageIdentity(uri: String): String? {
        val parsed = runCatching { URI(uri) }.getOrNull() ?: return null
        val scheme = parsed.scheme?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val host = parsed.host?.lowercase()?.takeIf(String::isNotBlank) ?: return null
        val port = parsed.port.takeIf { it >= 0 }?.let { ":$it" }.orEmpty()
        return "$scheme://$host$port"
    }
}
