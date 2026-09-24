// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage.path

import android.content.ContentResolver
import android.net.Uri
import com.wisso.wizefiles.storage.createOrLog
import com.wisso.wizefiles.util.takeIfNotEmpty
import java.io.File
import java.net.URI

fun AppPath.toUriString(): String = when (this) {
    is LocalAppPath -> file.toURI().toString()
    else -> rawPath
}

fun String.toAppPathOrNull(): AppPath? {
    val uri = URI::class.createOrLog(this) ?: return null
    return if (uri.scheme == ContentResolver.SCHEME_FILE || uri.scheme == null) {
        LocalAppPath(File(uri.path ?: return null))
    } else {
        RawAppPath(uri.toString())
    }
}

fun Uri.toAppPathOrNull(): AppPath? =
    when (scheme) {
        ContentResolver.SCHEME_FILE, null -> path?.takeIfNotEmpty()?.let { LocalAppPath(File(it)) }
        ContentResolver.SCHEME_CONTENT -> {
            val uri = URI::class.createOrLog(toString())
                // Some people use Uri.parse() without encoding their path. Let's try saving
                // them by calling the other URI constructor that encodes everything.
                ?: URI::class.createOrLog(scheme, userInfo, host, port, path, query, fragment)
            uri?.toString()?.let(::RawAppPath)
        }
        else -> null
    }
