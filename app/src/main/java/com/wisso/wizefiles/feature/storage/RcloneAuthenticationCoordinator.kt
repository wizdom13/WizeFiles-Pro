// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.content.Context
import com.wisso.wizefiles.provider.rclone.RcloneEngine

internal object RcloneAuthenticationCoordinator {
    suspend fun start(context: Context, openUrl: (String) -> Boolean) {
        RcloneAuthorizationService.startAndAwait(context.applicationContext)
        RcloneEngine.installOAuthURLListener(openUrl)
    }

    fun stop(context: Context) {
        RcloneEngine.clearOAuthURLListener()
        RcloneAuthorizationService.stop(context.applicationContext)
    }
}
