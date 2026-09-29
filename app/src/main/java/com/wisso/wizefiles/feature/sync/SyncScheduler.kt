// Copyright (C) 2026 Wize Soft (Wissam Shehadeh)
// SPDX-License-Identifier: GPL-3.0-only

package com.wisso.wizefiles.feature.sync

import android.content.Context
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.content.BroadcastReceiver
import android.content.pm.ServiceInfo
import android.os.Build
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.ForegroundInfo
import androidx.core.app.NotificationCompat
import com.wisso.wizefiles.R
import com.wisso.wizefiles.feature.transfer.TransferOperationState
import com.wisso.wizefiles.feature.transfer.TransferRepository
import com.wisso.wizefiles.util.AppLog
import kotlinx.coroutines.CancellationException
import java.time.DayOfWeek
import java.util.concurrent.TimeUnit

internal object SyncScheduler {
    fun apply(context: Context, profileId: String, schedule: SyncSchedule) {
        val manager = WorkManager.getInstance(context)
        val uniqueName = uniqueName(profileId)
        val profile = SyncRepository.profile(profileId)
        if (profile?.enabled == false) {
            manager.cancelUniqueWork(uniqueName)
            updateNextRun(profileId, 0)
            return
        }
        when (schedule.type) {
            SyncScheduleType.MANUAL -> {
                manager.cancelUniqueWork(uniqueName)
                updateNextRun(profileId, 0)
            }
            SyncScheduleType.INTERVAL -> {
                manager.enqueueUniquePeriodicWork(
                    uniqueName,
                    ExistingPeriodicWorkPolicy.UPDATE,
                    PeriodicWorkRequestBuilder<SyncRunWorker>(
                        schedule.intervalMinutes,
                        TimeUnit.MINUTES
                    )
                        .apply {
                            if (profile != null && SyncBackendRouter.kind(profile) == SyncBackendKind.SYNCTHING) {
                                setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR,
                                    SyncthingSessionOptions.decode(profile.constraintsJson).retryMinutes.toLong(), TimeUnit.MINUTES)
                            }
                        }
                        .setConstraints(constraints(schedule))
                        .setInputData(input(profileId, schedule))
                        .addTag(PROFILE_TAG_PREFIX + profileId)
                        .build()
                )
                updateNextRun(
                    profileId,
                    System.currentTimeMillis() +
                        TimeUnit.MINUTES.toMillis(schedule.intervalMinutes)
                )
            }
            SyncScheduleType.DAILY,
            SyncScheduleType.WEEKLY -> enqueueWallClock(context, profileId, schedule)
        }
    }

    fun enqueueWallClock(
        context: Context,
        profileId: String,
        schedule: SyncSchedule,
        nowMillis: Long = System.currentTimeMillis(),
        policy: ExistingWorkPolicy = ExistingWorkPolicy.REPLACE
    ) {
        val delay = SyncScheduleCalculator.nextDelay(schedule, nowMillis)
        val request = OneTimeWorkRequestBuilder<SyncRunWorker>()
            .setInitialDelay(delay.toMillis(), TimeUnit.MILLISECONDS)
            .apply {
                val profile = SyncRepository.profile(profileId)
                if (profile != null && SyncBackendRouter.kind(profile) == SyncBackendKind.SYNCTHING) {
                    setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR,
                        SyncthingSessionOptions.decode(profile.constraintsJson).retryMinutes.toLong(), TimeUnit.MINUTES)
                }
            }
            .setConstraints(constraints(schedule))
            .setInputData(input(profileId, schedule))
            .addTag(PROFILE_TAG_PREFIX + profileId)
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            uniqueName(profileId),
            policy,
            request
        )
        updateNextRun(profileId, nowMillis + delay.toMillis())
    }

    fun cancel(context: Context, profileId: String) {
        WorkManager.getInstance(context).cancelUniqueWork(uniqueName(profileId))
        updateNextRun(profileId, 0)
    }

    internal fun isOnWifi(context: Context): Boolean {
        val manager = context.getSystemService(ConnectivityManager::class.java)
        val network = manager.activeNetwork ?: return false
        return manager.getNetworkCapabilities(network)
            ?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
    }

    internal fun constraints(schedule: SyncSchedule) = Constraints.Builder()
        .setRequiresCharging(schedule.chargingOnly)
        .setRequiresBatteryNotLow(schedule.batteryNotLow)
        .setRequiresStorageNotLow(schedule.storageNotLow)
        .setRequiredNetworkType(
            when {
                schedule.unmeteredOnly -> NetworkType.UNMETERED
                schedule.wifiOnly -> NetworkType.CONNECTED
                else -> NetworkType.NOT_REQUIRED
            }
        )
        .build()

    private fun input(profileId: String, schedule: SyncSchedule) = Data.Builder()
        .putString(KEY_PROFILE_ID, profileId)
        .putBoolean(KEY_WIFI_ONLY, schedule.wifiOnly)
        .putString(KEY_SCHEDULE_TYPE, schedule.type.name)
        .putLong(KEY_INTERVAL_MINUTES, schedule.intervalMinutes)
        .putInt(KEY_LOCAL_HOUR, schedule.localTime.hour)
        .putInt(KEY_LOCAL_MINUTE, schedule.localTime.minute)
        .putString(KEY_WEEK_DAYS, schedule.daysOfWeek.joinToString(",") { it.name })
        .putBoolean(KEY_CHARGING_ONLY, schedule.chargingOnly)
        .putBoolean(KEY_BATTERY_NOT_LOW, schedule.batteryNotLow)
        .putBoolean(KEY_STORAGE_NOT_LOW, schedule.storageNotLow)
        .putBoolean(KEY_UNMETERED_ONLY, schedule.unmeteredOnly)
        .putBoolean(KEY_RUN_WHEN_AVAILABLE, schedule.runWhenConstraintsAvailable)
        .build()

    internal fun decode(data: Data): SyncSchedule = SyncSchedule(
        type = SyncScheduleType.valueOf(data.getString(KEY_SCHEDULE_TYPE) ?: SyncScheduleType.MANUAL.name),
        intervalMinutes = data.getLong(KEY_INTERVAL_MINUTES, 15),
        localTime = java.time.LocalTime.of(
            data.getInt(KEY_LOCAL_HOUR, 2),
            data.getInt(KEY_LOCAL_MINUTE, 0)
        ),
        daysOfWeek = data.getString(KEY_WEEK_DAYS).orEmpty().split(',')
            .filter(String::isNotBlank).map(DayOfWeek::valueOf).toSet()
            .ifEmpty { setOf(DayOfWeek.SUNDAY) },
        chargingOnly = data.getBoolean(KEY_CHARGING_ONLY, false),
        batteryNotLow = data.getBoolean(KEY_BATTERY_NOT_LOW, true),
        storageNotLow = data.getBoolean(KEY_STORAGE_NOT_LOW, true),
        unmeteredOnly = data.getBoolean(KEY_UNMETERED_ONLY, false),
        wifiOnly = data.getBoolean(KEY_WIFI_ONLY, false),
        runWhenConstraintsAvailable = data.getBoolean(KEY_RUN_WHEN_AVAILABLE, true)
    )

    private fun uniqueName(profileId: String) = "folder-sync-$profileId"

    private fun updateNextRun(profileId: String, nextRunAtMillis: Long) {
        SyncRepository.profile(profileId)?.let { profile ->
            SyncRepository.saveProfile(
                profile.copy(
                    nextRunAtMillis = nextRunAtMillis,
                    updatedAtMillis = System.currentTimeMillis()
                )
            )
        }
    }

    private const val PROFILE_TAG_PREFIX = "folder-sync-profile-"
    internal const val KEY_PROFILE_ID = "profile_id"
    private const val KEY_WIFI_ONLY = "wifi_only"
    private const val KEY_SCHEDULE_TYPE = "schedule_type"
    private const val KEY_INTERVAL_MINUTES = "interval_minutes"
    private const val KEY_LOCAL_HOUR = "local_hour"
    private const val KEY_LOCAL_MINUTE = "local_minute"
    private const val KEY_WEEK_DAYS = "week_days"
    private const val KEY_CHARGING_ONLY = "charging_only"
    private const val KEY_BATTERY_NOT_LOW = "battery_not_low"
    private const val KEY_STORAGE_NOT_LOW = "storage_not_low"
    private const val KEY_UNMETERED_ONLY = "unmetered_only"
    private const val KEY_RUN_WHEN_AVAILABLE = "run_when_available"
}

