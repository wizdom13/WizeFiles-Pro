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
