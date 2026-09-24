// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.io.IOException
import java.nio.channels.Channel
import java.nio.channels.FileChannel

interface ForceableChannel {
    @Throws(IOException::class)
    fun force(metaData: Boolean)
}

val Channel.isForceable: Boolean
    get() = this is ForceableChannel || this is FileChannel

@Throws(IOException::class)
fun Channel.force(metaData: Boolean) {
    val operation: (Boolean) -> Unit = when (this) {
        is FileChannel -> this::force
        is ForceableChannel -> this::force
        else -> throw UnsupportedOperationException(
            "Channel does not support forcing buffered data"
        )
    }
    operation(metaData)
}