internal object SyncScheduledRunCoordinator {
    suspend fun run(context: Context, profileId: String, retryAttempt: Int = 0): SyncWorkerResult =
        if (SyncRepository.profile(profileId)?.let(SyncBackendRouter::kind) == SyncBackendKind.SYNCTHING) {
            SyncthingSyncRunner.run(context, profileId, SyncRunTrigger.SCHEDULED, retryAttempt = retryAttempt)
        } else SyncRunCoordinator().runScheduled(profileId)
}

internal enum class SyncWorkerResult {
    SUCCESS,
    RETRY,
    FAILURE
}

internal class SyncRunWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val profileId = inputData.getString(SyncScheduler.KEY_PROFILE_ID) ?: return Result.failure()
        // Scheduled work must remain a normal WorkManager background task. Android can reject
        // foreground-service promotion when the app is not user-visible.
        SyncRecoveryManager.reconcileDetachedRun(profileId)
        val schedule = SyncScheduler.decode(inputData)
        val profile = SyncRepository.profile(profileId) ?: return Result.success()
        if (!profile.enabled) return Result.success()
        if (schedule.wifiOnly && !SyncScheduler.isOnWifi(applicationContext)) return Result.retry()
        val result = if (SyncBackendRouter.kind(profile) == SyncBackendKind.SYNCTHING) {
            // The Syncthing runner resumes recoverable work regardless of its original trigger,
            // while keeping an explicit user pause intact. A manual attempt may have exhausted
            // its retry budget before this independent scheduled session starts.
            SyncScheduledRunCoordinator.run(applicationContext, profileId, runAttemptCount)
        } else {
            val paused = SyncRepository.runs(profileId).firstOrNull {
                it.state == SyncRunState.PAUSED && it.trigger == SyncRunTrigger.SCHEDULED
            }
            val recoverable = paused?.takeIf {
                TransferRepository.operation(it.transferOperationId)?.state == TransferOperationState.RECOVERABLE
            }
            val hasOtherActiveRun = SyncRepository.runs(profileId).any {
                !it.state.isTerminal && it.id != recoverable?.id
            }
            if (recoverable != null) {
                runCatching { SyncRunCoordinator().resume(recoverable.id) }
                    .fold(
                        onSuccess = { if (it.failed == 0) SyncWorkerResult.SUCCESS else SyncWorkerResult.RETRY },
                        onFailure = { SyncWorkerResult.RETRY }
                    )
            } else if (paused != null || hasOtherActiveRun) {
                SyncWorkerResult.SUCCESS
            } else {
                SyncScheduledRunCoordinator.run(applicationContext, profileId, runAttemptCount)
            }
        }
        SyncRepository.runs(profileId).firstOrNull {
            it.state == SyncRunState.PREVIEW_READY ||
                it.state == SyncRunState.NEEDS_ATTENTION ||
                it.state == SyncRunState.SAFETY_BLOCKED
        }?.let { notifyAttention(applicationContext, it) }
        if (result != SyncWorkerResult.RETRY &&
            (schedule.type == SyncScheduleType.DAILY || schedule.type == SyncScheduleType.WEEKLY)) {
            SyncScheduler.enqueueWallClock(
                applicationContext,
                profileId,
                schedule,
                policy = ExistingWorkPolicy.APPEND_OR_REPLACE
            )
        }
        return when (result) {
            SyncWorkerResult.SUCCESS -> Result.success()
            SyncWorkerResult.RETRY -> Result.retry()
            // The failed attempt is already in history. Completing this work allows the next
            // wall-clock schedule to run instead of inheriting a failed prerequisite.
            SyncWorkerResult.FAILURE -> if (SyncBackendRouter.kind(profile) == SyncBackendKind.SYNCTHING)
                Result.success() else Result.failure()
        }
    }

}

