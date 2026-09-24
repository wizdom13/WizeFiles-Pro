// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.viewer.text

import java.nio.file.AccessMode
import java.nio.file.Path
import com.wisso.wizefiles.provider.common.provider

internal enum class TextEditorWriteDecision {
    WRITE,
    REQUEST_ALL_FILES_ACCESS,
    READ_ONLY
}

internal inline fun writeAccessGranted(check: () -> Unit): Boolean = runCatching(check).isSuccess

internal fun Path.canWriteInPlace(): Boolean = writeAccessGranted {
    provider.checkAccess(this, AccessMode.WRITE)
}

internal fun decideTextEditorWrite(
    writeAccessGranted: Boolean,
    isLocalPath: Boolean,
    canRequestAllFilesAccess: Boolean
): TextEditorWriteDecision = when {
    writeAccessGranted -> TextEditorWriteDecision.WRITE
    isLocalPath && canRequestAllFilesAccess -> TextEditorWriteDecision.REQUEST_ALL_FILES_ACCESS
    else -> TextEditorWriteDecision.READ_ONLY
}
