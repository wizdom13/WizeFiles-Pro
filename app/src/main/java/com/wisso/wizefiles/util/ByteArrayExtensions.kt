// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import java.security.MessageDigest

private const val SHA1 = "SHA-1"
private val UPPERCASE_HEX = "0123456789ABCDEF".toCharArray()

fun ByteArray.sha1Digest(): ByteArray =
    MessageDigest.getInstance(SHA1).digest(this)

fun ByteArray.toHexString(): String {
    if (isEmpty()) {
        return ""
    }
    return buildString(size * 2) {
        for (value in this@toHexString) {
            val unsigned = value.toInt() and 0xFF
            append(UPPERCASE_HEX[unsigned ushr 4])
            append(UPPERCASE_HEX[unsigned and 0x0F])
        }
    }
}
