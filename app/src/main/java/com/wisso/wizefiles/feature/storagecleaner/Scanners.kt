package com.wisso.wizefiles.storagecleaner

import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Environment
import android.provider.Settings
import com.wisso.wizefiles.R
import java.io.File
import java.util.concurrent.TimeUnit

internal fun isSystemApplication(flags: Int): Boolean {
    val systemFlags = ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP
    return (flags and systemFlags) != 0
}

class LargeFileScanner {
    fun scan(files: List<File>, filters: ScanFilters): List<LargeFileCandidate> {
        return files.asSequence()
            .mapNotNull { file ->
                val size = runCatching { file.length() }.getOrNull() ?: return@mapNotNull null
                if (size < filters.minLargeFileBytes) return@mapNotNull null
                LargeFileCandidate(
                    path = file.path,
                    size = size,
                    mimeType = null,
                    modifiedTimeMillis = runCatching { file.lastModified() }.getOrDefault(0L)
                )
            }
            .sortedByDescending { it.size }
            .toList()
    }
}

class DownloadScanner {
    fun scan(filters: ScanFilters): List<File> {
        val downloadDir = runCatching {
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        }.getOrNull() ?: return emptyList()
        if (!runCatching { downloadDir.exists() && downloadDir.isDirectory }.getOrDefault(false)) {
            return emptyList()
        }
        val staleThreshold = System.currentTimeMillis() - TimeUnit.DAYS.toMillis(filters.oldFileDays.toLong())
        return runCatching { downloadDir.listFiles() }
            .getOrNull()
            .orEmpty()
            .filter {
                runCatching {
                    it.isFile && it.lastModified() <= staleThreshold
                }.getOrDefault(false)
            }
    }
}

data class JunkReasonStrings(
    val temporaryFile: (Int) -> String,
    val partialDownload: (Int) -> String,
    val logFile: (Int) -> String
) {
    companion object {
        fun fromContext(context: Context) = JunkReasonStrings(
            temporaryFile = { days ->
                context.getString(
                    R.string.storage_cleaner_reason_temporary_file_age,
                    days
                )
            },
            partialDownload = { days ->
                context.getString(
                    R.string.storage_cleaner_reason_partial_download_age,
                    days
                )
            },
            logFile = { days ->
                context.getString(
                    R.string.storage_cleaner_reason_log_file_age,
                    days
                )
            }
        )
    }
}

internal enum class JunkFileKind {
    TEMPORARY,
    PARTIAL_DOWNLOAD,
    LOG
}

internal data class JunkClassification(
    val kind: JunkFileKind,
    val requiredAgeDays: Int
)

class JunkFileAnalyzer(
    private val strings: JunkReasonStrings,
    private val nowMillisProvider: () -> Long = System::currentTimeMillis
) {
    fun scan(files: List<File>, minAgeDays: Int = 7): List<JunkCandidate> {
        val nowMillis = nowMillisProvider()
        return files.asSequence().mapNotNull { file ->
            val size = runCatching { file.length() }.getOrNull()
                ?: return@mapNotNull null
            val modified = runCatching { file.lastModified() }.getOrDefault(0L)
            val classification = junkClassificationFor(
                fileName = file.name,
                modifiedTimeMillis = modified,
                nowMillis = nowMillis,
                minAgeDays = minAgeDays
            ) ?: return@mapNotNull null
            val reason = when (classification.kind) {
                JunkFileKind.TEMPORARY ->
                    strings.temporaryFile(classification.requiredAgeDays)
                JunkFileKind.PARTIAL_DOWNLOAD ->
                    strings.partialDownload(classification.requiredAgeDays)
                JunkFileKind.LOG ->
                    strings.logFile(classification.requiredAgeDays)
            }
            JunkCandidate(file.path, reason, size, modified)
        }.toList()
    }
}

