// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import java.nio.file.attribute.BasicFileAttributes

interface EncryptedFileAttributes {
    fun isEncrypted(): Boolean
}

fun BasicFileAttributes.isEncrypted(): Boolean =
    (this as? EncryptedFileAttributes)?.isEncrypted() ?: false
