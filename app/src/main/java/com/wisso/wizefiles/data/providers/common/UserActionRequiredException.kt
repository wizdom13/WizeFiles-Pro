// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.provider.common

import android.content.Context
import android.content.Intent
import java.nio.file.FileSystemException
import kotlin.coroutines.Continuation

abstract class UserActionRequiredException : FileSystemException {
    constructor(file: String?) : super(file)

    constructor(file: String?, other: String?, reason: String?) : super(file, other, reason)

    abstract fun getUserAction(continuation: Continuation<Boolean>, context: Context): UserAction
}

class UserAction(
    val intent: Intent,
    val title: String,
    val message: String?
)
