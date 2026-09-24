// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.storage

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.filejobs.fileJobNotificationTemplate
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Keeps the process runnable while rclone owns the browser OAuth callback server.
 *
 * Recent Android versions may freeze a background app while its browser sign-in
 * page is visible. That leaves rclone's localhost callback and token exchange in
 * an unusable network state. The actual configuration remains in the caller; this
 * service only spans the blocking OAuth continuation.
 */
class RcloneAuthorizationService : Service() {
    override fun onCreate() {
        super.onCreate()
        instance = this
        val notification = fileJobNotificationTemplate.createBuilder(this)
            .setContentTitle(getString(R.string.rclone_add_cloud_title))
            .setContentText(getString(R.string.rclone_oauth_explanation))
            .build()
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
        synchronized(stateLock) {
            isForeground = true
            startedLatch?.countDown()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int =
        START_NOT_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        synchronized(stateLock) {
            isForeground = false
            instance = null
            startedLatch?.countDown()
            startedLatch = null
        }
        super.onDestroy()
    }

    companion object {
        private const val NOTIFICATION_ID = 9_004
        private const val START_TIMEOUT_SECONDS = 10L
        private val stateLock = Any()

        @Volatile
        private var instance: RcloneAuthorizationService? = null
        private var isForeground = false
        private var startedLatch: CountDownLatch? = null

        fun startAndAwait(context: Context) {
            val latch = synchronized(stateLock) {
                if (isForeground) {
                    return
                }
                startedLatch ?: CountDownLatch(1).also { startedLatch = it }
            }
            ContextCompat.startForegroundService(
                context,
                Intent(context, RcloneAuthorizationService::class.java)
            )
            check(latch.await(START_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                "Timed out while starting cloud authorization"
            }
            check(isForeground) {
                "Cloud authorization service stopped before entering foreground"
            }
        }

        fun stop(context: Context) {
            instance?.let {
                ServiceCompat.stopForeground(it, ServiceCompat.STOP_FOREGROUND_REMOVE)
                it.stopSelf()
            } ?: context.stopService(Intent(context, RcloneAuthorizationService::class.java))
        }
    }
}
