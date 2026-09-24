// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.nearby

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.wisso.wizefiles.R

internal class NearbyTransferForegroundNotifier(
    private val context: Context
) {
    fun createChannel() {
        context.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "Nearby Transfer", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun notification(text: String, ongoing: Boolean = true): Notification {
        val content = PendingIntent.getActivity(
            context,
            0,
            Intent(context, NearbyTransferActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val cancel = PendingIntent.getService(
            context,
            1,
            Intent(context, NearbyTransferService::class.java)
                .setAction(NearbyTransferService.ACTION_CANCEL),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transfer_white_24dp)
            .setContentTitle("Nearby Transfer")
            .setContentText(text)
            .setContentIntent(content)
            .setOngoing(ongoing)
            .addAction(0, "Cancel", cancel)
            .build()
    }

    private companion object {
        const val CHANNEL_ID = "nearby_transfer"
    }
}
