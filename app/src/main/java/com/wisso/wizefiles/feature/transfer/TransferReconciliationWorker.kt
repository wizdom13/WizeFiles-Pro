package com.wisso.wizefiles.feature.transfer

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import android.Manifest
import android.content.pm.PackageManager
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.Worker
import androidx.work.WorkerParameters
import com.wisso.wizefiles.R
import java.util.concurrent.TimeUnit

class TransferReconciliationWorker(
    context: Context,
    parameters: WorkerParameters
) : Worker(context, parameters) {
    override fun doWork(): Result {
        val recovered = TransferRepository.recoverInterrupted()
        com.wisso.wizefiles.feature.sync.SyncRepository.reconcileInterruptedRuns()
        TransferDatabase.pruneHistory(
            System.currentTimeMillis() - TimeUnit.DAYS.toMillis(HISTORY_DAYS)
        )
        val recoverable = TransferDatabase.operations(
            setOf(TransferOperationState.RECOVERABLE, TransferOperationState.QUEUED)
        ).size
        if (recovered > 0 || recoverable > 0) postResumeNotification(recoverable)
        return Result.success()
    }

    private fun postResumeNotification(count: Int) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    applicationContext.getString(R.string.transfer_center_title),
                    NotificationManager.IMPORTANCE_DEFAULT
                )
            )
        }
        val intent = PendingIntent.getActivity(
            applicationContext,
            NOTIFICATION_ID,
            Intent(applicationContext, TransferCenterActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transfer_white_24dp)
            .setContentTitle(applicationContext.getString(R.string.transfer_ready_to_resume))
            .setContentText(
                applicationContext.resources.getQuantityString(
                    R.plurals.transfer_recoverable_count,
                    count,
                    count
                )
            )
            .setContentIntent(intent)
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(applicationContext).notify(NOTIFICATION_ID, notification)
        }
    }

    companion object {
        private const val UNIQUE_WORK = "transfer-reconciliation"
        private const val CHANNEL_ID = "transfer-recovery"
        private const val NOTIFICATION_ID = 0x575A
        private const val HISTORY_DAYS = 30L

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK,
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<TransferReconciliationWorker>().build()
            )
        }
    }
}

class TransferBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            TransferReconciliationWorker.enqueue(context)
        }
    }
}
