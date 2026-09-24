package com.wisso.wizefiles.feature.crashreport

import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import com.wisso.wizefiles.BuildConfig
import java.io.File
import java.time.Instant

object CrashReportStore {
    private const val DIRECTORY_NAME = "crash_reports"
    private const val PENDING_FILE_NAME = "pending.txt"
    private const val TEMP_FILE_NAME = "pending.tmp"
    private const val MAX_STACK_TRACE_CHARS = 128 * 1024
    private const val RETENTION_MILLIS = 7L * 24L * 60L * 60L * 1000L
    private const val TAG = "CrashReportStore"

    fun save(context: Context, rawStackTrace: String) {
        val report = CrashReportFormatter.format(
            metadata = CrashReportMetadata(
                appVersion = BuildConfig.VERSION_NAME,
                versionCode = BuildConfig.VERSION_CODE.toLong(),
                buildType = BuildConfig.BUILD_TYPE,
                timestamp = Instant.now().toString(),
                androidVersion = Build.VERSION.RELEASE,
                apiLevel = Build.VERSION.SDK_INT,
                manufacturer = Build.MANUFACTURER,
                model = Build.MODEL,
                processName = Application.getProcessName(),
                collectorThread = Thread.currentThread().name
            ),
            stackTrace = boundedStackTrace(CrashReportSanitizer.sanitize(rawStackTrace))
        )
        val directory = reportDirectory(context)
        if (!directory.exists() && !directory.mkdirs()) {
            Log.e(TAG, "Could not create the private crash-report directory")
            return
        }
        val temporary = File(directory, TEMP_FILE_NAME)
        val pending = File(directory, PENDING_FILE_NAME)
        runCatching {
            temporary.writeText(report, Charsets.UTF_8)
            if (pending.exists() && !pending.delete()) {
                error("Could not replace the pending crash report")
            }
            if (!temporary.renameTo(pending)) {
                temporary.copyTo(pending, overwrite = true)
                temporary.delete()
            }
        }.onFailure {
            temporary.delete()
            Log.e(TAG, "Could not save the local crash report", it)
        }
    }

    fun hasPending(context: Context): Boolean = pendingFile(context).isFile

    fun read(context: Context): String? = runCatching {
        pendingFile(context).takeIf(File::isFile)?.readText(Charsets.UTF_8)
    }.getOrElse {
        Log.e(TAG, "Could not read the local crash report", it)
        null
    }

    fun delete(context: Context) {
        runCatching {
            pendingFile(context).delete()
            File(reportDirectory(context), TEMP_FILE_NAME).delete()
        }.onFailure {
            Log.e(TAG, "Could not delete the local crash report", it)
        }
    }

    fun pruneExpired(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val pending = pendingFile(context)
        if (pending.isFile && nowMillis - pending.lastModified() >= RETENTION_MILLIS) {
            delete(context)
            CrashReportNotification.cancel(context)
        }
    }

    private fun boundedStackTrace(stackTrace: String): String =
        if (stackTrace.length <= MAX_STACK_TRACE_CHARS) {
            stackTrace
        } else {
            stackTrace.take(MAX_STACK_TRACE_CHARS) + "\n<truncated>"
        }

    private fun reportDirectory(context: Context): File =
        File(context.filesDir, DIRECTORY_NAME)

    private fun pendingFile(context: Context): File =
        File(reportDirectory(context), PENDING_FILE_NAME)
}