internal fun junkClassificationFor(
    fileName: String,
    modifiedTimeMillis: Long,
    nowMillis: Long,
    minAgeDays: Int
): JunkClassification? {
    if (modifiedTimeMillis <= 0L || modifiedTimeMillis > nowMillis) return null
    val normalizedMinimumDays = minAgeDays.coerceAtLeast(1)
    val classification = when {
        fileName.endsWith(".log", ignoreCase = true) ->
            JunkClassification(
                JunkFileKind.LOG,
                maxOf(normalizedMinimumDays, 30)
            )
        fileName.endsWith(".tmp", ignoreCase = true) ->
            JunkClassification(JunkFileKind.TEMPORARY, normalizedMinimumDays)
        fileName.endsWith(".part", ignoreCase = true) ->
            JunkClassification(JunkFileKind.PARTIAL_DOWNLOAD, normalizedMinimumDays)
        else -> return null
    }
    val ageMillis = nowMillis - modifiedTimeMillis
    if (
        ageMillis <
        TimeUnit.DAYS.toMillis(classification.requiredAgeDays.toLong())
    ) {
        return null
    }
    return classification
}

class UnusedAppsAnalyzer(
    private val context: Context,
    private val usageStatsManagerProvider: (Context) -> UsageStatsManager? = {
        it.getSystemService(Context.USAGE_STATS_SERVICE) as? UsageStatsManager
    },
    private val storageBytesProvider: (Context, ApplicationInfo) -> AppStorageBytes? =
        { appContext, appInfo -> AppStorageStatsCompat.getForApp(appContext, appInfo) },
    private val stringProvider: (Int) -> String = { context.getString(it) }
) {
    data class Result(val apps: List<UnusedAppCandidate>, val missingCapabilityMessage: String?)

    fun analyze(days: Int): Result {
        val usage = usageStatsManagerProvider(context)
            ?: return Result(
                emptyList(),
                stringProvider(R.string.storage_cleaner_usage_stats_unavailable)
            )
        val pm = context.packageManager
        val now = System.currentTimeMillis()
        val since = now - TimeUnit.DAYS.toMillis(365)
        val usageStats = runCatching {
            usage.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, since, now)
        }.getOrElse {
            return Result(
                emptyList(),
                stringProvider(R.string.storage_cleaner_permission_hint_usage_access)
            )
        }
        if (usageStats.isNullOrEmpty()) {
            return Result(
                emptyList(),
                stringProvider(R.string.storage_cleaner_permission_hint_usage_access)
            )
        }
        val byPackage = usageStats.associateBy { it.packageName }
        val oldThreshold = now - TimeUnit.DAYS.toMillis(days.toLong())
        var missingStorageSizeDetails = false
        val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
            .asSequence()
            .filterNot { isSystemApplication(it.flags) }
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .mapNotNull { app ->
                val stat = byPackage[app.packageName] ?: return@mapNotNull null
                if (stat.lastTimeUsed > oldThreshold) return@mapNotNull null
                val label = runCatching { pm.getApplicationLabel(app).toString() }
                    .getOrDefault(app.packageName)
                val storageBytes = storageBytesProvider(context, app)
                if (storageBytes == null) {
                    missingStorageSizeDetails = true
                }
                UnusedAppCandidate(
                    packageName = app.packageName,
                    label = label,
                    lastTimeUsedMillis = stat.lastTimeUsed.takeIf { it > 0 },
                    appBytes = storageBytes?.appBytes,
                    cacheBytes = storageBytes?.cacheBytes,
                    dataBytes = storageBytes?.dataBytes
                )
            }
            .sortedBy { it.lastTimeUsedMillis ?: Long.MIN_VALUE }
            .toList()
        val message = if (apps.isNotEmpty() && missingStorageSizeDetails) {
            stringProvider(R.string.storage_cleaner_app_storage_size_unavailable)
        } else {
            null
        }
        return Result(apps, message)
    }

    fun usageAccessIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
}
