// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import java.util.Base64 as JvmBase64

@JvmInline
value class Base64(val value: String)

fun String.asBase64(): Base64 = Base64(this)

fun Base64.toByteArray(): ByteArray = JvmBase64.getDecoder().decode(value)

fun ByteArray.toBase64(): Base64 =
    JvmBase64.getEncoder().encodeToString(this).asBase64()
