// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.core.files.model

import android.content.Context
import android.text.format.Formatter
import com.wisso.wizefiles.R
import com.wisso.wizefiles.util.getQuantityString

@JvmInline
value class FileSize(val value: Long) {

    /* @see android.text.format.Formatter#formatBytes(Resources, long, int) */
    val isHumanReadableInBytes: Boolean
        get() = value <= 900

    fun formatInBytes(context: Context): String =
        context.getQuantityString(R.plurals.size_in_bytes_format, value.toInt(), value)

    fun formatHumanReadable(context: Context): String =
        Formatter.formatFileSize(context, value)
}

fun Long.asFileSize(): FileSize = FileSize(this)
