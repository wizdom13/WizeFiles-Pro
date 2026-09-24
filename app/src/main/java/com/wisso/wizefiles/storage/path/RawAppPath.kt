// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import java.net.URI
import kotlinx.parcelize.Parcelize

@Parcelize
data class RawAppPath(override val rawPath: String) : AppPath {
    override val name: String
        get() {
            val uriRawNamePath = runCatching {
                URI(rawPath).takeIf { it.scheme != null }?.let { uri ->
                    if (uri.scheme.equals(ARCHIVE_URI_SCHEME, ignoreCase = true)) {
                        uri.rawQuery ?: uri.rawPath
                    } else {
                        uri.rawPath
                    }
                }
            }.getOrNull()
            val leafName = (uriRawNamePath ?: rawPath).leafName()
            return if (uriRawNamePath != null) {
                leafName.decodeUriPathSegment()
            } else {
                leafName
            }
        }
}

private const val ARCHIVE_URI_SCHEME = "archive"

private fun String.leafName(): String {
    val trimmed = trimEnd('/')
    if (trimmed.isEmpty()) {
        return "/"
    }
    return trimmed.substringAfterLast('/').ifEmpty { trimmed }
}

private fun String.decodeUriPathSegment(): String {
    if (this == "/") {
        return this
    }
    return runCatching { URI("/$this").path.removePrefix("/") }.getOrDefault(this)
}
