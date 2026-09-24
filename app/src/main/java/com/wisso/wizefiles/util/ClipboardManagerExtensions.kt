// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import com.wisso.wizefiles.R
import com.wisso.wizefiles.core.app.application

var ClipboardManager.primaryText: CharSequence
    get() = primaryClip?.firstOrNull()?.coerceToText(application)!!
    set(value) {
        setPrimaryClip(ClipData.newPlainText(null, value))
    }

private const val TOAST_COPIED_TEXT_MAX_LENGTH = 40

fun ClipboardManager.copyText(text: CharSequence, context: Context) {
    primaryText = text
    var copiedText = text
    var ellipsized = false
    if (copiedText.length > TOAST_COPIED_TEXT_MAX_LENGTH) {
        copiedText = copiedText.subSequence(0, TOAST_COPIED_TEXT_MAX_LENGTH)
        ellipsized = true
    }
    val indexOfFirstNewline = copiedText.indexOf('\n')
    if (indexOfFirstNewline != -1) {
        val indexOfSecondNewline = copiedText.indexOf('\n', indexOfFirstNewline + 1)
        if (indexOfSecondNewline != -1) {
            copiedText = copiedText.subSequence(0, indexOfSecondNewline)
            ellipsized = true
        }
    }
    if (ellipsized) {
        copiedText = "$copiedText…"
    }
    context.showToast(context.getString(R.string.copied_to_clipboard_format, copiedText))
}