internal class SyncResumeWorker(
    context: Context,
    parameters: WorkerParameters
) : CoroutineWorker(context, parameters) {
    override suspend fun doWork(): Result {
        val runId = inputData.getString(KEY_RUN_ID) ?: return Result.failure()
        val run = SyncRepository.run(runId) ?: return Result.failure()
        val foreground = tryEnterSyncForeground(run.profileId)
        SyncRecoveryManager.reconcileDetachedRun(run.profileId)
        if (SyncRepository.profile(run.profileId)?.let(SyncBackendRouter::kind) == SyncBackendKind.SYNCTHING) {
            val schedule = SyncScheduleCodec.decode(SyncRepository.profile(run.profileId)?.scheduleJson ?: "{}")
            if (schedule.wifiOnly && !SyncScheduler.isOnWifi(applicationContext)) return Result.retry()
            return when (SyncthingSyncRunner.run(applicationContext, run.profileId, SyncRunTrigger.RETRY, runId,
                    foreground, runAttemptCount)) {
                SyncWorkerResult.SUCCESS -> Result.success()
                SyncWorkerResult.RETRY -> Result.retry()
                SyncWorkerResult.FAILURE -> Result.failure()
            }
        }
        return runCatching {
            val coordinator = SyncRunCoordinator()
            when (run.state) {
                SyncRunState.APPROVED -> coordinator.executeApproved(run)
                SyncRunState.QUEUED -> coordinator.executeQueued(run)
                else -> coordinator.resume(runId)
            }
        }
            .fold(
                onSuccess = { Result.success() },
                onFailure = {
                    if (SyncRepository.run(runId)?.state == SyncRunState.CANCELLED) {
                        Result.success()
                    } else {
                        Result.retry()
                    }
                }
            )
    }

    companion object {
        private const val KEY_RUN_ID = "sync_run_id"

        fun enqueue(context: Context, runId: String) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                "folder-sync-resume-$runId",
                ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequestBuilder<SyncResumeWorker>()
                    .apply {
                        val profile = SyncRepository.run(runId)?.let { SyncRepository.profile(it.profileId) }
                        if (profile != null && SyncBackendRouter.kind(profile) == SyncBackendKind.SYNCTHING) {
                            setConstraints(SyncScheduler.constraints(SyncScheduleCodec.decode(profile.scheduleJson)))
                            setBackoffCriteria(androidx.work.BackoffPolicy.LINEAR,
                                SyncthingSessionOptions.decode(profile.constraintsJson).retryMinutes.toLong(), TimeUnit.MINUTES)
                        }
                    }
                    .setInputData(Data.Builder().putString(KEY_RUN_ID, runId).build())
                    .build()
            )
        }
    }
}

