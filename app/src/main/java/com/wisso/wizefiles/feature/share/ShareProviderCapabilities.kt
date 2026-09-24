// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.share

internal data class ShareProviderCapabilities(
    val read:Boolean,
    val create:Boolean,
    val update:Boolean,
    val delete:Boolean,
    val seekableRead:Boolean,
    val seekableWrite:Boolean,
    val atomicMove:Boolean
)

internal object ShareProviderCapabilityResolver {
    fun resolve(uri:String):ShareProviderCapabilities = when {
        uri.startsWith("file:") -> ShareProviderCapabilities(true,true,true,true,true,true,true)
        uri.startsWith("rclone:") -> ShareProviderCapabilities(true,true,true,true,true,false,false)
        uri.startsWith("content:") -> ShareProviderCapabilities(true,true,true,true,true,false,false)
        uri.startsWith("smb:") || uri.startsWith("sftp:") || uri.startsWith("ftp:") ->
            ShareProviderCapabilities(true,true,true,true,true,false,false)
        else -> ShareProviderCapabilities(true,false,false,false,false,false,false)
    }
}