internal suspend fun CoroutineWorker.tryEnterSyncForeground(profileId: String): Boolean {
    try {
        setForeground(syncForegroundInfo(applicationContext, profileId))
        return true
    } catch (exception: Exception) {
        if (!exception.isRecoverableSyncForegroundFailure()) throw exception
        AppLog.w(
            "FolderSync",
            "Foreground start was denied; continuing as WorkManager background work",
            exception
        )
        return false
    }
}

internal fun Throwable.isRecoverableSyncForegroundFailure(): Boolean {
    val causes = generateSequence(this) { it.cause }.toList()
    if (causes.any { it is CancellationException }) return false
    return causes.any { cause ->
        cause is SecurityException ||
            cause is IllegalStateException ||
            cause.javaClass.name == "android.app.ForegroundServiceStartNotAllowedException" ||
            cause.javaClass.name == "android.app.BackgroundServiceStartNotAllowedException"
    }
}

private fun syncForegroundInfo(context: Context, profileId: String): ForegroundInfo {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                SYNC_CHANNEL_ID,
                context.getString(R.string.sync_notification_title),
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }
    val profileName = SyncRepository.profile(profileId)?.name ?: profileId
    val contentIntent = PendingIntent.getActivity(
        context,
        profileId.hashCode(),
        Intent(context, SyncProfilesActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val notification = NotificationCompat.Builder(context, SYNC_CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_transfer_white_24dp)
        .setContentTitle(context.getString(R.string.sync_notification_title))
        .setContentText(context.getString(R.string.sync_notification_running, profileName))
        .setContentIntent(contentIntent)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .build()
    if (SyncRepository.profile(profileId)?.let(SyncBackendRouter::kind) == SyncBackendKind.SYNCTHING) {
        // A user stop has different recovery semantics from Android interrupting the worker.
        listOf(SyncthingControlReceiver.ACTION_PAUSE to R.string.transfer_pause,
            SyncthingControlReceiver.ACTION_CANCEL to android.R.string.cancel).forEach { (action, label) ->
            val pending = PendingIntent.getBroadcast(context, profileId.hashCode(),
                Intent(context, SyncthingControlReceiver::class.java).setAction(action).putExtra("profile", profileId),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            notification.actions = (notification.actions.orEmpty().toList() +
                android.app.Notification.Action.Builder(null, context.getString(label), pending).build()).toTypedArray()
        }
    }
    val id = SYNC_NOTIFICATION_BASE_ID + (profileId.hashCode() and 0x0FFF)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
    } else {
        ForegroundInfo(id, notification)
    }
}

private fun notifyAttention(context: Context, run: SyncRun) {
    val manager = context.getSystemService(NotificationManager::class.java)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        manager.createNotificationChannel(
            NotificationChannel(
                SYNC_ATTENTION_CHANNEL_ID,
                context.getString(R.string.sync_notification_attention_title),
                NotificationManager.IMPORTANCE_DEFAULT
            )
        )
    }
    val profileName = SyncRepository.profile(run.profileId)?.name ?: run.profileId
    val intent = SyncPreviewActivity.createIntent(context, run.id)
    val contentIntent = PendingIntent.getActivity(
        context,
        run.id.hashCode(),
        intent,
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
    val message = when (run.state) {
        SyncRunState.PREVIEW_READY -> R.string.sync_notification_preview_required
        SyncRunState.NEEDS_ATTENTION -> R.string.sync_notification_conflicts
        else -> R.string.sync_notification_safety_blocked
    }
    manager.notify(
        SYNC_ATTENTION_NOTIFICATION_BASE_ID + (run.id.hashCode() and 0x0FFF),
        NotificationCompat.Builder(context, SYNC_ATTENTION_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_transfer_white_24dp)
            .setContentTitle(context.getString(R.string.sync_notification_attention_title))
            .setContentText(context.getString(message, profileName))
            .setContentIntent(contentIntent)
            .setAutoCancel(true)
            .build()
    )
}

private const val SYNC_CHANNEL_ID = "folder-sync"
private const val SYNC_ATTENTION_CHANNEL_ID = "folder-sync-attention"
private const val SYNC_NOTIFICATION_BASE_ID = 0x6300
private const val SYNC_ATTENTION_NOTIFICATION_BASE_ID = 0x6400

class SyncScheduleReconciliationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(
                Intent.ACTION_BOOT_COMPLETED,
                Intent.ACTION_TIME_CHANGED,
                Intent.ACTION_TIMEZONE_CHANGED
            )
        ) return
        SyncRepository.profiles().filter(SyncProfile::enabled).forEach { profile ->
            SyncScheduler.apply(context, profile.id, SyncScheduleCodec.decode(profile.scheduleJson))
        }
    }
}
